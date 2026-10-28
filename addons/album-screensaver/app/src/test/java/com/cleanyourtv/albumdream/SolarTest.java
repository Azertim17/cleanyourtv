package com.cleanyourtv.albumdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SolarTest {

    /** 28 October 2026 is day 301. Expected local times are Open-Meteo's own sunrise and sunset for that day. */
    private static final int DAY = 301;

    private static void check(String place, double lat, double lon, int offset, int riseH, int riseM, int setH, int setM) {
        int[] t = Solar.times(lat, lon, DAY, offset);
        int rise = riseH * 60 + riseM, set = setH * 60 + setM;
        assertTrue(place + " sunrise " + t[0] + " expected " + rise, Math.abs(t[0] - rise) <= 6);
        assertTrue(place + " sunset " + t[1] + " expected " + set, Math.abs(t[1] - set) <= 6);
    }

    @Test public void lyon() { check("Lyon", 45.76, 4.84, 60, 7, 14, 17, 33); }
    @Test public void reykjavik() { check("Reykjavik", 64.15, -21.94, 0, 8, 58, 17, 23); }
    @Test public void tokyo() { check("Tokyo", 35.68, 139.69, 540, 5, 59, 16, 50); }
    @Test public void sydney() { check("Sydney", -33.87, 151.21, 660, 5, 59, 19, 19); }

    @Test public void polarNightAndMidnightSun() {
        assertEquals(Solar.NEVER_UP, Solar.times(78.0, 15.0, 355, 60)[0]);    // Svalbard, late December
        assertEquals(Solar.ALWAYS_UP, Solar.times(78.0, 15.0, 172, 120)[0]);  // Svalbard, late June
    }
}
