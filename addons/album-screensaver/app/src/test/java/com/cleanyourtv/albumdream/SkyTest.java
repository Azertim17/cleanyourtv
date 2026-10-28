package com.cleanyourtv.albumdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SkyTest {

    /** A French autumn day: the sun up at 07:14 and down at 17:33. */
    private static final int RISE = 7 * 60 + 14, SET = 17 * 60 + 33;

    @Test public void theSunsetGlowFallsOnTheRealSunset() {
        assertEquals(1140, Sky.reference(SET, RISE, SET));
        assertEquals(450, Sky.reference(RISE, RISE, SET));
    }

    @Test public void eightInTheEveningIsNightWhenTheSunSetAtHalfPastFive() {
        assertTrue(Sky.reference(20 * 60, RISE, SET) >= 1350);   // past the table's last twilight stop
        Sky.Palette night = Sky.at(20 * 60, RISE, SET);
        assertEquals(0, night.sun);   // no warm glow left on the horizon
    }

    @Test public void theOldFixedTableWouldHaveShownTheSunsetGlowAtEight() {
        assertTrue(Sky.at(20 * 60).sun > 0);
    }

    @Test public void theDeepNightStaysNightAndDawnComesBeforeSunrise() {
        assertEquals(0, Sky.reference(3 * 60, RISE, SET));
        assertTrue(Sky.reference(RISE - 30, RISE, SET) > 320);   // the dawn glow has started
        assertTrue(Sky.reference(RISE - 30, RISE, SET) < 450);
    }

    @Test public void midnightSunAndPolarNight() {
        assertEquals(780, Sky.reference(23 * 60, Solar.ALWAYS_UP, Solar.ALWAYS_UP));
        assertEquals(0, Sky.reference(12 * 60, Solar.NEVER_UP, Solar.NEVER_UP));
    }

    @Test public void daytimeIsSpreadBetweenSunriseAndSunset() {
        int noon = Sky.reference((RISE + SET) / 2, RISE, SET);
        assertTrue(noon > 700 && noon < 900);
    }
}
