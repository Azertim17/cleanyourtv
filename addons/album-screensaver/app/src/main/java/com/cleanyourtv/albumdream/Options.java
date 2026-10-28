package com.cleanyourtv.albumdream;

import android.content.Context;
import android.content.SharedPreferences;

/** The switches in SettingsActivity. All off by default: then it is the plain full-screen slideshow. */
final class Options {
    static final String CLOCK = "clock";
    static final String DATES = "photo_dates";
    static final String SKY = "sky";
    static final String PRINTS = "prints";
    static final String WEATHER = "weather";
    /** A place typed in the settings: its name and coordinates. Without one the weather uses the TV's location. */
    static final String PLACE = "weather_place";
    static final String PLACE_LAT = "weather_lat";
    static final String PLACE_LON = "weather_lon";

    final boolean clock;
    final boolean dates;
    final boolean sky;
    final boolean prints;
    final boolean weather;

    private Options(SharedPreferences p) {
        clock = p.getBoolean(CLOCK, false);
        dates = p.getBoolean(DATES, false);
        sky = p.getBoolean(SKY, false);
        prints = p.getBoolean(PRINTS, false);
        weather = p.getBoolean(WEATHER, false);
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE);
    }

    static Options load(Context context) {
        return new Options(preferences(context));
    }
}
