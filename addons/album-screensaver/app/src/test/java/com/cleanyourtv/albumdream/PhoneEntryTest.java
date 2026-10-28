package com.cleanyourtv.albumdream;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PhoneEntryTest {

    @Test public void readsTheLinkFromAFormBody() {
        assertEquals("https://photos.example.com/share/AbC-1_x",
                PhoneEntry.formValue("url=https%3A%2F%2Fphotos.example.com%2Fshare%2FAbC-1_x", "url"));
        assertEquals("a b", PhoneEntry.formValue("x=1&url=a+b&y=2", "url"));
        assertEquals("", PhoneEntry.formValue("x=1", "url"));
        assertEquals("", PhoneEntry.formValue("", "url"));
    }

    @Test public void contentLengthIsReadFromTheHeaders() {
        assertEquals(42, PhoneEntry.contentLength("POST /t HTTP/1.1\r\nHost: x\r\nContent-Length: 42\r\nAccept: */*"));
        assertEquals(7, PhoneEntry.contentLength("POST /t HTTP/1.1\r\ncontent-length:7"));
        assertEquals(-1, PhoneEntry.contentLength("GET /t HTTP/1.1\r\nHost: x"));
        assertEquals(-1, PhoneEntry.contentLength("POST /t HTTP/1.1\r\nContent-Length: abc"));
    }
}
