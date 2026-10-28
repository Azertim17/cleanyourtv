package com.cleanyourtv.albumdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AlbumTest {

    @Test public void immichShareLinkWithKey() {
        Album a = Album.parse("https://photos.example.com/share/AbC_123-xyz");
        assertNotNull(a);
        assertEquals(Album.Kind.IMMICH, a.kind);
        assertEquals("https://photos.example.com", a.base);
        assertEquals("AbC_123-xyz", a.key);
        assertFalse(a.slug);
        assertEquals("https://photos.example.com/api/shared-links/me?key=AbC_123-xyz", a.immichListUrl());
        assertEquals("https://photos.example.com/api/assets/9f/thumbnail?size=preview&key=AbC_123-xyz",
                a.immichPictureUrl("9f"));
    }

    @Test public void immichSlugPortAndSubPath() {
        Album a = Album.parse(" http://nas.local:2283/immich/s/holidays/ ");
        assertNotNull(a);
        assertEquals("http://nas.local:2283/immich", a.base);
        assertTrue(a.slug);
        assertEquals("http://nas.local:2283/immich/api/shared-links/me?slug=holidays", a.immichListUrl());
    }

    @Test public void googleLinks() {
        assertEquals(Album.Kind.GOOGLE, Album.parse("https://photos.app.goo.gl/AbCdEf123").kind);
        assertEquals(Album.Kind.GOOGLE, Album.parse("https://photos.google.com/share/AF1Q?key=abc").kind);
    }

    @Test public void rejectsOtherLinks() {
        assertNull(Album.parse(null));
        assertNull(Album.parse(""));
        assertNull(Album.parse("ftp://photos.example.com/share/abc"));
        assertNull(Album.parse("https://example.com/"));
        assertNull(Album.parse("https://example.com/share/bad key"));
        assertNull(Album.parse("javascript:alert(1)"));
    }

    @Test public void fileNamesAreSafeAndCarryTheDate() {
        Album.Item dated = new Album.Item("3f2a9c1b4d5e6f708192", "https://x/y", "20231202", "2023-12-02T09:00:00Z");
        assertTrue(dated.fileName().matches("i[0-9a-f]{16}-20231202\\.jpg"));
        assertTrue(Media.image(dated.fileName()));
        assertNotNull(Media.dateFromName(dated.fileName()));
        Album.Item undated = new Album.Item("ab12", "https://lh3.googleusercontent.com/pw/x", "", "");
        assertTrue(undated.fileName().matches("g[0-9a-f]{16}\\.jpg"));
        Album.Item other = new Album.Item("3f2a9c1b4d5e6f708193", "https://x/y", "20231202", "");
        assertFalse(dated.fileName().equals(other.fileName()));
        assertTrue(Media.image(undated.fileName()));
    }

    @Test public void keepsTheNewestWhenOverTheLimit() {
        List<Album.Item> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) items.add(new Album.Item("id" + i, "u", "", "2020-01-0" + (i + 1)));
        items.add(new Album.Item("none", "u", "", ""));
        List<Album.Item> top = Album.newest(items, 3);
        assertEquals(3, top.size());
        assertEquals("id4", top.get(0).id);
        assertEquals("id2", top.get(2).id);
    }
}
