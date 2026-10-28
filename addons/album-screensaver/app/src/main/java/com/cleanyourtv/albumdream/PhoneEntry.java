package com.cleanyourtv.albumdream;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

/**
 * Typing a long link with a TV remote is slow, so the settings can open a tiny web page on the TV's own network address
 * for a minute: the screen shows a QR code, the phone opens the page, the link is pasted there and sent to the TV.
 *
 * It listens only while the settings screen asks for it (and stops when it is left), only on the local network, and only
 * answers the one address that carries a random token, so another device on the network cannot post a link without
 * having seen the QR code. A link is only accepted if {@link Album#parse} understands it. Nothing leaves the network.
 */
final class PhoneEntry {

    interface Listener {
        /** Called on the server's thread with a link that parsed as an Immich or Google Photos album. */
        void onLink(String link);
    }

    private static final int BODY_MAX = 4096;
    private static final int HEAD_MAX = 8192;

    private final Context context;
    private final Listener listener;
    private final String token;
    private volatile ServerSocket server;
    private Thread thread;

    PhoneEntry(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        byte[] random = new byte[8];
        new SecureRandom().nextBytes(random);
        StringBuilder sb = new StringBuilder();
        for (byte b : random) sb.append(String.format(Locale.ROOT, "%02x", b));
        token = sb.toString();
    }

    /** Starts listening; returns the address to open on the phone, or null when the TV has no local network address. */
    String start() {
        String ip = localAddress();
        if (ip == null) return null;
        try {
            server = new ServerSocket(0);
        } catch (IOException e) {
            return null;
        }
        ServerSocket s = server;
        thread = new Thread(() -> serve(s), "phone-entry");
        thread.setDaemon(true);
        thread.start();
        return "http://" + ip + ":" + s.getLocalPort() + "/" + token;
    }

    void stop() {
        ServerSocket s = server;
        server = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // Closing is enough: the accept loop ends.
            }
        }
    }

    /** The TV's address on the home network (Wi-Fi or Ethernet): a private IPv4 address, or null. */
    static String localAddress() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (IOException ignored) {
            // No address: the caller says so.
        }
        return null;
    }

    private void serve(ServerSocket s) {
        while (server == s && !s.isClosed()) {
            try (Socket c = s.accept()) {
                c.setSoTimeout(5000);
                handle(c);
            } catch (IOException e) {
                if (s.isClosed()) return;
            }
        }
    }

    private void handle(Socket c) throws IOException {
        InputStream in = c.getInputStream();
        String head = readHead(in);
        if (head == null) return;
        String[] first = head.split("\r\n", 2)[0].split(" ");
        if (first.length < 2) return;
        String method = first[0], path = first[1].split("\\?")[0];
        if (!path.equals("/" + token)) {
            respond(c, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        if (method.equals("GET")) {
            respond(c, 200, "text/html; charset=utf-8", page(null));
        } else if (method.equals("POST")) {
            int length = contentLength(head);
            if (length < 0 || length > BODY_MAX) {
                respond(c, 413, "text/plain; charset=utf-8", "Too large");
                return;
            }
            String link = formValue(readBody(in, head, length), "url");
            if (Album.parse(link) == null) {
                respond(c, 400, "text/html; charset=utf-8", page(context.getString(R.string.phone_page_bad)));
                return;
            }
            respond(c, 200, "text/html; charset=utf-8", page(context.getString(R.string.phone_page_ok), true));
            listener.onLink(link.trim());
        } else {
            respond(c, 405, "text/plain; charset=utf-8", "Method not allowed");
        }
    }

    /** The request line and headers, up to the blank line; any body bytes already read stay in {@code carry}. */
    private byte[] carry = new byte[0];

    private String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = in.read(chunk)) > 0) {
            buf.write(chunk, 0, read);
            byte[] all = buf.toByteArray();
            int end = indexOf(all, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            if (end >= 0) {
                carry = java.util.Arrays.copyOfRange(all, end + 4, all.length);
                return new String(all, 0, end, StandardCharsets.ISO_8859_1);
            }
            if (all.length > HEAD_MAX) return null;
        }
        return null;
    }

    private String readBody(InputStream in, String head, int length) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(carry, 0, Math.min(carry.length, length));
        byte[] chunk = new byte[1024];
        while (body.size() < length) {
            int read = in.read(chunk, 0, Math.min(chunk.length, length - body.size()));
            if (read <= 0) break;
            body.write(chunk, 0, read);
        }
        return new String(body.toByteArray(), StandardCharsets.UTF_8);
    }

    static int contentLength(String head) {
        for (String line : head.split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                try {
                    return Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /** The decoded value of one field of an application/x-www-form-urlencoded body, or "". */
    static String formValue(String body, String name) {
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0 || !pair.substring(0, eq).equals(name)) continue;
            try {
                return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) if (data[i + j] != pattern[j]) continue outer;
            return i;
        }
        return -1;
    }

    private String page(String message) {
        return page(message, false);
    }

    /** The one page: a dark, phone-sized form with a single field. */
    private String page(String message, boolean done) {
        String note = message == null ? "" : "<p class=\"" + (done ? "ok" : "bad") + "\">" + message + "</p>";
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + context.getString(R.string.dream_name) + "</title><style>"
                + "body{margin:0;padding:28px 22px;background:#070A14;color:#fff;font:18px/1.4 -apple-system,Roboto,sans-serif}"
                + "h1{font-size:28px;margin:0 0 6px}p{color:#b8c0cc}textarea{width:100%;box-sizing:border-box;height:120px;"
                + "border-radius:14px;border:2px solid #3a4560;background:#10172b;color:#fff;padding:14px;font-size:17px}"
                + "button{margin-top:16px;width:100%;padding:16px;border:0;border-radius:14px;background:#4FD1B5;color:#06231e;"
                + "font-size:19px;font-weight:600}.ok{color:#4FD1B5}.bad{color:#ff8a8a}</style></head><body>"
                + "<h1>" + context.getString(R.string.dream_name) + "</h1><p>" + context.getString(R.string.phone_page_help) + "</p>"
                + note + "<form method=\"post\"><textarea name=\"url\" placeholder=\"https://\" autofocus></textarea>"
                + "<button type=\"submit\">" + context.getString(R.string.phone_page_button) + "</button></form></body></html>";
    }

    private static void respond(Socket c, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 400 ? "Bad Request" : code == 404 ? "Not Found"
                : code == 405 ? "Method Not Allowed" : "Payload Too Large";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + bytes.length
                + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n";
        OutputStream out = c.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }
}
