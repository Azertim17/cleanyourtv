package com.cleanyourtv.albumdream;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the pictures folder in step with the album: downloads the pictures it does not have, deletes the ones that
 * left the album. Pictures already on the TV are not downloaded again. Runs off the main thread; at most one at a time.
 */
final class Sync {

    static final String PREFS = "album";
    static final String URL_KEY = "url";
    static final String HOURS_KEY = "hours";
    static final String AT_KEY = "last_at";
    static final String STATUS_KEY = "last_status";
    static final int DEFAULT_HOURS = 12;

    private static final int TIMEOUT_MS = 20_000;
    private static final int PAGE_MAX = 8 * 1024 * 1024;
    private static final String AGENT = "Mozilla/5.0 (Linux; Android 11; Smart TV) AppleWebKit/537.36 Chrome/120 Safari/537.36";

    /** A single worker: two syncs at once would delete each other's half-downloaded files. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    /** True from the moment a sync is queued until it ends, so the screensaver does not queue a second one. */
    private static volatile boolean busy;

    interface Listener {
        /** Called on the worker thread: a one-line status for the screen, as the sync goes. */
        void progress(String status);
    }

    private Sync() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String albumUrl(Context context) {
        return prefs(context).getString(URL_KEY, "");
    }

    static int hours(Context context) {
        int h = prefs(context).getInt(HOURS_KEY, DEFAULT_HOURS);
        return h < 1 || h > 24 * 30 ? DEFAULT_HOURS : h;
    }

    /** True while a sync is queued or running. */
    static boolean running() {
        return busy;
    }

    /** True when an album is set and the last good sync is older than the interval. */
    static boolean due(Context context) {
        SharedPreferences p = prefs(context);
        if (busy || Album.parse(p.getString(URL_KEY, "")) == null) return false;
        long age = System.currentTimeMillis() - p.getLong(AT_KEY, 0);
        return age < 0 || age >= hours(context) * 3_600_000L;
    }

    /** Syncs in the background, then calls {@code done} (also off the main thread) with the status line. */
    static void start(Context context, Listener listener, Runnable done) {
        Context app = context.getApplicationContext();
        busy = true;
        WORKER.execute(() -> {
            String status;
            try {
                status = run(app, listener);
            } finally {
                busy = false;
            }
            if (done != null) done.run();
            if (listener != null) listener.progress(status);
        });
    }

    /** One full sync. Returns the status line; never throws. */
    static String run(Context context, Listener listener) {
        SharedPreferences p = prefs(context);
        String status;
        boolean ok = false;
        try {
            status = sync(context, p.getString(URL_KEY, ""), listener);
            ok = true;
        } catch (IOException e) {
            status = context.getString(R.string.sync_unreachable,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } catch (RuntimeException | org.json.JSONException e) {
            status = context.getString(R.string.sync_unreadable, e.getClass().getSimpleName());
        }
        SharedPreferences.Editor ed = p.edit().putString(STATUS_KEY, status);
        if (ok) ed.putLong(AT_KEY, System.currentTimeMillis());
        ed.apply();
        return status;
    }

    private static String sync(Context context, String link, Listener listener)
            throws IOException, org.json.JSONException {
        Album album = Album.parse(link);
        if (album == null) throw new IOException(context.getString(R.string.album_bad_link));
        File dir = context.getExternalFilesDir(null);
        if (dir == null) throw new IOException("no storage");
        File folder = new File(dir, Media.PICTURES);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("cannot create " + folder);

        say(listener, context.getString(R.string.sync_reading));
        List<Album.Item> all = list(album);
        if (all.isEmpty()) throw new IOException(context.getString(R.string.sync_empty));
        List<Album.Item> keep = Album.newest(all, Media.MAX_FILES);

        Set<String> wanted = new HashSet<>();
        for (Album.Item item : keep) wanted.add(item.fileName());

        int fetched = 0, failed = 0, n = 0;
        for (Album.Item item : keep) {
            n++;
            File target = new File(folder, item.fileName());
            if (target.isFile() && target.length() > 0) continue;
            say(listener, context.getString(R.string.sync_downloading, n, keep.size()));
            try {
                download(album, item, target);
                fetched++;
            } catch (IOException e) {
                failed++;
                wanted.remove(item.fileName());
            }
        }
        // Only now delete what left the album: a sync that broke off halfway never leaves the folder empty.
        int removed = 0;
        File[] existing = folder.listFiles();
        if (existing != null) {
            for (File f : existing) {
                if (f.getName().startsWith(".") && f.getName().endsWith(".part")) { f.delete(); continue; }
                if (Media.file(f.getName()) && !wanted.contains(f.getName()) && f.delete()) removed++;
            }
        }
        if (failed == keep.size()) throw new IOException(context.getString(R.string.sync_none_downloaded));
        String result = context.getString(R.string.sync_done, wanted.size(), fetched, removed);
        return failed > 0 ? result + " " + context.getString(R.string.sync_failed, failed) : result;
    }

    private static List<Album.Item> list(Album album) throws IOException, org.json.JSONException {
        if (album.kind == Album.Kind.IMMICH) {
            String json = new String(get(album.immichListUrl(), "application/json", null), StandardCharsets.UTF_8);
            List<Album.Item> items = Immich.parse(album, json);
            String albumId = Immich.albumId(json);
            if (items.isEmpty() && !albumId.isEmpty()) {
                // Immich 3.x leaves the pictures out of the shared link and answers a search for the album instead.
                String found = new String(get(album.immichSearchUrl(), "application/json",
                        Immich.searchBody(albumId)), StandardCharsets.UTF_8);
                items = Immich.parseSearch(album, found);
            }
            return items;
        }
        return Google.parse(new String(get(album.base, "text/html", null), StandardCharsets.UTF_8));
    }

    private static void download(Album album, Album.Item item, File target) throws IOException {
        byte[] data;
        try {
            data = get(item.url, "image/*", null);
        } catch (IOException e) {
            if (album.kind != Album.Kind.IMMICH) throw e;
            // An Immich older than 1.118 has the picture at its previous path.
            data = get(album.immichLegacyPictureUrl(item.id), "image/*", null);
        }
        if (data.length == 0 || data.length > Media.IMAGE_MAX) throw new IOException("bad picture size");
        File part = new File(target.getParentFile(), "." + target.getName() + ".part");
        try (OutputStream out = new FileOutputStream(part)) {
            out.write(data);
        }
        if (!part.renameTo(target)) {
            part.delete();
            throw new IOException("cannot save " + target.getName());
        }
    }

    /** GET, or POST of {@code json} when given, with redirects followed by hand (HttpURLConnection will not go from
     *  http to https). */
    private static byte[] get(String address, String accept, String json) throws IOException {
        String current = address;
        for (int hops = 0; hops < 6; hops++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", AGENT);
            c.setRequestProperty("Accept", accept);
            c.setRequestProperty("Accept-Language", "en");
            if (json != null) {
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String to = c.getHeaderField("Location");
                    if (to == null) throw new IOException("redirect without a target");
                    current = new URL(new URL(current), to).toString();
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        if (out.size() > PAGE_MAX) throw new IOException("answer too large");
                    }
                    return out.toByteArray();
                }
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    private static void say(Listener listener, String status) {
        if (listener != null) listener.progress(status);
    }
}
