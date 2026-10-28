package com.cleanyourtv.albumdream;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An album shared by link, and what one of its pictures is. Two kinds of link are understood:
 * an Immich shared link ({@code https://host/share/KEY} or {@code https://host/s/SLUG}) and a Google Photos
 * shared album ({@code https://photos.app.goo.gl/...} or {@code https://photos.google.com/share/...}).
 * Pure Java: no Android classes, so the tests run on the JVM.
 */
final class Album {

    enum Kind { IMMICH, GOOGLE }

    /** One picture to download: {@code url} is where the TV-sized JPEG comes from; {@link #fileName} is the file it becomes. */
    static final class Item {
        final String id;
        final String url;
        /** "yyyymmdd" or "" when the album does not say when the picture was taken. */
        final String date;
        /** Sort key, newest first: the full ISO date, or "" when unknown. */
        final String taken;

        Item(String id, String url, String date, String taken) {
            this.id = id;
            this.url = url;
            this.date = date;
            this.taken = taken;
        }

        /** The file name in the pictures folder: letters and digits, ending in -yyyymmdd when the date is known. */
        String fileName() {
            // A short hash of the id: Immich ids are long and alike at the start, and file names are kept short.
            String tag = kindTag() + Google.hash(id);
            return date.isEmpty() ? tag + ".jpg" : tag + "-" + date + ".jpg";
        }

        private String kindTag() {
            return url.contains("googleusercontent.com") ? "g" : "i";
        }
    }

    final Kind kind;
    /** Immich: the server's base address, e.g. https://photos.example.com (a sub-path is kept). Google: the link itself. */
    final String base;
    /** Immich only: the shared link's key or slug, and which of the two it is. */
    final String key;
    final boolean slug;

    private Album(Kind kind, String base, String key, boolean slug) {
        this.kind = kind;
        this.base = base;
        this.key = key;
        this.slug = slug;
    }

    private static final Pattern IMMICH_PATH = Pattern.compile("^(.*?)/(share|s)/([A-Za-z0-9_-]+)/?$");
    private static final Pattern KEY_OK = Pattern.compile("[A-Za-z0-9_-]+");

    /** The album behind a pasted link, or null when it is neither an Immich nor a Google Photos link. */
    static Album parse(String text) {
        if (text == null) return null;
        String link = text.trim();
        if (link.length() > 2048 || !link.regionMatches(true, 0, "http", 0, 4)) return null;
        URI uri;
        try {
            uri = new URI(link);
        } catch (Exception e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || !(scheme.equals("https") || scheme.equals("http"))) return null;
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();

        if (host.equals("photos.app.goo.gl") || host.equals("photos.google.com")) {
            return new Album(Kind.GOOGLE, link, null, false);
        }
        Matcher m = IMMICH_PATH.matcher(path);
        if (m.matches() && KEY_OK.matcher(m.group(3)).matches()) {
            String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            return new Album(Kind.IMMICH, scheme + "://" + host + port + m.group(1), m.group(3), m.group(2).equals("s"));
        }
        return null;
    }

    /** Immich: the call that lists the shared link's pictures. */
    String immichListUrl() {
        return base + "/api/shared-links/me?" + (slug ? "slug=" : "key=") + key;
    }

    /** Immich 3.x: the search call that lists an album's pictures with the shared link's key (POST). */
    String immichSearchUrl() {
        return base + "/api/search/metadata?" + (slug ? "slug=" : "key=") + key;
    }

    /** Immich: a picture as a screen-sized JPEG ("preview", about 1440 px, far smaller than the original). */
    String immichPictureUrl(String assetId) {
        return base + "/api/assets/" + assetId + "/thumbnail?size=preview&" + (slug ? "slug=" : "key=") + key;
    }

    /** The same picture from servers older than Immich 1.118, which had another path. */
    String immichLegacyPictureUrl(String assetId) {
        return base + "/api/asset/thumbnail/" + assetId + "?size=preview&" + (slug ? "slug=" : "key=") + key;
    }

    /** The pictures to keep: newest first (those without a date last), at most {@code max}. */
    static List<Item> newest(List<Item> items, int max) {
        List<Item> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> b.taken.compareTo(a.taken));
        return sorted.size() > max ? new ArrayList<>(sorted.subList(0, max)) : sorted;
    }
}
