package com.cleanyourtv.albumdream;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the screensaver and the card's slideshow show: the pictures (full screen, or as prints), and on top what
 * the options switch on: the time and date, the weather, the sky behind it all. Both hosts call start and stop.
 */
final class Scene {

    private static final long MINUTE_MS = 60_000L;
    /** How far the bottom line may wander so it does not burn in. */
    private static final int NUDGE_DP = 20, NUDGE_UP_DP = 6;

    final FrameLayout root;
    private final Context context;
    private final Options options;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final SkyView sky;
    private final View bottomLine;
    private final TextView weather;
    private final Slides slides;
    private final Prints prints;
    private ExecutorService network;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (sky != null) sky.update();
            nudge();
            handler.postDelayed(this, MINUTE_MS);
        }
    };
    private final Runnable refreshWeather = this::refreshWeather;

    Scene(Context context) {
        this.context = context;
        options = Options.load(context);
        root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        if (options.sky && options.prints) {
            sky = new SkyView(context);
            root.addView(sky, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            sky = null;
        }
        FrameLayout stage = new FrameLayout(context);
        root.addView(stage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View overlay = LayoutInflater.from(context).inflate(R.layout.overlay, root, false);
        root.addView(overlay);
        bottomLine = overlay.findViewById(R.id.bottomLine);
        weather = overlay.findViewById(R.id.weather);
        TextView caption = overlay.findViewById(R.id.caption);
        View empty = overlay.findViewById(R.id.empty);
        File dir = context.getExternalFilesDir(null);
        ((TextView) overlay.findViewById(R.id.emptyPath)).setText(
                dir == null ? "" : new File(dir, Media.PICTURES).getPath());

        int clockVisibility = options.clock ? View.VISIBLE : View.GONE;
        overlay.findViewById(R.id.time).setVisibility(clockVisibility);
        overlay.findViewById(R.id.ampm).setVisibility(clockVisibility);
        overlay.findViewById(R.id.date).setVisibility(clockVisibility);
        weather.setVisibility(View.GONE);
        bottomLine.setVisibility(options.clock || options.weather ? View.VISIBLE : View.GONE);
        // In full-screen mode the pictures cover everything: a dark fade keeps the bottom line readable.
        overlay.findViewById(R.id.bottomFade).setVisibility(
                !options.prints && (options.clock || options.weather) ? View.VISIBLE : View.GONE);

        if (options.prints) {
            caption.setVisibility(View.GONE);
            prints = new Prints(context, stage, options.clock || options.weather ? bottomLine : null, empty,
                    options.dates);
            slides = null;
        } else {
            slides = new Slides(context, stage, options.dates ? caption : null, empty);
            prints = null;
            if (!options.dates) caption.setVisibility(View.GONE);
        }
        bottomLine.bringToFront();
    }

    void start() {
        if (running) return;
        running = true;
        if (slides != null) slides.start();
        if (prints != null) prints.start();
        // New pictures from the album arrive for the next time the screensaver starts; none is replaced under the viewer.
        if (Sync.due(context)) Sync.start(context, null, null);
        handler.post(tick);
        if (options.weather) {
            network = Executors.newSingleThreadExecutor();
            handler.post(refreshWeather);
        }
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (network != null) network.shutdownNow();
        network = null;
        if (slides != null) slides.stop();
        if (prints != null) prints.stop();
    }

    private void nudge() {
        if (bottomLine.getVisibility() != View.VISIBLE) return;
        float d = context.getResources().getDisplayMetrics().density;
        bottomLine.animate().translationX((random.nextFloat() * 2 - 1) * NUDGE_DP * d)
                .translationY(random.nextFloat() * -NUDGE_UP_DP * d).setDuration(1500).start();
    }

    /** The cached reading if it is fresh, else a new one; again every 30 minutes while the scene runs. */
    private void refreshWeather() {
        if (!running) return;
        handler.postDelayed(refreshWeather, Weather.REFRESH_MS);
        SharedPreferences cache = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        SharedPreferences opts = Options.preferences(context);
        boolean typed = opts.contains(Options.PLACE_LAT) && opts.contains(Options.PLACE_LON);
        // The cache holds the place it was read for: a reading for another place is stale at once.
        String where = typed ? opts.getString(Options.PLACE_LAT, "") + "," + opts.getString(Options.PLACE_LON, "") : "tv";
        Weather.Reading cached = where.equals(cache.getString("where", ""))
                ? Weather.Reading.decode(cache.getString("reading", "")) : null;
        long age = System.currentTimeMillis() - cache.getLong("at", 0);
        if (cached != null && age >= 0 && age < Weather.REFRESH_MS) {
            showWeather(cached);
            return;
        }
        if (typed) {
            try {
                fetchWeather(Double.parseDouble(opts.getString(Options.PLACE_LAT, "")),
                        Double.parseDouble(opts.getString(Options.PLACE_LON, "")), cache, where);
            } catch (NumberFormatException ignored) {
                // A damaged place: show nothing.
            }
            return;
        }
        Weather.locate(context, location -> {
            if (location == null || !running) return;
            fetchWeather(location.getLatitude(), location.getLongitude(), cache, where);
        });
    }

    private void fetchWeather(double lat, double lon, SharedPreferences cache, String where) {
        ExecutorService n = network;
        if (n == null) return;
        try {
            n.execute(() -> {
                Weather.Reading reading;
                try {
                    reading = Weather.now(lat, lon);
                } catch (Exception e) {
                    return;   // offline: keep what shows, try again in half an hour
                }
                cache.edit().putString("reading", reading.encode()).putString("where", where)
                        .putLong("at", System.currentTimeMillis()).apply();
                handler.post(() -> { if (running) showWeather(reading); });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Stopped meanwhile.
        }
    }

    /** The temperature, with a plain white line icon after it. */
    private void showWeather(Weather.Reading reading) {
        weather.setText(reading.temperature + "\u00B0");
        int size = Math.round(weather.getTextSize() * 1.15f);
        WeatherIcon icon = new WeatherIcon(WeatherIcon.kindFor(reading.code, reading.day), 0xF2FFFFFF);
        icon.setBounds(0, 0, size, size);
        weather.setCompoundDrawablesRelative(null, null, icon, null);
        weather.setCompoundDrawablePadding(Math.round(weather.getTextSize() * 0.3f));
        weather.setVisibility(View.VISIBLE);
    }
}
