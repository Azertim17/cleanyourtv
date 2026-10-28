package com.cleanyourtv.albumdream;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * The look of the settings app, after the launcher it is made for: a dark navy screen with soft teal and violet light,
 * large rounded pills, and a bright ring on whatever the remote has selected. Drawn in code, no image files.
 */
final class Ui {

    static final int NAVY = 0xFF070A14;
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xB3FFFFFF;
    static final int FAINT = 0x66FFFFFF;
    static final int TEAL = 0xFF4FD1B5;
    static final int ON_TEAL = 0xFF06231E;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** The screen: navy, with a teal glow top left and a violet one top right, like the launcher's aurora wallpaper. */
    static Drawable background(Context c) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B1426, NAVY});
        return new LayerDrawable(new Drawable[]{base,
                glow(c, 0x663FBF9F, 0.12f, 0.0f, 520), glow(c, 0x556B4FD8, 0.92f, 0.05f, 560)});
    }

    private static Drawable glow(Context c, int color, float cx, float cy, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(dp(c, radiusDp));
        g.setGradientCenter(cx, cy);
        g.setColors(new int[]{color, color & 0x00FFFFFF});
        return g;
    }

    private static GradientDrawable shape(Context c, int fill, float radiusDp, int strokeDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }

    /** A row or pill: soft glass at rest, brighter with a white ring when selected. */
    static Drawable glass(Context c, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, shape(c, 0x40FFFFFF, radiusDp, 3, 0xFFFFFFFF));
        s.addState(new int[]{android.R.attr.state_pressed}, shape(c, 0x40FFFFFF, radiusDp, 3, 0xFFFFFFFF));
        s.addState(new int[]{}, shape(c, 0x1AFFFFFF, radiusDp, 0, 0));
        return s;
    }

    /** The main action: solid teal, with the same white ring when selected. */
    static Drawable solid(Context c, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, shape(c, 0xFF7FE6CE, radiusDp, 3, 0xFFFFFFFF));
        s.addState(new int[]{android.R.attr.state_pressed}, shape(c, 0xFF7FE6CE, radiusDp, 3, 0xFFFFFFFF));
        s.addState(new int[]{}, shape(c, TEAL, radiusDp, 0, 0));
        return s;
    }

    /** The small label that says whether something is on, or what it is set to. */
    static Drawable chip(Context c, boolean on) {
        return shape(c, on ? TEAL : 0x26FFFFFF, 100, 0, 0);
    }

    static TextView text(Context c, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.NORMAL));
        return t;
    }

    /** A section title: small, spaced capitals, as in the launcher's greeting. */
    static TextView caps(Context c, int stringId) {
        TextView t = text(c, 13, MUTED, true);
        t.setText(c.getString(stringId).toUpperCase(java.util.Locale.getDefault()));
        t.setLetterSpacing(0.16f);
        t.setPadding(dp(c, 6), dp(c, 16), 0, dp(c, 8));
        return t;
    }

    static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }
}
