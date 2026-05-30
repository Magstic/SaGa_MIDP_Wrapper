package com.nttdocomo.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class BitmapFont {
    private static final String RESOURCE = "/font/glyphs.bin";
    private static final String SJIS_MAP_RESOURCE = "/font/sjis_map.bin";
    private static boolean attempted;
    private static boolean loaded;
    private static boolean mapAttempted;
    private static boolean mapLoaded;
    private static int height;
    private static int ascent;
    private static int descent;
    private static char[] codes;
    private static byte[] advances;
    private static byte[] glyphData;
    private static char[] sjisCodes;
    private static char[] sjisUnicode;
    private static final int BYTES_PER_GLYPH = 24; /* 12 rows * uint16 row mask */
    private static final int MAX_WIDTH = 16;
    private static int[] scratch;
    private static byte[] asciiAdvancePlus;

    private BitmapFont() {}

    static boolean isLoaded() {
        ensureLoaded();
        return loaded;
    }

    static int getHeight() {
        ensureLoaded();
        return loaded ? height : 12;
    }

    static int getAscent() {
        ensureLoaded();
        return loaded ? ascent : 11;
    }

    static int getDescent() {
        ensureLoaded();
        return loaded ? descent : 1;
    }

    static boolean canDraw(String s) {
        int i;
        ensureLoaded();
        if (!loaded || s == null) return false;
        s = normalizeText(s);
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') continue;
            if (find(c) < 0) return false;
        }
        return true;
    }

    static int stringWidth(String s) {
        int i;
        int w = 0;
        ensureLoaded();
        if (!loaded || s == null || s.length() == 0) return 0;
        s = normalizeText(s);
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') break;
            if (c == '\t') {
                w += 12;
            } else {
                w += charWidth(c);
            }
        }
        return w;
    }

    static int charWidth(char c) {
        int idx;
        ensureLoaded();
        if (!loaded) return 0;
        if (c < 256 && asciiAdvancePlus != null && asciiAdvancePlus[c] != 0) {
            return (asciiAdvancePlus[c] & 0xff) - 1;
        }
        idx = find(c);
        if (idx < 0) idx = find('?');
        if (idx < 0) return 6;
        return advances[idx] & 0xff;
    }

    static boolean drawString(Graphics g, String s, int x, int baseline, int argb, Font f) {
        int i;
        int cx;
        int top;
        int a;
        ensureLoaded();
        if (!loaded || g == null || s == null) return false;
        s = normalizeText(s);
        cx = x;
        a = f == null ? ascent : f.getAscent();
        top = baseline - a;
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') break;
            if (c == '\t') {
                cx += 12;
            } else {
                cx += drawChar(g, c, cx, top, argb);
            }
        }
        return true;
    }


    static boolean drawRaw(javax.microedition.lcdui.Graphics mg, String s, int x, int baseline, int argb) {
        int i;
        int cx;
        int top;
        ensureLoaded();
        if (!loaded || mg == null || s == null) return false;
        s = normalizeText(s);
        cx = x;
        top = baseline - ascent;
        mg.setColor(argb & 0x00FFFFFF);
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') break;
            if (c == '\t') {
                cx += 12;
            } else {
                cx += drawCharRaw(mg, c, cx, top);
            }
        }
        return true;
    }

    private static int drawCharRaw(javax.microedition.lcdui.Graphics mg, char c, int x, int y) {
        int idx = find(c);
        int width;
        int row;
        int col;
        int mask;
        int glyphOff;
        if (idx < 0) idx = find('?');
        if (idx < 0) return 6;
        width = advances[idx] & 0xff;
        if (width <= 0) return 0;
        if (width > MAX_WIDTH) width = MAX_WIDTH;
        glyphOff = idx * BYTES_PER_GLYPH;
        for (row = 0; row < height; row++) {
            mask = ((glyphData[glyphOff + row * 2] & 0xff) << 8) | (glyphData[glyphOff + row * 2 + 1] & 0xff);
            col = 0;
            while (col < width) {
                while (col < width && (mask & (1 << (15 - col))) == 0) col++;
                if (col < width) {
                    int run = col;
                    while (col < width && (mask & (1 << (15 - col))) != 0) col++;
                    mg.drawLine(x + run, y + row, x + col - 1, y + row);
                }
            }
        }
        return width;
    }

    /*
     * The original game used new String(bytes, "SJIS").  On non-Japanese
     * Nokia/S60 firmwares that charset alias throws, so the class patcher maps
     * it to ISO-8859-1 instead.  That makes the game initialize correctly and
     * preserves the original Shift-JIS bytes as U+0000..U+00FF chars.  Before
     * measuring or drawing text, convert such byte-preserving strings back to
     * Unicode with our own compact CP932/Shift-JIS table.
     */
    static String normalizeText(String s) {
        int i;
        int len;
        boolean highByte = false;
        boolean sjisLeadPair = false;
        if (s == null || s.length() == 0) return s;
        len = s.length();

        /*
         * v2.12: after SjisPatch the real game text is already
         * decoded through Sjis.  The old raw-byte fallback is kept only
         * for legacy byte-preserving strings, but it must not reinterpret normal
         * Unicode labels such as "♪×".  U+00D7 (multiplication sign) is in the
         * 0x80..0xFF range; the old test treated it as a Shift-JIS byte and
         * converted "♪×" into "jﾗ".
         */
        for (i = 0; i < len; i++) {
            int c = s.charAt(i);
            if (c > 0xFF) {
                return s;
            }
            if (c >= 0x80) {
                highByte = true;
                if (isSjisLead(c) && i + 1 < len) {
                    int t = s.charAt(i + 1);
                    if (t >= 0x40 && t <= 0xFC && t != 0x7F) {
                        sjisLeadPair = true;
                    }
                }
            }
        }
        if (!highByte || !sjisLeadPair) return s;
        return decodeRawSjisBytes(s);
    }

    private static String decodeRawSjisBytes(String s) {
        StringBuffer out = new StringBuffer(s.length());
        int i = 0;
        int len = s.length();
        ensureMapLoaded();
        while (i < len) {
            int b = s.charAt(i) & 0xFF;
            if (b <= 0x7F) {
                out.append((char)b);
                i++;
            } else if (b >= 0xA1 && b <= 0xDF) {
                out.append((char)(0xFF61 + (b - 0xA1)));
                i++;
            } else if (isSjisLead(b) && i + 1 < len) {
                int b2 = s.charAt(i + 1) & 0xFF;
                int u = mapSjis((b << 8) | b2);
                if (u != 0) {
                    out.append((char)u);
                    i += 2;
                } else {
                    out.append('?');
                    i++;
                }
            } else {
                out.append('?');
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isSjisLead(int b) {
        return (b >= 0x81 && b <= 0x9F) || (b >= 0xE0 && b <= 0xFC);
    }

    private static int mapSjis(int code) {
        int lo;
        int hi;
        int mid;
        int v;
        if (!mapLoaded || sjisCodes == null) return 0;
        lo = 0;
        hi = sjisCodes.length - 1;
        while (lo <= hi) {
            mid = (lo + hi) >>> 1;
            v = (sjisCodes[mid] & 0xFFFF) - code;
            if (v == 0) return sjisUnicode[mid] & 0xFFFF;
            if (v < 0) lo = mid + 1;
            else hi = mid - 1;
        }
        return 0;
    }

    private static synchronized void ensureMapLoaded() {
        byte[] data;
        int count;
        int p;
        int i;
        if (mapAttempted) return;
        mapAttempted = true;
        try {
            data = readResource(SJIS_MAP_RESOURCE);
            if (data == null || data.length < 6) return;
            if (data[0] != 'S' || data[1] != 'M' || data[2] != 'P' || data[3] != '1') return;
            count = ((data[4] & 0xff) << 8) | (data[5] & 0xff);
            if (count <= 0 || data.length < 6 + count * 4) return;
            sjisCodes = new char[count];
            sjisUnicode = new char[count];
            p = 6;
            for (i = 0; i < count; i++) {
                sjisCodes[i] = (char)(((data[p] & 0xff) << 8) | (data[p + 1] & 0xff));
                sjisUnicode[i] = (char)(((data[p + 2] & 0xff) << 8) | (data[p + 3] & 0xff));
                p += 4;
            }
            mapLoaded = true;
        } catch (Throwable ignored) {
            mapLoaded = false;
        }
    }

    private static int drawChar(Graphics g, char c, int x, int y, int argb) {
        int idx = find(c);
        int width;
        int row;
        int col;
        int mask;
        int glyphOff;
        if (idx < 0) idx = find('?');
        if (idx < 0) return 6;
        width = advances[idx] & 0xff;
        if (width <= 0) return 0;
        if (width > MAX_WIDTH) width = MAX_WIDTH;
        glyphOff = idx * BYTES_PER_GLYPH;
        if (((argb >>> 24) & 0xFF) >= 255) {
            javax.microedition.lcdui.Graphics mg = g.getMIDPGraphics();
            mg.setColor(argb & 0x00FFFFFF);
            for (row = 0; row < height; row++) {
                mask = ((glyphData[glyphOff + row * 2] & 0xff) << 8) | (glyphData[glyphOff + row * 2 + 1] & 0xff);
                col = 0;
                while (col < width) {
                    while (col < width && (mask & (1 << (15 - col))) == 0) col++;
                    if (col < width) {
                        int run = col;
                        while (col < width && (mask & (1 << (15 - col))) != 0) col++;
                        mg.drawLine(x + run, y + row, x + col - 1, y + row);
                    }
                }
            }
            return width;
        }
        ensureScratch(width * height);
        int pos = 0;
        for (row = 0; row < height; row++) {
            mask = ((glyphData[glyphOff + row * 2] & 0xff) << 8) | (glyphData[glyphOff + row * 2 + 1] & 0xff);
            for (col = 0; col < width; col++) {
                scratch[pos++] = ((mask & (1 << (15 - col))) != 0) ? argb : 0x00000000;
            }
        }
        g.drawRGBComposite(scratch, 0, width, x, y, width, height, true);
        return width;
    }

    private static void ensureScratch(int size) {
        if (scratch == null || scratch.length < size) {
            scratch = new int[size];
        }
    }

    private static int find(char c) {
        int lo = 0;
        int hi;
        int mid;
        int v;
        if (codes == null) return -1;
        hi = codes.length - 1;
        while (lo <= hi) {
            mid = (lo + hi) >>> 1;
            v = codes[mid] - c;
            if (v == 0) return mid;
            if (v < 0) lo = mid + 1;
            else hi = mid - 1;
        }
        return -1;
    }

    private static synchronized void ensureLoaded() {
        byte[] data;
        int count;
        int p;
        int i;
        if (attempted) return;
        attempted = true;
        try {
            data = readResource(RESOURCE);
            if (data == null || data.length < 9) return;
            if (data[0] != 'B' || data[1] != 'M' || data[2] != 'F' || data[3] != '1') return;
            height = data[4] & 0xff;
            ascent = data[5] & 0xff;
            descent = data[6] & 0xff;
            count = ((data[7] & 0xff) << 8) | (data[8] & 0xff);
            if (height <= 0 || height > 16 || count <= 0) return;
            if (data.length < 9 + count * (3 + BYTES_PER_GLYPH)) return;
            codes = new char[count];
            advances = new byte[count];
            glyphData = new byte[count * BYTES_PER_GLYPH];
            p = 9;
            for (i = 0; i < count; i++) {
                codes[i] = (char)(((data[p] & 0xff) << 8) | (data[p + 1] & 0xff));
                advances[i] = data[p + 2];
                System.arraycopy(data, p + 3, glyphData, i * BYTES_PER_GLYPH, BYTES_PER_GLYPH);
                p += 3 + BYTES_PER_GLYPH;
            }
            loaded = true;
            buildFastWidths();
        } catch (Throwable ignored) {
            loaded = false;
        }
    }


    private static void buildFastWidths() {
        int i;
        asciiAdvancePlus = new byte[256];
        for (i = 0; i < codes.length; i++) {
            int c = codes[i] & 0xffff;
            if (c < 256) {
                int w = advances[i] & 0xff;
                if (w < 255) asciiAdvancePlus[c] = (byte)(w + 1);
            }
        }
    }

    private static byte[] readResource(String path) throws IOException {
        InputStream in = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int r;
        try {
            in = BitmapFont.class.getResourceAsStream(path);
            if (in == null) return null;
            while ((r = in.read(buf)) >= 0) {
                if (r > 0) out.write(buf, 0, r);
            }
            return out.toByteArray();
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }
}
