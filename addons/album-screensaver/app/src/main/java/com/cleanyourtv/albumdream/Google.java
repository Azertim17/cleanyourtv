package com.cleanyourtv.albumdream;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Google Photos shared album. Google has no public API for these, so this reads the page that the share link
 * opens in a browser and picks out the picture addresses it embeds. That page is not a contract: if Google changes it,
 * this stops finding pictures (the README says so).
 */
final class Google {

    private Google() {}

    /** A picture the album page lists: its base address on Google's image servers, then its pixel size. */
    private static final Pattern PICTURE = Pattern.compile(
            "\\[\"(https://lh3\\.googleusercontent\\.com/pw/[A-Za-z0-9_-]{20,})\",(\\d{2,5}),(\\d{2,5})");

    /** The pictures in the page, in page order, each once. Videos have the same address shape and are skipped. */
    static List<Album.Item> parse(String html) {
        Map<String, int[]> found = new LinkedHashMap<>();
        Matcher m = PICTURE.matcher(html.replace("\\u003d", "=").replace("\\/", "/"));
        while (m.find()) {
            if (!found.containsKey(m.group(1))) {
                found.put(m.group(1), new int[]{Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))});
            }
        }
        List<Album.Item> items = new ArrayList<>();
        for (Map.Entry<String, int[]> e : found.entrySet()) {
            int w = e.getValue()[0], h = e.getValue()[1];
            if (w < 200 || h < 200) continue;   // avatars and covers, not album pictures
            // "=w1920-h1080" fits the picture inside that box, as a JPEG.
            items.add(new Album.Item(hash(e.getKey()), e.getKey() + "=w1920-h1080-no", "", ""));
        }
        return items;
    }

    /** A short stable id for a picture: the same album address always gives the same file name. */
    static String hash(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
