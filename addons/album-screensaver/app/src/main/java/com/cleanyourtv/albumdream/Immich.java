package com.cleanyourtv.albumdream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Reads what an Immich shared link answers: the pictures (never clips) with the day each was taken. */
final class Immich {

    private Immich() {}

    /**
     * The shared link's pictures. Immich 1.x puts an album's assets under "album", later versions at the top level
     * of the answer; both are read.
     */
    static List<Album.Item> parse(Album album, String json) throws org.json.JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray assets = root.optJSONArray("assets");
        JSONObject inner = root.optJSONObject("album");
        if ((assets == null || assets.length() == 0) && inner != null) assets = inner.optJSONArray("assets");
        return items(album, assets);
    }

    /** The album's id from the shared link's answer, or "" for a link to single pictures. */
    static String albumId(String json) throws org.json.JSONException {
        JSONObject inner = new JSONObject(json).optJSONObject("album");
        return inner == null ? "" : inner.optString("id", "");
    }

    /** The POST body that asks for an album's pictures (Immich 3.x no longer lists them in the shared link). */
    static String searchBody(String albumId) throws org.json.JSONException {
        return new JSONObject().put("albumIds", new JSONArray().put(albumId)).put("type", "IMAGE")
                .put("size", 1000).put("page", 1).put("withExif", true).toString();
    }

    /** The pictures in a search answer, {"assets":{"items":[...]}}. */
    static List<Album.Item> parseSearch(Album album, String json) throws org.json.JSONException {
        JSONObject found = new JSONObject(json).optJSONObject("assets");
        return items(album, found == null ? null : found.optJSONArray("items"));
    }

    private static List<Album.Item> items(Album album, JSONArray assets) {
        List<Album.Item> items = new ArrayList<>();
        if (assets == null) return items;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null || !"IMAGE".equals(a.optString("type"))) continue;
            if (a.optBoolean("isTrashed", false)) continue;
            String id = a.optString("id", "");
            if (!id.matches("[A-Za-z0-9-]{8,64}")) continue;
            String taken = a.optString("localDateTime", "");
            if (taken.isEmpty()) {
                JSONObject exif = a.optJSONObject("exifInfo");
                if (exif != null) taken = exif.optString("dateTimeOriginal", "");
            }
            if (taken.isEmpty()) taken = a.optString("fileCreatedAt", "");
            items.add(new Album.Item(id, album.immichPictureUrl(id), day(taken), taken));
        }
        return items;
    }

    /** "2023-12-02T09:22:30.000Z" gives "20231202"; anything not starting with a date gives "". */
    static String day(String iso) {
        if (iso == null || iso.length() < 10 || iso.charAt(4) != '-' || iso.charAt(7) != '-') return "";
        String d = iso.substring(0, 4) + iso.substring(5, 7) + iso.substring(8, 10);
        if (!d.matches("\\d{8}")) return "";
        int year = Integer.parseInt(d.substring(0, 4)), month = Integer.parseInt(d.substring(4, 6));
        return year >= 1900 && month >= 1 && month <= 12 ? d : "";
    }
}
