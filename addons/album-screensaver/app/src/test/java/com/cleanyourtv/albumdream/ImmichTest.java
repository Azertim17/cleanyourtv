package com.cleanyourtv.albumdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ImmichTest {

    private final Album album = Album.parse("https://p.example.com/share/KEY1");

    @Test public void readsTopLevelAssetsAndSkipsClipsAndTrash() throws Exception {
        String json = "{\"type\":\"ALBUM\",\"assets\":["
                + "{\"id\":\"11111111-aaaa-bbbb-cccc-000000000001\",\"type\":\"IMAGE\",\"localDateTime\":\"2023-12-02T09:22:30.000Z\"},"
                + "{\"id\":\"11111111-aaaa-bbbb-cccc-000000000002\",\"type\":\"VIDEO\"},"
                + "{\"id\":\"11111111-aaaa-bbbb-cccc-000000000003\",\"type\":\"IMAGE\",\"isTrashed\":true},"
                + "{\"id\":\"11111111-aaaa-bbbb-cccc-000000000004\",\"type\":\"IMAGE\",\"exifInfo\":{\"dateTimeOriginal\":\"2021-05-17T10:00:00.000Z\"}},"
                + "{\"id\":\"bad id\",\"type\":\"IMAGE\"}]}";
        List<Album.Item> items = Immich.parse(album, json);
        assertEquals(2, items.size());
        assertEquals("20231202", items.get(0).date);
        assertEquals("20210517", items.get(1).date);
        assertEquals("https://p.example.com/api/assets/11111111-aaaa-bbbb-cccc-000000000001/thumbnail?size=preview&key=KEY1",
                items.get(0).url);
        assertEquals("11111111-aaaa-bbbb-cccc-000000000001", items.get(0).id);   // the real id, for the older API path
        assertTrue(items.get(0).fileName().endsWith("-20231202.jpg"));
    }

    @Test public void readsAssetsUnderAlbumForOlderServers() throws Exception {
        String json = "{\"album\":{\"assets\":[{\"id\":\"22222222-aaaa-bbbb-cccc-000000000001\",\"type\":\"IMAGE\",\"fileCreatedAt\":\"2019-01-05T00:00:00.000Z\"}]}}";
        List<Album.Item> items = Immich.parse(album, json);
        assertEquals(1, items.size());
        assertEquals("20190105", items.get(0).date);
    }

    @Test public void dayRejectsGarbage() {
        assertEquals("", Immich.day(""));
        assertEquals("", Immich.day("not a date"));
        assertEquals("", Immich.day("2023-13-02T00:00:00Z"));
        assertEquals("20230102", Immich.day("2023-01-02"));
    }

    @Test public void immich3ListsTheAlbumThroughASearch() throws Exception {
        String link = "{\"type\":\"ALBUM\",\"assets\":[],\"album\":{\"id\":\"18300545-96ca-4979-a129-059ddbb8f054\",\"assetCount\":1}}";
        assertEquals(0, Immich.parse(album, link).size());
        assertEquals("18300545-96ca-4979-a129-059ddbb8f054", Immich.albumId(link));
        assertEquals("", Immich.albumId("{\"type\":\"INDIVIDUAL\",\"assets\":[]}"));
        assertTrue(Immich.searchBody("abc").contains("\"albumIds\":[\"abc\"]"));
        assertEquals("https://p.example.com/api/search/metadata?key=KEY1", album.immichSearchUrl());
        String found = "{\"assets\":{\"total\":1,\"items\":[{\"id\":\"a489eed0-cb67-48f0-80a0-2a94f3e00e4a\",\"type\":\"IMAGE\",\"localDateTime\":\"2026-08-29T00:35:06.000Z\"}]}}";
        List<Album.Item> items = Immich.parseSearch(album, found);
        assertEquals(1, items.size());
        assertEquals("20260829", items.get(0).date);
    }
}
