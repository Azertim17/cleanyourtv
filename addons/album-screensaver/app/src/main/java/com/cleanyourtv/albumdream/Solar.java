package com.cleanyourtv.albumdream;

/**
 * Sunrise and sunset for a place and a day, so the sky behind the prints follows the real sun instead of fixed clock
 * times (the sun sets before 18:00 in a French winter and after 22:00 in a summer). The sunrise equation from the
 * Almanac for Computers (the form NOAA publishes): good to a few minutes between the polar circles, which is plenty for
 * a colour gradient. Pure Java.
 */
final class Solar {

    /** The sun does not come up all day (polar night). */
    static final int NEVER_UP = -1;
    /** The sun does not go down all day (midnight sun). */
    static final int ALWAYS_UP = -2;

    private static final double ZENITH = 90.833;   // the sun's upper edge on the horizon, with refraction

    private Solar() {}

    /**
     * {sunrise, sunset} as minutes after midnight in local time ({@code offsetMinutes} from UTC, daylight saving
     * included). For polar night both are {@link #NEVER_UP}, for midnight sun both are {@link #ALWAYS_UP}.
     */
    static int[] times(double latitude, double longitude, int dayOfYear, int offsetMinutes) {
        double[] rise = event(latitude, longitude, dayOfYear, true);
        double[] set = event(latitude, longitude, dayOfYear, false);
        if (rise == null || set == null) {
            // cosH out of range: the sign says which. Recompute it once to tell the two apart.
            double decl = Math.asin(0.39782 * Math.sin(Math.toRadians(trueLongitude(dayOfYear + (12 - longitude / 15) / 24))));
            double cosH = (Math.cos(Math.toRadians(ZENITH)) - Math.sin(decl) * Math.sin(Math.toRadians(latitude)))
                    / (Math.cos(decl) * Math.cos(Math.toRadians(latitude)));
            int kind = cosH > 1 ? NEVER_UP : ALWAYS_UP;
            return new int[]{kind, kind};
        }
        return new int[]{local(rise[0], offsetMinutes), local(set[0], offsetMinutes)};
    }

    private static int local(double utcHours, int offsetMinutes) {
        double minutes = (utcHours * 60 + offsetMinutes) % 1440;
        if (minutes < 0) minutes += 1440;
        return (int) Math.round(minutes) % 1440;
    }

    private static double trueLongitude(double t) {
        double m = 0.9856 * t - 3.289;
        return norm(m + 1.916 * Math.sin(Math.toRadians(m)) + 0.020 * Math.sin(Math.toRadians(2 * m)) + 282.634);
    }

    private static double norm(double degrees) {
        double d = degrees % 360;
        return d < 0 ? d + 360 : d;
    }

    /** {UT hours} of the sunrise or sunset, or null when the sun does not cross the horizon that day. */
    private static double[] event(double latitude, double longitude, int dayOfYear, boolean rising) {
        double lngHour = longitude / 15.0;
        double t = dayOfYear + ((rising ? 6 : 18) - lngHour) / 24.0;
        double l = trueLongitude(t);
        double ra = norm(Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l)))));
        ra += Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90;
        ra /= 15;
        double sinDec = 0.39782 * Math.sin(Math.toRadians(l));
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(ZENITH)) - sinDec * Math.sin(Math.toRadians(latitude)))
                / (cosDec * Math.cos(Math.toRadians(latitude)));
        if (cosH > 1 || cosH < -1) return null;
        double h = rising ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH));
        h /= 15;
        double ut = h + ra - 0.06571 * t - 6.622 - lngHour;
        ut %= 24;
        if (ut < 0) ut += 24;
        return new double[]{ut};
    }
}
