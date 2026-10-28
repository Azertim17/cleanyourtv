package com.cleanyourtv.albumdream;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The weather as a plain white line icon: a sun, a moon, a cloud, rain, snow or a bolt, drawn from a few strokes with
 * round ends. No colour and no font glyphs, so it looks the same on every TV. The pictures are designed on a 100 x 100
 * grid and scaled to the bounds.
 */
final class WeatherIcon extends Drawable {

    enum Kind { SUN, MOON, PARTLY, PARTLY_NIGHT, CLOUD, RAIN, SNOW, STORM }

    /** The icon for a WMO weather code (Open-Meteo), by day or by night. */
    static Kind kindFor(int code, boolean day) {
        if (code == 0 || code == 1) return day ? Kind.SUN : Kind.MOON;
        if (code == 2) return day ? Kind.PARTLY : Kind.PARTLY_NIGHT;
        if (code == 3 || code == 45 || code == 48) return Kind.CLOUD;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Kind.SNOW;
        if (code >= 95) return Kind.STORM;
        return Kind.RAIN;
    }

    private final Kind kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    WeatherIcon(Kind kind, int color) {
        this.kind = kind;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        int w = getBounds().width(), h = getBounds().height();
        if (w <= 0 || h <= 0) return;
        float scale = Math.min(w, h) / 100f;
        canvas.save();
        canvas.translate(getBounds().left + (w - 100 * scale) / 2f, getBounds().top + (h - 100 * scale) / 2f);
        canvas.scale(scale, scale);
        paint.setStrokeWidth(5.5f);
        switch (kind) {
            case SUN: sun(canvas, 50, 50, 17, 27, 40, false); break;
            case MOON: moon(canvas, 50, 50, 30); break;
            case PARTLY: sun(canvas, 70, 30, 10, 16, 23, true); cloud(canvas, -4, 12); break;
            case PARTLY_NIGHT: moon(canvas, 68, 32, 17); cloud(canvas, -4, 12); break;
            case CLOUD: cloud(canvas, 0, 0); break;
            case RAIN: cloud(canvas, 0, -10); rain(canvas); break;
            case SNOW: cloud(canvas, 0, -10); snow(canvas); break;
            case STORM: cloud(canvas, 0, -10); bolt(canvas); break;
            default: break;
        }
        canvas.restore();
    }

    /** A circle with eight short rays; with {@code lowerGap} the two rays that point down and down-left are left out,
     *  where a cloud sits. */
    private void sun(Canvas c, float cx, float cy, float r, float from, float to, boolean lowerGap) {
        c.drawCircle(cx, cy, r, paint);
        for (int i = 0; i < 8; i++) {
            if (lowerGap && (i == 2 || i == 3)) continue;
            double a = Math.toRadians(i * 45);
            c.drawLine(cx + (float) Math.cos(a) * from, cy + (float) Math.sin(a) * from,
                    cx + (float) Math.cos(a) * to, cy + (float) Math.sin(a) * to, paint);
        }
    }

    /** A crescent: a circle with a second, offset circle cut out of it. */
    private void moon(Canvas c, float cx, float cy, float r) {
        Path outer = new Path(), cut = new Path();
        outer.addCircle(cx, cy, r, Path.Direction.CW);
        cut.addCircle(cx + r * 0.47f, cy - r * 0.33f, r * 0.87f, Path.Direction.CW);
        path.reset();
        path.op(outer, cut, Path.Op.DIFFERENCE);
        c.drawPath(path, paint);
    }

    /** The outline of a cloud: three round tops on a flat base, moved by (dx, dy) on the grid. */
    private void cloud(Canvas c, float dx, float dy) {
        Path a = new Path(), b = new Path(), d = new Path(), base = new Path();
        a.addCircle(34 + dx, 58 + dy, 14, Path.Direction.CW);
        b.addCircle(52 + dx, 48 + dy, 20, Path.Direction.CW);
        d.addCircle(72 + dx, 60 + dy, 13, Path.Direction.CW);
        base.addRoundRect(new RectF(20 + dx, 58 + dy, 85 + dx, 74 + dy), 8, 8, Path.Direction.CW);
        path.reset();
        path.op(a, b, Path.Op.UNION);
        path.op(path, d, Path.Op.UNION);
        path.op(path, base, Path.Op.UNION);
        c.drawPath(path, paint);
    }

    private void rain(Canvas c) {
        for (int i = 0; i < 3; i++) {
            float x = 38 + i * 15;
            c.drawLine(x, 80, x - 6, 94, paint);
        }
    }

    private void snow(Canvas c) {
        Paint dot = new Paint(paint);
        dot.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) c.drawCircle(38 + i * 15, 86 + (i == 1 ? 4 : 0), 3.2f, dot);
    }

    private void bolt(Canvas c) {
        path.reset();
        path.moveTo(55, 70);
        path.lineTo(46, 84);
        path.lineTo(56, 84);
        path.lineTo(48, 98);
        c.drawPath(path, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
