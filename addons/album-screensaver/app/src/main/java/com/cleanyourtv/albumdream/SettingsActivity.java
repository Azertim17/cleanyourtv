package com.cleanyourtv.albumdream;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * The album screensaver's app: what you open from its card on Home or from the TV's screensaver settings. The album and
 * the two main actions (preview, refresh now) on the left; what shows over the pictures, the weather and the timings on
 * the right. Everything is reached with the remote.
 */
public class SettingsActivity extends Activity {

    private static final int ASK_LOCATION = 1;
    private static final int[] SECONDS = {10, 20, 30, 60, 120};
    private static final int[] IDLE_MINUTES = {1, 2, 5, 10, 15, 30, 60};
    private static final int[] REFRESH_HOURS = {1, 6, 12, 24, 72};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private ToggleRow weatherRow;
    private ToggleRow skyRow;
    private TextView weatherStatus;
    private TextView locationButton;
    private EditText placeField;
    private EditText albumLink;
    private TextView countText;
    private TextView metaText;
    private TextView statusText;
    private TextView refreshButton;
    private TextView timingStatus;
    private PhoneEntry phone;
    private LinearLayout phonePanel;
    private QrView phoneQr;
    private TextView phoneUrl;
    private TextView phoneButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = Options.preferences(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background(this));

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setPadding(Ui.dp(this, 56), Ui.dp(this, 36), Ui.dp(this, 56), 0);
        root.addView(columns, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setClipToPadding(false);
        leftScroll.setVerticalScrollBarEnabled(false);
        leftScroll.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 24));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        leftScroll.addView(left);
        columns.addView(leftScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.9f));
        buildLeft(left);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 40));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(right);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f);
        rp.leftMargin = Ui.dp(this, 48);
        columns.addView(scroll, rp);
        buildRight(right);

        linkTextFields(left);
        linkTextFields(right);
        setContentView(root);
        View first = left.findViewWithTag("preview");
        if (first != null) first.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWeatherStatus();   // back from the TV's location settings
        updateStatus(null);
    }

    @Override
    protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }

    // ---- left: the album and its two actions

    private void buildLeft(LinearLayout left) {
        TextView title = Ui.text(this, 40, Ui.TEXT, true);
        title.setText(R.string.dream_name);
        left.addView(title);
        TextView subtitle = Ui.text(this, 16, Ui.MUTED, false);
        subtitle.setText(R.string.hub_subtitle);
        subtitle.setSingleLine(true);
        subtitle.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 16));
        left.addView(subtitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 14), Ui.dp(this, 22), Ui.dp(this, 14));
        card.setBackground(Ui.glass(this, 22));
        card.setFocusable(false);
        countText = Ui.text(this, 30, Ui.TEXT, true);
        card.addView(countText);
        metaText = Ui.text(this, 16, Ui.MUTED, false);
        metaText.setPadding(0, Ui.dp(this, 2), 0, 0);
        card.addView(metaText);
        statusText = Ui.text(this, 15, Ui.TEAL, false);
        statusText.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(statusText);
        left.addView(card, matchWidth(0, 0, 0, 12));

        TextView preview = pill(R.string.action_preview, "▶", true, v -> startActivity(new Intent(this, SlideshowActivity.class)));
        preview.setTag("preview");
        left.addView(preview, matchWidth(0, 0, 0, 10));
        refreshButton = pill(R.string.action_refresh, "↻", false, v -> refreshNow());
        left.addView(refreshButton, matchWidth(0, 0, 0, 4));

        left.addView(Ui.caps(this, R.string.section_album));
        phoneButton = pill(R.string.action_phone, "\u2316", false, v -> togglePhoneEntry());
        left.addView(phoneButton, matchWidth(0, 0, 0, 10));
        phonePanel = new LinearLayout(this);
        phonePanel.setOrientation(LinearLayout.HORIZONTAL);
        phonePanel.setGravity(Gravity.CENTER_VERTICAL);
        phonePanel.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        phonePanel.setBackground(Ui.glass(this, 18));
        phonePanel.setFocusable(false);
        phonePanel.setVisibility(View.GONE);
        phoneQr = new QrView(this);
        phonePanel.addView(phoneQr, new LinearLayout.LayoutParams(Ui.dp(this, 170), Ui.dp(this, 170)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView scan = Ui.text(this, 17, Ui.TEXT, false);
        scan.setText(R.string.phone_scan);
        words.addView(scan);
        phoneUrl = Ui.text(this, 13, Ui.FAINT, false);
        phoneUrl.setPadding(0, Ui.dp(this, 8), 0, 0);
        words.addView(phoneUrl);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.leftMargin = Ui.dp(this, 16);
        phonePanel.addView(words, wp);
        left.addView(phonePanel, matchWidth(0, 0, 0, 10));
        albumLink = field(R.string.album_hint, Sync.albumUrl(this), EditorInfo.IME_ACTION_DONE, this::saveAndSync);
        albumLink.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        left.addView(albumLink, matchWidth(0, 0, 0, 0));
    }

    /** Shows (or hides) a QR code for a page on the TV's own address where a phone can paste the album link. */
    private void togglePhoneEntry() {
        if (phone != null) {
            closePhoneEntry();
            return;
        }
        PhoneEntry entry = new PhoneEntry(this, link -> main.post(() -> {
            closePhoneEntry();
            albumLink.setText(link);
            statusText.setText(R.string.phone_received);
            saveAndSync();
        }));
        String url = entry.start();
        if (url == null || !phoneQr.setText(url)) {
            entry.stop();
            statusText.setText(R.string.phone_no_network);
            return;
        }
        phone = entry;
        phoneUrl.setText(url);
        phonePanel.setVisibility(View.VISIBLE);
        // The column scrolls: bring the code into view.
        phonePanel.post(() -> phonePanel.requestRectangleOnScreen(
                new Rect(0, 0, phonePanel.getWidth(), phonePanel.getHeight()), false));
        phoneButton.setText("\u2715   " + getString(R.string.action_phone_close));
    }

    private void closePhoneEntry() {
        if (phone != null) phone.stop();
        phone = null;
        phonePanel.setVisibility(View.GONE);
        phoneButton.setText("\u2316   " + getString(R.string.action_phone));
    }

    @Override
    protected void onPause() {
        closePhoneEntry();   // the page only lives while this screen is in front
        super.onPause();
    }

    /** Saves the link in the field (if it is one) and syncs now. */
    private void saveAndSync() {
        String link = albumLink.getText().toString().trim();
        if (Album.parse(link) == null) {
            statusText.setText(R.string.album_bad_link);
            return;
        }
        Sync.prefs(this).edit().putString(Sync.URL_KEY, link).putLong(Sync.AT_KEY, 0).apply();
        startSync();
    }

    /** The refresh button: the link in the field if it changed, else the saved one. */
    private void refreshNow() {
        String typed = albumLink.getText().toString().trim();
        if (!typed.isEmpty() && !typed.equals(Sync.albumUrl(this))) {
            saveAndSync();
            return;
        }
        if (Album.parse(Sync.albumUrl(this)) == null) {
            statusText.setText(R.string.album_bad_link);
            return;
        }
        Sync.prefs(this).edit().putLong(Sync.AT_KEY, 0).apply();
        startSync();
    }

    private void startSync() {
        if (Sync.running()) return;
        refreshButton.setAlpha(0.5f);
        statusText.setText(R.string.refreshing);
        Sync.start(this, s -> main.post(() -> updateStatus(s)), () -> main.post(() -> refreshButton.setAlpha(1f)));
    }

    /** The picture count, when the last good sync was, and a status line (the given one, or the last one's). */
    private void updateStatus(String line) {
        File dir = getExternalFilesDir(null);
        int count = dir == null ? 0 : Media.list(new File(dir, Media.PICTURES)).size();
        countText.setText(getString(R.string.status_photos, count));
        long at = Sync.prefs(this).getLong(Sync.AT_KEY, 0);
        metaText.setText(getString(R.string.status_last, ago(at)));
        String status = line != null ? line : Sync.prefs(this).getString(Sync.STATUS_KEY, getString(R.string.album_none));
        if (!Sync.running() || line != null) statusText.setText(status);
    }

    private String ago(long at) {
        if (at <= 0) return getString(R.string.ago_never);
        long minutes = (System.currentTimeMillis() - at) / 60_000L;
        if (minutes < 1) return getString(R.string.ago_now);
        if (minutes < 60) return getString(R.string.ago_minutes, (int) minutes);
        long hours = minutes / 60;
        if (hours < 48) return getString(R.string.ago_hours, (int) hours);
        return getString(R.string.ago_days, (int) (hours / 24));
    }

    // ---- right: display, weather, timing

    private void buildRight(LinearLayout right) {
        right.addView(Ui.caps(this, R.string.section_display));
        right.addView(toggle(Options.CLOCK, R.string.option_clock, null));
        right.addView(toggle(Options.DATES, R.string.option_dates, null));
        right.addView(toggle(Options.PRINTS, R.string.option_prints, on -> skyRow.setRowEnabled(on)));
        skyRow = toggle(Options.SKY, R.string.option_sky, null);
        skyRow.setRowEnabled(preferences.getBoolean(Options.PRINTS, false));
        right.addView(skyRow);

        right.addView(Ui.caps(this, R.string.section_weather));
        weatherRow = toggle(Options.WEATHER, R.string.option_weather, this::weatherChanged);
        right.addView(weatherRow);
        placeField = field(R.string.place_hint, preferences.getString(Options.PLACE, ""), EditorInfo.IME_ACTION_SEARCH, this::findPlace);
        right.addView(placeField, matchWidth(0, 10, 0, 0));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView find = pill(R.string.place_find, null, false, v -> findPlace());
        buttons.addView(find, new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f));
        TextView tv = pill(R.string.place_use_tv, null, false, v -> useTvLocation());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f);
        tp.leftMargin = Ui.dp(this, 10);
        buttons.addView(tv, tp);
        right.addView(buttons, matchWidth(0, 10, 0, 0));
        weatherStatus = Ui.text(this, 14, Ui.MUTED, false);
        weatherStatus.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), 0, 0);
        right.addView(weatherStatus);
        locationButton = pill(R.string.open_location_settings, null, false, v -> openLocationSettings());
        right.addView(locationButton, matchWidth(0, 10, 0, 0));

        right.addView(Ui.caps(this, R.string.section_timing));
        right.addView(choice(R.string.timing_seconds, SECONDS, this::currentSeconds, this::saveSeconds,
                v -> getString(R.string.unit_seconds, v)));
        right.addView(choice(R.string.timing_idle, IDLE_MINUTES, this::currentIdleMinutes, this::saveIdleMinutes,
                v -> getString(R.string.unit_minutes, v)));
        right.addView(choice(R.string.timing_refresh, REFRESH_HOURS, () -> Sync.hours(this), this::saveRefreshHours,
                v -> getString(R.string.unit_hours, v)));
        timingStatus = Ui.text(this, 14, Ui.MUTED, false);
        timingStatus.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), 0, 0);
        right.addView(timingStatus);
    }

    private void weatherChanged(boolean on) {
        if (on && !typedPlace() && !Weather.permitted(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_LOCATION);
        }
        updateWeatherStatus();
    }

    private boolean typedPlace() {
        return preferences.contains(Options.PLACE_LAT) && preferences.contains(Options.PLACE_LON);
    }

    private void useTvLocation() {
        preferences.edit().remove(Options.PLACE).remove(Options.PLACE_LAT).remove(Options.PLACE_LON).apply();
        placeField.setText("");
        if (weatherRow.on() && !Weather.permitted(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_LOCATION);
        }
        updateWeatherStatus();
    }

    private void findPlace() {
        String query = placeField.getText().toString().trim();
        if (query.isEmpty()) return;
        weatherStatus.setText(R.string.place_searching);
        network.execute(() -> {
            Weather.Place place = null;
            boolean failed = false;
            try {
                place = Weather.find(query);
            } catch (Exception e) {
                failed = true;
            }
            Weather.Place found = place;
            boolean offline = failed;
            main.post(() -> {
                if (found == null) {
                    weatherStatus.setText(offline ? R.string.place_offline : R.string.place_not_found);
                    return;
                }
                preferences.edit().putString(Options.PLACE, found.name)
                        .putString(Options.PLACE_LAT, String.valueOf(found.latitude))
                        .putString(Options.PLACE_LON, String.valueOf(found.longitude)).apply();
                placeField.setText(found.name);
                if (!weatherRow.on()) weatherRow.set(true);
                updateWeatherStatus();
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != ASK_LOCATION) return;
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            if (!typedPlace()) weatherRow.set(false);   // no place and no location: no weather
        }
        updateWeatherStatus();
    }

    private void updateWeatherStatus() {
        boolean on = preferences.getBoolean(Options.WEATHER, false);
        boolean typed = typedPlace();
        boolean locationOff = on && !typed && Weather.permitted(this) && !Weather.locationOn(this);
        String text;
        if (!on) text = getString(R.string.weather_info);
        else if (typed) text = getString(R.string.weather_place_ok, preferences.getString(Options.PLACE, ""));
        else if (!Weather.permitted(this)) text = getString(R.string.weather_no_permission);
        else if (locationOff) text = getString(R.string.weather_location_off);
        else text = getString(R.string.weather_ok);
        weatherStatus.setText(text);
        locationButton.setVisibility(locationOff ? View.VISIBLE : View.GONE);
    }

    private void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ---- timing

    private File secondsFile() {
        File dir = getExternalFilesDir(null);
        return dir == null ? null : new File(dir, Media.SECONDS);
    }

    private int currentSeconds() {
        return Media.seconds(Photos.read(secondsFile()));
    }

    private void saveSeconds(int seconds) {
        File f = secondsFile();
        if (f == null) return;
        try {
            Files.write(f.toPath(), String.valueOf(seconds).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ignored) {
            // Not saved: the picture time stays what it was.
        }
    }

    private int currentIdleMinutes() {
        try {
            return Math.round(Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT) / 60000f);
        } catch (Settings.SettingNotFoundException e) {
            return 0;
        }
    }

    /** The idle time before the TV starts the screensaver: Android's own screen-off timeout. Needs the "modify
     *  system settings" permission, which setup.sh and build-and-install.sh grant over adb. */
    private void saveIdleMinutes(int minutes) {
        if (!Settings.System.canWrite(this)) {
            timingStatus.setText(R.string.idle_not_allowed);
            return;
        }
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60_000);
            timingStatus.setText(getString(R.string.idle_saved, minutes));
        } catch (SecurityException e) {
            timingStatus.setText(R.string.idle_not_allowed);
        }
    }

    private void saveRefreshHours(int hours) {
        Sync.prefs(this).edit().putInt(Sync.HOURS_KEY, hours).apply();
    }

    // ---- widgets

    private interface Changed {
        void on(boolean value);
    }

    private interface Save {
        void to(int value);
    }

    private LinearLayout.LayoutParams matchWidth(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, left), Ui.dp(this, top), Ui.dp(this, right), Ui.dp(this, bottom));
        return p;
    }

    /** A rounded button: teal when it is the main action, glass otherwise; a glyph in front when given. */
    private TextView pill(int label, String glyph, boolean solid, View.OnClickListener click) {
        TextView t = Ui.text(this, 19, solid ? Ui.ON_TEAL : Ui.TEXT, true);
        t.setText(glyph == null ? getString(label) : glyph + "   " + getString(label));
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        t.setMinHeight(Ui.dp(this, 56));
        t.setBackground(solid ? Ui.solid(this, 18) : Ui.glass(this, 18));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(click);
        return t;
    }

    /** A text field in the same glass; the keyboard's Enter runs {@code submit}. */
    private EditText field(int hint, String text, int ime, Runnable submit) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Ui.FAINT);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        e.setSingleLine(true);
        e.setImeOptions(ime);
        e.setText(text);
        e.setBackground(Ui.glass(this, 16));
        e.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        e.setOnEditorActionListener((v, action, event) -> {
            submit.run();
            return false;
        });
        return e;
    }

    /** One row of the right column: a label and, at its end, a chip. */
    private abstract class Row extends LinearLayout {
        final TextView label;
        final TextView chip;

        Row(int labelRes) {
            super(SettingsActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Ui.dp(getContext(), 22), Ui.dp(getContext(), 14), Ui.dp(getContext(), 16), Ui.dp(getContext(), 14));
            setBackground(Ui.glass(getContext(), 18));
            setFocusable(true);
            setClickable(true);
            label = Ui.text(getContext(), 19, Ui.TEXT, false);
            label.setText(labelRes);
            addView(label, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            chip = Ui.text(getContext(), 17, Ui.TEXT, true);
            chip.setPadding(Ui.dp(getContext(), 16), Ui.dp(getContext(), 6), Ui.dp(getContext(), 16), Ui.dp(getContext(), 6));
            addView(chip);
            LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = Ui.dp(getContext(), 8);
            setLayoutParams(p);
        }

        void setRowEnabled(boolean enabled) {
            setAlpha(enabled ? 1f : 0.4f);
            setFocusable(enabled);
            setClickable(enabled);
        }
    }

    /** An on/off row, kept in the options. */
    private final class ToggleRow extends Row {
        private final String key;
        private final Changed changed;

        ToggleRow(String key, int labelRes, Changed changed) {
            super(labelRes);
            this.key = key;
            this.changed = changed;
            showState();
            setOnClickListener(v -> set(!on()));
        }

        boolean on() {
            return preferences.getBoolean(key, false);
        }

        void set(boolean value) {
            preferences.edit().putBoolean(key, value).apply();
            showState();
            if (changed != null) changed.on(value);
        }

        private void showState() {
            boolean on = on();
            chip.setText(on ? R.string.state_on : R.string.state_off);
            chip.setTextColor(on ? Ui.ON_TEAL : Ui.MUTED);
            chip.setBackground(Ui.chip(getContext(), on));
        }
    }

    private ToggleRow toggle(String key, int label, Changed changed) {
        return new ToggleRow(key, label, changed);
    }

    /** A row that steps through the values on each click and shows the current one. */
    private Row choice(int label, int[] values, IntSupplier current, Save save, IntFunction<String> format) {
        Row row = new Row(label) { };
        Runnable show = () -> {
            row.chip.setText(format.apply(current.getAsInt()));
            row.chip.setBackground(Ui.chip(this, false));
        };
        show.run();
        row.setOnClickListener(v -> {
            int now = current.getAsInt(), next = values[0];
            for (int i = 0; i < values.length; i++) {
                if (values[i] == now) {
                    next = values[(i + 1) % values.length];
                    break;
                }
            }
            save.to(next);
            show.run();
        });
        return row;
    }

    /** Android's own focus search steps over a text field when the remote's Down or Up starts from a wide row, so each
     *  field is linked to the controls above and below it by hand. */
    private void linkTextFields(View root) {
        List<View> order = new ArrayList<>();
        collectFocusable(root, order);
        for (View v : order) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        for (int i = 0; i < order.size(); i++) {
            if (!(order.get(i) instanceof EditText)) continue;
            View field = order.get(i);
            if (i > 0) {
                field.setNextFocusUpId(order.get(i - 1).getId());
                order.get(i - 1).setNextFocusDownId(field.getId());
            }
            if (i + 1 < order.size()) {
                field.setNextFocusDownId(order.get(i + 1).getId());
                order.get(i + 1).setNextFocusUpId(field.getId());
            }
        }
    }

    private static void collectFocusable(View v, List<View> out) {
        if (v instanceof ViewGroup && !(v instanceof EditText)) {
            ViewGroup g = (ViewGroup) v;
            if (v.isFocusable() && v.getVisibility() == View.VISIBLE) out.add(v);
            else for (int i = 0; i < g.getChildCount(); i++) collectFocusable(g.getChildAt(i), out);
        } else if (v.isFocusable() && v.getVisibility() == View.VISIBLE) {
            out.add(v);
        }
    }
}
