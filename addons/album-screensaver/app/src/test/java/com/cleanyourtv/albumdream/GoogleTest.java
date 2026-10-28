package com.cleanyourtv.albumdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class GoogleTest {

    private static final String A = "https://lh3.googleusercontent.com/pw/AP1GczPabcdefghijklmnopqrstuvwx_1234";
    private static final String B = "https://lh3.googleusercontent.com/pw/AP1GczQabcdefghijklmnopqrstuvwx_5678";

    @Test public void picksPicturesOnceInPageOrderAndSkipsSmallOnes() {
        String html = "<script>AF_initDataCallback({data:[[\"" + A + "\",4032,3024,1],[\"" + B + "\",3024,4032,2],"
                + "[\"" + A + "\",4032,3024,3],[\"https://lh3.googleusercontent.com/pw/AP1GczSmallAvatarxxxxxxxxxxxxx\",96,96]]})</script>";
        List<Album.Item> items = Google.parse(html);
        assertEquals(2, items.size());
        assertEquals(A + "=w1920-h1080-no", items.get(0).url);
        assertEquals(B + "=w1920-h1080-no", items.get(1).url);
        assertTrue(items.get(0).fileName().matches("g[0-9a-f]{16}\\.jpg"));
    }

    @Test public void understandsEscapedPages() {
        String html = "[\\\"x\\\"] [\"" + A.replace("/", "\\/") + "\",1600,1200";
        assertEquals(1, Google.parse(html).size());
    }

    @Test public void idsAreStable() {
        assertEquals(Google.hash(A), Google.hash(A));
    }
}
