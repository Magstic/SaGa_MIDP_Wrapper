import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FontBuild {
    private static final int PIXEL_SIZE = 12;
    private static final int HEIGHT = 12;
    private static final int ASCENT = 11;
    private static final int DESCENT = 1;
    private static final int MAX_WIDTH = 16;
    private static final int BYTES_PER_GLYPH = 24;
    private static final int RENDER_SIZE = 48;
    private static final int RENDER_X = 16;
    private static final int RENDER_BASELINE = 32;

    private FontBuild() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: FontBuild <font-dir> <out-dir> [source-dir]");
        }
        File fontDir = new File(args[0]);
        File outDir = new File(args[1]);
        File srcDir = args.length >= 3 ? new File(args[2]) : null;
        File fontFile = findFont(fontDir);
        if (fontFile == null) {
            throw new IOException("No .ttf or .otf font found in " + fontDir.getPath());
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Could not create " + outDir.getPath());
        }

        Font base = loadFont(fontFile).deriveFont((float)PIXEL_SIZE);
        Map<Integer,Integer> sjisMap = buildSjisMap();
        TreeSet<Integer> glyphs = buildGlyphSet(sjisMap, base, srcDir);

        writeSjisMap(sjisMap, new File(outDir, "sjis_map.bin"));
        writeGlyphs(base, glyphs, new File(outDir, "glyphs.bin"));
        System.out.println("FontBuild: " + fontFile.getName() + ", glyphs=" + glyphs.size() + ", sjis=" + sjisMap.size());
    }

    private static File findFont(File dir) {
        File[] files;
        int i;
        if (dir == null || !dir.isDirectory()) return null;
        files = dir.listFiles();
        if (files == null) return null;
        for (i = 0; i < files.length; i++) {
            String n = files[i].getName().toLowerCase();
            if (files[i].isFile() && (n.endsWith(".ttf") || n.endsWith(".otf"))) {
                return files[i];
            }
        }
        return null;
    }

    private static Font loadFont(File file) throws IOException, FontFormatException {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (FontFormatException ex) {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            in = new FileInputStream(file);
            return Font.createFont(Font.TYPE1_FONT, in);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }

    private static TreeSet<Integer> buildGlyphSet(Map<Integer,Integer> sjisMap, Font font, File srcDir) throws IOException {
        TreeSet<Integer> set = new TreeSet<Integer>();
        int i;
        for (i = 0x20; i <= 0x7E; i++) addIfDisplayable(set, font, i);
        for (i = 0xA0; i <= 0xFF; i++) addIfDisplayable(set, font, i);
        for (i = 0xFF61; i <= 0xFF9F; i++) addIfDisplayable(set, font, i);
        addIfDisplayable(set, font, '?');
        addIfDisplayable(set, font, 0x3000);
        addIfDisplayable(set, font, 0x3001);
        addIfDisplayable(set, font, 0x3002);
        addIfDisplayable(set, font, 0x3007);
        addIfDisplayable(set, font, 0x266A); /* ♪ */
        addIfDisplayable(set, font, 0x25CB); /* ○ */
        addIfDisplayable(set, font, 0x3007); /* 〇 */
        addIfDisplayable(set, font, 0x00D7); /* × */
        addIfDisplayable(set, font, 0x2715); /* ✕ */
        for (Iterator<Integer> it = sjisMap.values().iterator(); it.hasNext();) {
            int u = it.next().intValue();
            addIfDisplayable(set, font, u);
        }
        addJavaSourceLiterals(set, font, srcDir);
        if (!set.contains(Integer.valueOf('?'))) set.add(Integer.valueOf('?'));
        return set;
    }

    private static void addIfDisplayable(TreeSet<Integer> set, Font font, int cp) {
        if (cp <= 0 || cp > 0xFFFF) return;
        if (font.canDisplay((char)cp) || cp < 0x100 || cp == 0x2715) {
            set.add(Integer.valueOf(cp));
        }
    }

    private static void addJavaSourceLiterals(TreeSet<Integer> set, Font font, File srcDir) throws IOException {
        if (srcDir == null || !srcDir.isDirectory()) return;
        scanJavaDir(set, font, srcDir);
    }

    private static void scanJavaDir(TreeSet<Integer> set, Font font, File dir) throws IOException {
        File[] files = dir.listFiles();
        int i;
        if (files == null) return;
        for (i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                scanJavaDir(set, font, f);
            } else if (f.isFile() && f.getName().endsWith(".java")) {
                scanJavaFile(set, font, f);
            }
        }
    }

    private static void scanJavaFile(TreeSet<Integer> set, Font font, File file) throws IOException {
        String s = readText(file);
        int i = 0;
        int len = s.length();
        boolean lineComment = false;
        boolean blockComment = false;
        while (i < len) {
            char c = s.charAt(i);
            if (lineComment) {
                if (c == '\n' || c == '\r') lineComment = false;
                i++;
            } else if (blockComment) {
                if (c == '*' && i + 1 < len && s.charAt(i + 1) == '/') {
                    blockComment = false;
                    i += 2;
                } else {
                    i++;
                }
            } else if (c == '/' && i + 1 < len && s.charAt(i + 1) == '/') {
                lineComment = true;
                i += 2;
            } else if (c == '/' && i + 1 < len && s.charAt(i + 1) == '*') {
                blockComment = true;
                i += 2;
            } else if (c == '"') {
                i = scanQuotedLiteral(set, font, s, i + 1, '"');
            } else if (c == '\'') {
                i = scanQuotedLiteral(set, font, s, i + 1, '\'');
            } else {
                i++;
            }
        }
    }

    private static String readText(File f) throws IOException {
        FileInputStream in = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try {
            in = new FileInputStream(f);
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        } finally {
            if (in != null) in.close();
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    private static int scanQuotedLiteral(TreeSet<Integer> set, Font font, String s, int i, char quote) {
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i++);
            if (c == quote) return i;
            if (c == '\\' && i < len) {
                EscapeResult er = readEscape(s, i);
                if (er.consumed > 0) {
                    c = er.value;
                    i += er.consumed;
                } else {
                    c = s.charAt(i++);
                }
            }
            if (c != '\n' && c != '\r' && c != '\t') {
                addIfDisplayable(set, font, c);
            }
        }
        return i;
    }

    private static EscapeResult readEscape(String s, int i) {
        int len = s.length();
        if (i >= len) return new EscapeResult((char)0, 0);
        char c = s.charAt(i);
        if (c == 'u') {
            int j = i;
            while (j < len && s.charAt(j) == 'u') j++;
            if (j + 4 <= len) {
                int v = 0;
                int k;
                for (k = 0; k < 4; k++) {
                    int d = hex(s.charAt(j + k));
                    if (d < 0) return new EscapeResult((char)0, 0);
                    v = (v << 4) | d;
                }
                return new EscapeResult((char)v, (j - i) + 4);
            }
            return new EscapeResult((char)0, 0);
        }
        if (c >= '0' && c <= '7') {
            int v = c - '0';
            int consumed = 1;
            while (consumed < 3 && i + consumed < len) {
                char dch = s.charAt(i + consumed);
                if (dch < '0' || dch > '7') break;
                v = (v << 3) | (dch - '0');
                consumed++;
            }
            return new EscapeResult((char)v, consumed);
        }
        switch (c) {
            case 'b': return new EscapeResult('\b', 1);
            case 't': return new EscapeResult('\t', 1);
            case 'n': return new EscapeResult('\n', 1);
            case 'f': return new EscapeResult('\f', 1);
            case 'r': return new EscapeResult('\r', 1);
            case '"': return new EscapeResult('"', 1);
            case '\'': return new EscapeResult('\'', 1);
            case '\\': return new EscapeResult('\\', 1);
            default: return new EscapeResult(c, 1);
        }
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static final class EscapeResult {
        final char value;
        final int consumed;
        EscapeResult(char value, int consumed) {
            this.value = value;
            this.consumed = consumed;
        }
    }

    private static Map<Integer,Integer> buildSjisMap() {
        TreeMap<Integer,Integer> map = new TreeMap<Integer,Integer>();
        Charset cs = findCharset();
        int b1;
        int b2;
        for (b1 = 0x81; b1 <= 0xFC; b1++) {
            if (!isLead(b1)) continue;
            for (b2 = 0x40; b2 <= 0xFC; b2++) {
                if (b2 == 0x7F) continue;
                addDecoded(map, cs, (b1 << 8) | b2);
            }
        }
        return map;
    }

    private static Charset findCharset() {
        String[] names = new String[] {"windows-31j", "MS932", "Shift_JIS"};
        int i;
        for (i = 0; i < names.length; i++) {
            try { return Charset.forName(names[i]); } catch (Throwable ignored) {}
        }
        throw new RuntimeException("No Shift-JIS compatible charset available in build JRE");
    }

    private static boolean isLead(int b) {
        return (b >= 0x81 && b <= 0x9F) || (b >= 0xE0 && b <= 0xFC);
    }

    private static void addDecoded(TreeMap<Integer,Integer> map, Charset cs, int code) {
        byte[] bytes = new byte[] { (byte)(code >> 8), (byte)code };
        String s;
        char c;
        try {
            s = new String(bytes, cs.name());
        } catch (Exception ex) {
            return;
        }
        if (s.length() != 1) return;
        c = s.charAt(0);
        if (c == '\uFFFD' || c == 0) return;
        map.put(Integer.valueOf(code), Integer.valueOf(c));
    }

    private static void writeSjisMap(Map<Integer,Integer> map, File out) throws IOException {
        DataOutputStream dos = null;
        try {
            dos = new DataOutputStream(new FileOutputStream(out));
            dos.writeByte('S'); dos.writeByte('M'); dos.writeByte('P'); dos.writeByte('1');
            dos.writeShort(map.size());
            for (Iterator<Map.Entry<Integer,Integer>> it = map.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Integer,Integer> e = it.next();
                dos.writeShort(e.getKey().intValue());
                dos.writeShort(e.getValue().intValue());
            }
        } finally {
            if (dos != null) dos.close();
        }
    }

    private static void writeGlyphs(Font font, TreeSet<Integer> glyphs, File out) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        BufferedImage img = new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        int count = 0;
        try {
            g.setFont(font);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            for (Iterator<Integer> it = glyphs.iterator(); it.hasNext();) {
                int cp = it.next().intValue();
                GlyphBitmap gb = renderGlyph(font, cp);
                if (gb.advance <= 0 && cp != ' ') continue;
                data.writeShort(cp);
                data.writeByte(gb.advance);
                data.write(gb.rows);
                count++;
            }
        } finally {
            g.dispose();
        }
        DataOutputStream dos = null;
        try {
            dos = new DataOutputStream(new FileOutputStream(out));
            dos.writeByte('B'); dos.writeByte('M'); dos.writeByte('F'); dos.writeByte('1');
            dos.writeByte(HEIGHT);
            dos.writeByte(ASCENT);
            dos.writeByte(DESCENT);
            dos.writeShort(count);
            body.writeTo(dos);
        } finally {
            if (dos != null) dos.close();
        }
    }

    private static GlyphBitmap renderGlyph(Font font, int cp) {
        BufferedImage img = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        FontMetrics fm;
        int adv;
        int renderCp = cp;
        int row;
        int col;
        int sx;
        int sy;
        int outX;
        int outY;
        int minX = RENDER_SIZE;
        int minY = RENDER_SIZE;
        int maxX = -1;
        int maxY = -1;
        int minOutY;
        int maxOutY;
        int yShift = 0;
        byte[] rows = new byte[BYTES_PER_GLYPH];
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setFont(font);
            fm = g.getFontMetrics();
            if (cp == 0x2715 && !font.canDisplay((char)cp)) {
                renderCp = font.canDisplay('×') ? '×' : 'X';
            }
            adv = fm.charWidth((char)renderCp);
            if (cp == ' ') adv = Math.max(3, adv);
            if (adv <= 0) adv = isWide(cp) ? 12 : 6;
            if (adv > MAX_WIDTH) adv = MAX_WIDTH;
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(Color.WHITE);
            g.drawString(String.valueOf((char)renderCp), RENDER_X, RENDER_BASELINE);

            for (sy = 0; sy < RENDER_SIZE; sy++) {
                for (sx = 0; sx < RENDER_SIZE; sx++) {
                    if ((img.getRGB(sx, sy) & 0xFFFFFF) != 0) {
                        if (sx < minX) minX = sx;
                        if (sx > maxX) maxX = sx;
                        if (sy < minY) minY = sy;
                        if (sy > maxY) maxY = sy;
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                return new GlyphBitmap(adv, rows);
            }

            minOutY = minY - RENDER_BASELINE + ASCENT;
            maxOutY = maxY - RENDER_BASELINE + ASCENT;
            if (minOutY < 0) yShift = -minOutY;
            if (maxOutY + yShift >= HEIGHT) yShift -= (maxOutY + yShift - (HEIGHT - 1));
            if (minOutY + yShift < 0) yShift = -minOutY;

            for (sy = minY; sy <= maxY; sy++) {
                outY = sy - RENDER_BASELINE + ASCENT + yShift;
                if (outY < 0 || outY >= HEIGHT) continue;
                for (sx = minX; sx <= maxX; sx++) {
                    if ((img.getRGB(sx, sy) & 0xFFFFFF) == 0) continue;
                    outX = sx - RENDER_X;
                    if (outX < 0) outX += (RENDER_X - minX);
                    if (outX >= 0 && outX < adv && outX < MAX_WIDTH) {
                        int off = outY * 2;
                        int mask = ((rows[off] & 0xff) << 8) | (rows[off + 1] & 0xff);
                        mask |= (1 << (15 - outX));
                        rows[off] = (byte)(mask >> 8);
                        rows[off + 1] = (byte)mask;
                    }
                }
            }
        } finally {
            g.dispose();
        }
        return new GlyphBitmap(adv, rows);
    }

    private static boolean isWide(int cp) {
        return (cp >= 0x3000 && cp <= 0x9FFF) || (cp >= 0xF900 && cp <= 0xFAFF) ||
               (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    private static final class GlyphBitmap {
        final int advance;
        final byte[] rows;
        GlyphBitmap(int advance, byte[] rows) {
            this.advance = advance;
            this.rows = rows;
        }
    }
}
