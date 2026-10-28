package com.cleanyourtv.albumdream;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The weather now, for the weather option: the TV's own location (coarse, rounded to about 1 km before it is
 * sent) and Open-Meteo, which needs no key. Only called while the option is on; nothing else goes out.
 */
final class Weather {
    static final long REFRESH_MS = 30 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;

    private Weather() {}

    static boolean permitted(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Whether the TV's location services are on (Settings > Privacy > Location on Google TV). */
    static boolean locationOn(Context context) {
        LocationManager lm = context.getSystemService(LocationManager.class);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        return !lm.getProviders(true).isEmpty();
    }

    /** The newest location the TV already knows, without asking for a new one; null when there is none. */
    @SuppressWarnings("deprecation")
    static Location lastKnown(Context context) {
        LocationManager lm = context.getSystemService(LocationManager.class);
        if (lm == null || !permitted(context)) return null;
        try {
            Location best = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /** The newest location the TV knows, or a fresh one; {@code done} gets null when there is none. */
    @SuppressWarnings("deprecation")
    static void locate(Context context, Consumer<Location> done) {
        LocationManager lm = context.getSystemService(LocationManager.class);
        if (lm == null || !permitted(context) || !locationOn(context)) {
            done.accept(null);
            return;
        }
        try {
            Location best = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) {
                done.accept(best);
                return;
            }
            if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                done.accept(null);
                return;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.getMainExecutor(),
                        done::accept);
            } else {
                // All four methods spelled out: before Android 11 they have no defaults.
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
                    @Override public void onLocationChanged(Location location) { done.accept(location); }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onProviderDisabled(String provider) { }
                }, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException e) {
            done.accept(null);
        }
    }

    /** What Open-Meteo says now: the temperature, the WMO weather code and whether it is day. */
    static final class Reading {
        final int temperature;
        final int code;
        final boolean day;

        Reading(int temperature, int code, boolean day) {
            this.temperature = temperature;
            this.code = code;
            this.day = day;
        }

        /** "13,3,1": how a reading is kept between runs. */
        String encode() {
            return temperature + "," + code + "," + (day ? 1 : 0);
        }

        /** The reading from {@link #encode}, or null for anything else (an older format, a damaged value). */
        static Reading decode(String text) {
            if (text == null) return null;
            String[] p = text.split(",");
            if (p.length != 3) return null;
            try {
                return new Reading(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), p[2].trim().equals("1"));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** The weather now for the place, blocking: call it off the main thread. */
    static Reading now(double latitude, double longitude) throws IOException, JSONException {
        // Two decimals is about 1 km: enough for the weather, no more of where the TV is than that.
        String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.2f"
                + "&longitude=%.2f&current=temperature_2m,weather_code,is_day", latitude, longitude);
        JSONObject current = get(url).getJSONObject("current");
        return new Reading((int) Math.round(current.getDouble("temperature_2m")), current.getInt("weather_code"),
                current.optInt("is_day", 1) == 1);
    }

    /** A place found by name: "Lyon, France" and its coordinates. */
    static final class Place {
        final String name;
        final double latitude;
        final double longitude;

        Place(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /** The best match for a city name from Open-Meteo's geocoding (no key), or null; blocking, off the main thread. */
    static Place find(String query) throws IOException, JSONException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name="
                + java.net.URLEncoder.encode(query.trim(), "UTF-8");
        org.json.JSONArray results = get(url).optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject r = results.getJSONObject(0);
        String name = r.optString("name", query.trim());
        String country = r.optString("country", "");
        return new Place(country.isEmpty() ? name : name + ", " + country, r.getDouble("latitude"), r.getDouble("longitude"));
    }

    private static JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, n);
                if (out.size() > 64 * 1024) throw new IOException("reply too large");
            }
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }
}
