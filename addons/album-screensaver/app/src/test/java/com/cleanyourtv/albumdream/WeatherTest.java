package com.cleanyourtv.albumdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherTest {

    @Test public void readingSurvivesTheRoundTrip() {
        Weather.Reading r = Weather.Reading.decode(new Weather.Reading(-3, 71, false).encode());
        assertNotNull(r);
        assertEquals(-3, r.temperature);
        assertEquals(71, r.code);
        assertFalse(r.day);
        assertTrue(Weather.Reading.decode("13,0,1").day);
    }

    @Test public void oldOrDamagedValuesAreNotReadings() {
        assertNull(Weather.Reading.decode("13\u00B0  \u263E"));
        assertNull(Weather.Reading.decode(""));
        assertNull(Weather.Reading.decode(null));
        assertNull(Weather.Reading.decode("a,b,c"));
    }

    @Test public void iconsFollowTheWeatherCode() {
        assertEquals(WeatherIcon.Kind.SUN, WeatherIcon.kindFor(0, true));
        assertEquals(WeatherIcon.Kind.MOON, WeatherIcon.kindFor(1, false));
        assertEquals(WeatherIcon.Kind.PARTLY, WeatherIcon.kindFor(2, true));
        assertEquals(WeatherIcon.Kind.PARTLY_NIGHT, WeatherIcon.kindFor(2, false));
        assertEquals(WeatherIcon.Kind.CLOUD, WeatherIcon.kindFor(45, true));
        assertEquals(WeatherIcon.Kind.RAIN, WeatherIcon.kindFor(61, true));
        assertEquals(WeatherIcon.Kind.RAIN, WeatherIcon.kindFor(80, false));
        assertEquals(WeatherIcon.Kind.SNOW, WeatherIcon.kindFor(73, true));
        assertEquals(WeatherIcon.Kind.STORM, WeatherIcon.kindFor(95, true));
    }
}
