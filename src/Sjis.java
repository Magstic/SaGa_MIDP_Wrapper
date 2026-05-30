import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Sjis {
    private static final String MAP_RESOURCE = "/font/sjis_map.bin";
    private static boolean attempted;
    private static boolean loaded;
    private static char[] codes;
    private static char[] unicode;

    private Sjis() {}

    public static String decode(byte[] data) {
        if (data == null) return "";
        return decode(data, 0, data.length);
    }

    public static String decode(byte[] data, int off, int len) {
        StringBuffer out;
        int end;
        int p;
        if (data == null || len <= 0) return "";
        if (off < 0) off = 0;
        if (off > data.length) off = data.length;
        if (off + len < off || off + len > data.length) len = data.length - off;
        ensureMap();
        out = new StringBuffer(len);
        end = off + len;
        p = off;
        while (p < end) {
            int b = data[p] & 0xff;
            if (b <= 0x7F) {
                out.append((char)b);
                p++;
            } else if (b >= 0xA1 && b <= 0xDF) {
                out.append((char)(0xFF61 + (b - 0xA1)));
                p++;
            } else if (isLead(b) && p + 1 < end) {
                int b2 = data[p + 1] & 0xff;
                int u = map((b << 8) | b2);
                if (u != 0) {
                    out.append((char)u);
                    p += 2;
                } else {
                    out.append('?');
                    p++;
                }
            } else {
                out.append('?');
                p++;
            }
        }
        return out.toString();
    }

    private static boolean isLead(int b) {
        return (b >= 0x81 && b <= 0x9F) || (b >= 0xE0 && b <= 0xFC);
    }

    private static int map(int code) {
        int lo;
        int hi;
        int mid;
        int v;
        if (!loaded || codes == null) return 0;
        lo = 0;
        hi = codes.length - 1;
        while (lo <= hi) {
            mid = (lo + hi) >>> 1;
            v = (codes[mid] & 0xFFFF) - code;
            if (v == 0) return unicode[mid] & 0xFFFF;
            if (v < 0) lo = mid + 1;
            else hi = mid - 1;
        }
        return 0;
    }

    private static synchronized void ensureMap() {
        byte[] data;
        int count;
        int p;
        int i;
        if (attempted) return;
        attempted = true;
        try {
            data = readResource(MAP_RESOURCE);
            if (data == null || data.length < 6) return;
            if (data[0] != 'S' || data[1] != 'M' || data[2] != 'P' || data[3] != '1') return;
            count = ((data[4] & 0xff) << 8) | (data[5] & 0xff);
            if (count <= 0 || data.length < 6 + count * 4) return;
            codes = new char[count];
            unicode = new char[count];
            p = 6;
            for (i = 0; i < count; i++) {
                codes[i] = (char)(((data[p] & 0xff) << 8) | (data[p + 1] & 0xff));
                unicode[i] = (char)(((data[p + 2] & 0xff) << 8) | (data[p + 3] & 0xff));
                p += 4;
            }
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
        }
    }

    private static byte[] readResource(String path) throws IOException {
        InputStream in = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int r;
        try {
            in = Sjis.class.getResourceAsStream(path);
            if (in == null) return null;
            while ((r = in.read(buf)) >= 0) {
                if (r > 0) out.write(buf, 0, r);
            }
            return out.toByteArray();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }
}
