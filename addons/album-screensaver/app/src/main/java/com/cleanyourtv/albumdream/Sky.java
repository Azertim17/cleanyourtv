package com.cleanyourtv.albumdream;

/**
 * Time-of-day sky palette: night blues, a warm horizon at dawn and sunset, a glow where the sun sits. Pure maths,
 * the caller passes minutes since midnight; every colour stays dark enough for white text on top.
 * From Photo Prints by @shivamsingh262 (#936).
 */
final class Sky {

    private static final class Stop {
        final int minute;
        final int top;
        final int mid;
        final int bottom;
        /** Alpha of the sun/moon glow near the horizon, 0 to 255. */
        final int sun;
        /** Horizontal position of the glow, 0 = left edge, 1 = right edge. */
        final float sunX;

        Stop(int minute, int top, int mid, int bottom, int sun, float sunX) {
            this.minute = minute;
            this.top = top;
            this.mid = mid;
            this.bottom = bottom;
            this.sun = sun;
            this.sunX = sunX;
        }
    }

    /** The palette at one instant: the three gradient colours plus the glow. */
    static final class Palette {
        final int top;
        final int mid;
        final int bottom;
        final int sun;
        final float sunX;

        Palette(int top, int mid, int bottom, int sun, float sunX) {
            this.top = top;
            this.mid = mid;
            this.bottom = bottom;
            this.sun = sun;
            this.sunX = sunX;
        }
    }

    private static final int NIGHT_TOP = 0xFF04060E;
    private static final int NIGHT_MID = 0xFF070C1A;
    private static final int NIGHT_BOTTOM = 0xFF0B1326;

    private static final Stop[] STOPS = {
            new Stop(0, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            new Stop(320, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            new Stop(390, 0xFF160F2E, 0xFF3B2352, 0xFF8A4A6B, 100, 0.24f),
            new Stop(450, 0xFF12203F, 0xFF5C3A63, 0xFFD3674F, 160, 0.30f),
            new Stop(570, 0xFF0E1A33, 0xFF1F3557, 0xFF43648B, 90, 0.38f),
            new Stop(780, 0xFF0B1524, 0xFF17304F, 0xFF31587F, 60, 0.50f),
            new Stop(1020, 0xFF0D1526, 0xFF20395C, 0xFF4C5470, 90, 0.62f),
            new Stop(1140, 0xFF1B1030, 0xFF6C2E52, 0xFFE2704A, 190, 0.76f),
            new Stop(1185, 0xFF140C27, 0xFF4A2049, 0xFFA54A5C, 130, 0.82f),
            new Stop(1260, 0xFF0A0A1C, 0xFF191230, 0xFF3A1F49, 60, 0.86f),
            new Stop(1350, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            new Stop(1440, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
    };

    private Sky() {}

    /** The table's own sunrise (the dawn glow's peak) and sunset (the sunset glow's peak), in minutes. */
    private static final int TABLE_SUNRISE = 450, TABLE_SUNSET = 1140;
    /** Twilight in the table lasts about 2.7 times as long as the real thing (the sky is dark about 70 minutes after
     *  sunset), so real minutes are stretched by that much around the sun. */
    private static final float TWILIGHT_STRETCH = 2.7f;

    /**
     * The palette at a real time of day for a place whose sun rises and sets at {@code sunrise} and {@code sunset}
     * (minutes after midnight, see {@link Solar}): the table is stretched so its dawn glow falls on the real sunrise and
     * its sunset glow on the real sunset, and the night in between stays dark.
     */
    static Palette at(int minuteOfDay, int sunrise, int sunset) {
        return at(reference(minuteOfDay, sunrise, sunset));
    }

    /** The table time that matches {@code minute} when the sun rises and sets at the given minutes. */
    static int reference(int minute, int sunrise, int sunset) {
        if (sunrise == Solar.NEVER_UP) return 0;
        if (sunrise == Solar.ALWAYS_UP) return 780;
        int m = ((minute % 1440) + 1440) % 1440;
        int dayLength = ((sunset - sunrise) % 1440 + 1440) % 1440;
        if (dayLength == 0) dayLength = 1;
        int sinceSunrise = ((m - sunrise) % 1440 + 1440) % 1440;
        if (sinceSunrise < dayLength) {
            // Daytime: sunrise to sunset spread over the table's dawn peak to sunset peak.
            return TABLE_SUNRISE + Math.round(sinceSunrise * (TABLE_SUNSET - TABLE_SUNRISE) / (float) dayLength);
        }
        int sinceSunset = sinceSunrise - dayLength;
        int untilSunrise = 1440 - sinceSunrise;
        if (sinceSunset <= untilSunrise) {
            return Math.min(1440, TABLE_SUNSET + Math.round(sinceSunset * TWILIGHT_STRETCH));
        }
        return Math.max(0, TABLE_SUNRISE - Math.round(untilSunrise * TWILIGHT_STRETCH));
    }

    /** The palette at {@code minuteOfDay}, clamped to a day. */
    static Palette at(int minuteOfDay) {
        int m = Math.max(0, Math.min(1440, minuteOfDay));
        Stop a = STOPS[0];
        Stop b = STOPS[STOPS.length - 1];
        for (int i = 0; i < STOPS.length - 1; i++) {
            if (m >= STOPS[i].minute && m <= STOPS[i + 1].minute) {
                a = STOPS[i];
                b = STOPS[i + 1];
                break;
            }
        }
        float t = b.minute == a.minute ? 0f : (m - a.minute) / (float) (b.minute - a.minute);
        return new Palette(
                lerpColor(a.top, b.top, t),
                lerpColor(a.mid, b.mid, t),
                lerpColor(a.bottom, b.bottom, t),
                Math.round(a.sun + (b.sun - a.sun) * t),
                a.sunX + (b.sunX - a.sunX) * t);
    }

    /** Blends two opaque ARGB colours channel by channel; {@code t} is 0 to 1. */
    static int lerpColor(int from, int to, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * f);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
