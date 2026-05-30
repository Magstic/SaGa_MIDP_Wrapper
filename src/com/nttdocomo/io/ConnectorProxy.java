package com.nttdocomo.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ConnectorProxy {
    private static final String RESOURCE_PREFIX = "resource:///";
    private static final String SCRATCHPAD_PREFIX = "scratchpad:///";
    private static final String SCRATCHPAD_WRITE_ERROR = "scratchpad writes are handled by SpData";
    private static final int LOGICAL_SCRATCHPAD_SIZE = 819200;

    private static final int[] TOKEN_POS = new int[] {
        1604, 128918, 168972, 296958, 465070, 507703, 664167, 703288, 710578
    };
    private static final int[] TOKEN_LEN = new int[] {
        127314, 40054, 127968, 168112, 42633, 156450, 39121, 7290, 40132
    };
    private static final String[] TOKEN_TAG = new String[] {
        "127314_ba15a58b",
        "40054_ce8ff3c5",
        "127968_70367932",
        "168112_50756bee",
        "42633_f0ed6a8c",
        "156450_f08ba4cb",
        "39121_58f504c3",
        "7290_ae311457",
        "40132_28748bc0"
    };

    private ConnectorProxy() {}

    public static javax.microedition.io.Connection open(String uri) throws IOException {
        return open(uri, 0, false);
    }

    public static javax.microedition.io.Connection open(String uri, int mode) throws IOException {
        return open(uri, mode, false);
    }

    public static javax.microedition.io.Connection open(String uri, int mode, boolean timeouts) throws IOException {
        if (isScratchpad(uri)) {
            ScratchpadRange range = parseScratchpadRange(uri);
            return new ScratchpadConnection(range.pos, range.length);
        }
        if (isResource(uri)) {
            return new ResourceConnection(normalizeResourcePath(uri));
        }
        return new HttpConnectionAdapter(uri, mode, timeouts);
    }

    public static InputStream openInputStream(String uri) throws IOException {
        if (isScratchpad(uri)) {
            ScratchpadRange range = parseScratchpadRange(uri);
            return openScratchpadInputStream(range.pos, range.length);
        }
        if (isResource(uri)) {
            return openResourceInputStream(normalizeResourcePath(uri));
        }
        return javax.microedition.io.Connector.openInputStream(uri);
    }

    public static DataInputStream openDataInputStream(String uri) throws IOException {
        return new DataInputStream(openInputStream(uri));
    }

    public static OutputStream openOutputStream(String uri) throws IOException {
        if (isScratchpad(uri)) {
            throw new IOException(SCRATCHPAD_WRITE_ERROR);
        }
        return javax.microedition.io.Connector.openOutputStream(uri);
    }

    public static DataOutputStream openDataOutputStream(String uri) throws IOException {
        return new DataOutputStream(openOutputStream(uri));
    }

    public static void preflightScratchpad() throws IOException {
        byte[] init = readResourceFully("/s/h.bin");
        if (init == null || init.length < 4) {
            throw new IOException("SP resource preflight failed: s/h");
        }
        if (readResourceFully("/s/t1564.bin") == null) {
            throw new IOException("SP resource preflight failed: s/t1564");
        }
        if (readResourceFully("/e/0/o/0000.bin") == null) {
            throw new IOException("SP resource preflight failed: e/0/o");
        }
        if (readResourceFully("/e/s/d/0000.bin") == null) {
            throw new IOException("SP resource preflight failed: e/s/d");
        }
    }

    private static InputStream openScratchpadInputStream(int pos, int length) throws IOException {
        byte[] data;
        String tag;

        if (length < 0) {
            data = readResourceFully(tablePath(pos));
            if (data != null) return new ByteArrayInputStream(data);
            return new ZeroInputStream(LOGICAL_SCRATCHPAD_SIZE - pos);
        }

        if (pos == 1 && length == 3) {
            data = readResourceFully("/s/h.bin");
            if (data != null && data.length >= 4) {
                return new ByteArrayInputStream(new byte[] { data[1], data[2], data[3] });
            }
        }

        if (pos == 778240 && length == 4) {
            return new ByteArrayInputStream(new byte[] {0, 0, 0, 0});
        }

        if (pos == 778244 && length == 0) {
            return new ByteArrayInputStream(new byte[0]);
        }

        tag = tokenTag(pos, length);
        if (tag != null) {
            return new TokenInputStream(tag, length);
        }

        return new ZeroInputStream(length);
    }

    private static String tablePath(int pos) {
        if (pos == 1564 || pos == 1586 || pos == 296940 || pos == 664153) {
            return "/s/t" + pos + ".bin";
        }
        return "/s/missing_" + pos + ".bin";
    }

    private static String tokenTag(int pos, int length) {
        int i;
        for (i = 0; i < TOKEN_POS.length; i++) {
            if (TOKEN_POS[i] == pos && TOKEN_LEN[i] == length) {
                return TOKEN_TAG[i];
            }
        }
        return null;
    }

    private static boolean isScratchpad(String uri) {
        return uri != null && uri.startsWith(SCRATCHPAD_PREFIX);
    }

    private static boolean isResource(String uri) {
        return uri != null && uri.startsWith(RESOURCE_PREFIX);
    }

    private static String normalizeResourcePath(String uri) {
        String name = uri == null ? "" : uri;
        if (name.startsWith(RESOURCE_PREFIX)) {
            name = name.substring(RESOURCE_PREFIX.length());
        }
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name;
    }

    private static InputStream openResourceInputStream(String name) throws IOException {
        String[] paths = new String[] { "/" + name, "/res/" + name };
        int i;
        for (i = 0; i < paths.length; i++) {
            InputStream input = ConnectorProxy.class.getResourceAsStream(paths[i]);
            if (input != null) return input;
        }
        throw new IOException("Resource not found: " + name);
    }

    private static ScratchpadRange parseScratchpadRange(String uri) throws IOException {
        int pos = 0;
        int length = -1;
        int p;
        int comma;
        String work;

        if (uri == null || !uri.startsWith(SCRATCHPAD_PREFIX)) {
            throw new IOException("Not a scratchpad URI: " + uri);
        }
        work = uri.substring(SCRATCHPAD_PREFIX.length());
        p = work.indexOf(";pos=");
        if (p >= 0) {
            p += 5;
            comma = work.indexOf(',', p);
            if (comma < 0) comma = work.length();
            pos = parseIntSafe(work.substring(p, comma), 0);
            p = work.indexOf("length=", comma);
            if (p >= 0) {
                p += 7;
                length = parseIntSafe(work.substring(p), -1);
            }
        }
        return new ScratchpadRange(pos, length);
    }

    private static int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int clampPos(int pos) {
        if (pos < 0) return 0;
        if (pos > LOGICAL_SCRATCHPAD_SIZE) return LOGICAL_SCRATCHPAD_SIZE;
        return pos;
    }

    private static int clampLength(int pos, int length) {
        int available = LOGICAL_SCRATCHPAD_SIZE - pos;
        if (available < 0) available = 0;
        if (length < 0 || length > available) return -1;
        return length;
    }

    private static byte[] readResourceFully(String path) throws IOException {
        InputStream input = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            input = ConnectorProxy.class.getResourceAsStream(path);
            if (input == null) return null;
            int r;
            while ((r = input.read(buffer)) >= 0) {
                if (r > 0) out.write(buffer, 0, r);
            }
            return out.toByteArray();
        } finally {
            if (input != null) {
                try { input.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static final class ScratchpadRange {
        final int pos;
        final int length;
        ScratchpadRange(int pos, int length) {
            this.pos = clampPos(pos);
            this.length = clampLength(this.pos, length);
        }
    }

    private static final class TokenInputStream extends InputStream {
        private final byte[] token;
        private final int total;
        private int pos;

        TokenInputStream(String tag, int totalLength) {
            String text = "SPTOK:" + tag + "\n";
            byte[] t;
            try {
                t = text.getBytes("ISO-8859-1");
            } catch (Exception e) {
                t = text.getBytes();
            }
            token = t;
            total = totalLength < 0 ? token.length : totalLength;
        }

        public int read() {
            if (pos >= total) return -1;
            return pos++ < token.length ? (token[pos - 1] & 0xff) : 0;
        }

        public int read(byte[] b, int off, int len) {
            int i;
            if (b == null) throw new NullPointerException();
            if (len == 0) return 0;
            if (pos >= total) return -1;
            if (len > total - pos) len = total - pos;
            for (i = 0; i < len; i++) {
                b[off + i] = pos < token.length ? token[pos] : 0;
                pos++;
            }
            return len;
        }
    }

    private static final class ZeroInputStream extends InputStream {
        private int remaining;
        ZeroInputStream(int length) {
            remaining = length < 0 ? 0 : length;
        }
        public int read() {
            if (remaining <= 0) return -1;
            remaining--;
            return 0;
        }
        public int read(byte[] b, int off, int len) {
            int i;
            if (b == null) throw new NullPointerException();
            if (len == 0) return 0;
            if (remaining <= 0) return -1;
            if (len > remaining) len = remaining;
            for (i = 0; i < len; i++) b[off + i] = 0;
            remaining -= len;
            return len;
        }
    }

    private static final class ScratchpadConnection implements javax.microedition.io.ContentConnection {
        private final int pos;
        private final int length;
        ScratchpadConnection(int pos, int length) {
            ScratchpadRange r = new ScratchpadRange(pos, length);
            this.pos = r.pos;
            this.length = r.length;
        }
        public InputStream openInputStream() throws IOException { return openScratchpadInputStream(pos, length); }
        public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }
        public OutputStream openOutputStream() throws IOException { throw new IOException(SCRATCHPAD_WRITE_ERROR); }
        public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(openOutputStream()); }
        public String getType() { return null; }
        public String getEncoding() { return null; }
        public long getLength() { return length; }
        public void close() {}
    }

    private static final class ResourceConnection implements javax.microedition.io.ContentConnection {
        private final String name;
        ResourceConnection(String name) { this.name = name; }
        public InputStream openInputStream() throws IOException { return openResourceInputStream(name); }
        public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }
        public OutputStream openOutputStream() throws IOException { throw new IOException("resource URI is read-only"); }
        public DataOutputStream openDataOutputStream() throws IOException { throw new IOException("resource URI is read-only"); }
        public String getType() { return null; }
        public String getEncoding() { return null; }
        public long getLength() { return -1L; }
        public void close() {}
    }

    private static final class HttpConnectionAdapter implements HttpConnection {
        private final String uri;
        private final int mode;
        private final boolean timeouts;
        private javax.microedition.io.HttpConnection delegate;
        HttpConnectionAdapter(String uri, int mode, boolean timeouts) {
            this.uri = uri;
            this.mode = mode;
            this.timeouts = timeouts;
        }
        public void setRequestMethod(String method) throws IOException { ensureOpen().setRequestMethod(method); }
        public void connect() throws IOException { ensureOpen(); }
        public InputStream openInputStream() throws IOException { return ensureOpen().openInputStream(); }
        public DataInputStream openDataInputStream() throws IOException { return ensureOpen().openDataInputStream(); }
        public OutputStream openOutputStream() throws IOException { return ensureOpen().openOutputStream(); }
        public DataOutputStream openDataOutputStream() throws IOException { return ensureOpen().openDataOutputStream(); }
        public String getType() { try { return ensureOpen().getType(); } catch (IOException e) { return null; } }
        public String getEncoding() { try { return ensureOpen().getEncoding(); } catch (IOException e) { return null; } }
        public long getLength() { try { return ensureOpen().getLength(); } catch (IOException e) { return -1L; } }
        public void close() throws IOException { if (delegate != null) { delegate.close(); delegate = null; } }
        private javax.microedition.io.HttpConnection ensureOpen() throws IOException {
            if (delegate == null) delegate = (javax.microedition.io.HttpConnection)javax.microedition.io.Connector.open(uri, mode, timeouts);
            return delegate;
        }
    }
}
