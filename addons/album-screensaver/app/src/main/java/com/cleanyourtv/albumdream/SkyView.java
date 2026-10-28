package com.cleanyourtv.albumdream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.View;

import java.util.Calendar;

/**
 * A full-screen gradient that follows the clock (Sky). One small bitmap (480x270) stretched over the screen and
 * redrawn at most once a minute. From Photo Prints by @shivamsingh262 (#936).
 */
final class SkyView extends View {

    private static final int TEX_W = 480, TEX_H = 270;

    private int lastMinute = -1;
    private Bitmap texture;
    private final Context appContext;
    /** The sunrise and sunset worked out for today and the place, kept for the day. */
    private int[] sun;
    private int sunDay = -1;

    SkyView(Context context) {
        super(context);
        appContext = context.getApplicationContext();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Where the sun is judged from: the city typed in the settings, else the TV's last known location, else a guess at
     * the middle latitudes on the time zone's longitude (the sky is then right to within the season's swing).
     */
    private double[] place(int offsetMinutes) {
        android.content.SharedPreferences p = Options.preferences(appContext);
        try {
            if (p.contains(Options.PLACE_LAT) && p.contains(Options.PLACE_LON)) {
                return new double[]{Double.parseDouble(p.getString(Options.PLACE_LAT, "")),
                        Double.parseDouble(p.getString(Options.PLACE_LON, ""))};
            }
        } catch (NumberFormatException ignored) {
            // A damaged place: fall through to the next source.
        }
        android.location.Location known = Weather.lastKnown(appContext);
        if (known != null) return new double[]{known.getLatitude(), known.getLongitude()};
        return new double[]{46.0, offsetMinutes / 60.0 * 15.0};
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Picks up a new time of day; cheap and safe to call often. */
    void update() {
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (minute == lastMinute) return;
        lastMinute = minute;

        int offset = (now.get(Calendar.ZONE_OFFSET) + now.get(Calendar.DST_OFFSET)) / 60_000;
        int day = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        if (sun == null || day != sunDay) {
            double[] where = place(offset);
            sun = Solar.times(where[0], where[1], now.get(Calendar.DAY_OF_YEAR), offset);
            sunDay = day;
        }
        Sky.Palette sky = Sky.at(minute, sun[0], sun[1]);
        boolean first = texture == null;
        if (first) texture = Bitmap.createBitmap(TEX_W, TEX_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(texture);
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0f, 0f, 0f, TEX_H,
                new int[]{sky.top, sky.mid, sky.bottom},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, TEX_W, TEX_H, paint);

        if (sky.sun > 0) {
            int warm = Sky.lerpColor(sky.bottom, 0xFFFFD9A0, 0.65f);
            paint.setShader(new RadialGradient(
                    sky.sunX * TEX_W, TEX_H * 0.80f, TEX_W * 0.55f,
                    new int[]{(sky.sun << 24) | (warm & 0x00FFFFFF), warm & 0x00FFFFFF},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, TEX_W, TEX_H, paint);
        }

        if (first) {
            BitmapDrawable drawable = new BitmapDrawable(getResources(), texture);
            drawable.setGravity(Gravity.FILL);
            setBackground(drawable);
        }
        invalidate();
    }
}
