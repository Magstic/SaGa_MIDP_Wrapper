package com.nttdocomo.ui;

public class Font {
    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_HEADING = 1;

    public static final int FACE_SYSTEM = 0x71000000;
    public static final int FACE_MONOSPACE = 0x72000000;
    public static final int FACE_PROPORTIONAL = 0x73000000;

    public static final int STYLE_PLAIN = 0x70100000;
    public static final int STYLE_BOLD = 0x70110000;
    public static final int STYLE_ITALIC = 0x70120000;
    public static final int STYLE_BOLDITALIC = 0x70130000;

    public static final int SIZE_SMALL = 0x70000100;
    public static final int SIZE_MEDIUM = 0x70000200;
    public static final int SIZE_LARGE = 0x70000300;
    public static final int SIZE_TINY = 0x70000400;

    private static Font defaultFont;
    private final javax.microedition.lcdui.Font midpFont;
    private final int type;
    private final int height;
    private final int ascent;
    private final int descent;
    private final int baselineShift;
    private final int widthScalePercent;

    private Font(int fontType, javax.microedition.lcdui.Font font, int h, int asc, int desc) {
        type = fontType;
        midpFont = font;
        height = h;
        ascent = asc;
        descent = desc;
        int midpBaseline = 0;
        try { midpBaseline = font.getBaselinePosition(); } catch (Throwable t) { midpBaseline = asc; }
        /*
         * DoJa's Graphics.drawString() uses the baseline too, but DoJa's tiny
         * system font metrics on 505i/900i-era devices are tighter than many
         * MIDP fonts exposed by emulators.  The shift maps a DoJa baseline to
         * the underlying MIDP baseline so that code using y + getHeight()
         * positions text like on DoJa instead of drifting downward.
         */
        baselineShift = midpBaseline - asc;
        widthScalePercent = 100;
    }

    public static Font getDefaultFont() {
        if (defaultFont == null) {
            defaultFont = getFont(FACE_SYSTEM | STYLE_PLAIN | SIZE_TINY);
        }
        return defaultFont;
    }

    public static void setDefaultFont(Font f) {
        if (f != null) {
            defaultFont = f;
        }
    }

    public static Font getFont(int type) {
        if (type == TYPE_DEFAULT || type == TYPE_HEADING) {
            return getDefaultFont();
        }
        int face = decodeFace(type);
        int style = decodeStyle(type);
        int size = decodeSize(type);
        javax.microedition.lcdui.Font midp = javax.microedition.lcdui.Font.getFont(face, style, size);
        return new Font(type, midp, logicalHeight(type), logicalAscent(type), logicalDescent(type));
    }

    public static Font getFont(int type, int inputSize) {
        if (inputSize > 0) {
            int face = decodeFace(type);
            int style = decodeStyle(type);
            int sizeConst;
            if (inputSize <= 12) sizeConst = SIZE_TINY;
            else if (inputSize <= 14) sizeConst = SIZE_SMALL;
            else if (inputSize <= 18) sizeConst = SIZE_MEDIUM;
            else sizeConst = SIZE_LARGE;
            javax.microedition.lcdui.Font midp = javax.microedition.lcdui.Font.getFont(face, style, decodeSize(sizeConst));
            int h = inputSize;
            int desc = h <= 12 ? 1 : 2;
            return new Font((type & 0xFFFF00FF) | sizeConst, midp, h, h - desc, desc);
        }
        return getFont(type);
    }

    public static int[] getSupportedFontSizes() {
        return new int[] { 12, 14, 16, 20 };
    }

    private static int decodeFace(int type) {
        int face = type & 0x0F000000;
        if (face == (FACE_MONOSPACE & 0x0F000000)) {
            return javax.microedition.lcdui.Font.FACE_MONOSPACE;
        }
        if (face == (FACE_PROPORTIONAL & 0x0F000000)) {
            return javax.microedition.lcdui.Font.FACE_PROPORTIONAL;
        }
        return javax.microedition.lcdui.Font.FACE_SYSTEM;
    }

    private static int decodeStyle(int type) {
        int styleBits = type & 0x000F0000;
        if (styleBits == 0x00010000) {
            return javax.microedition.lcdui.Font.STYLE_BOLD;
        }
        if (styleBits == 0x00020000) {
            return javax.microedition.lcdui.Font.STYLE_ITALIC;
        }
        if (styleBits == 0x00030000) {
            return javax.microedition.lcdui.Font.STYLE_BOLD | javax.microedition.lcdui.Font.STYLE_ITALIC;
        }
        return javax.microedition.lcdui.Font.STYLE_PLAIN;
    }

    private static int decodeSize(int type) {
        int sizeBits = type & 0x00000F00;
        if (sizeBits == 0x00000100 || sizeBits == 0x00000400) {
            return javax.microedition.lcdui.Font.SIZE_SMALL;
        }
        if (sizeBits == 0x00000300) {
            return javax.microedition.lcdui.Font.SIZE_LARGE;
        }
        return javax.microedition.lcdui.Font.SIZE_MEDIUM;
    }

    private static int sizeBits(int type) {
        int b = type & 0x00000F00;
        return b == 0 ? (SIZE_TINY & 0x00000F00) : b;
    }

    private static int logicalHeight(int type) {
        switch (sizeBits(type)) {
            case 0x00000100: return 14;  // SIZE_SMALL
            case 0x00000200: return 16;  // SIZE_MEDIUM
            case 0x00000300: return 20;  // SIZE_LARGE
            case 0x00000400: return 12;  // SIZE_TINY
            default: return 12;
        }
    }

    private static int logicalDescent(int type) {
        switch (sizeBits(type)) {
            case 0x00000100: return 2;
            case 0x00000200: return 2;
            case 0x00000300: return 3;
            case 0x00000400: return 1;
            default: return 1;
        }
    }

    private static int logicalAscent(int type) {
        int h = logicalHeight(type);
        return h - logicalDescent(type);
    }

    javax.microedition.lcdui.Font getMIDPFont() {
        return midpFont;
    }

    int getBaselineShift() {
        return baselineShift;
    }

    int getType() {
        return type;
    }

    public int getAscent() {
        return ascent;
    }

    public int getDescent() {
        return descent;
    }

    public int stringWidth(String s) {
        if (BitmapFont.isLoaded()) {
            return BitmapFont.stringWidth(s == null ? "" : s);
        }
        int w = midpFont.stringWidth(s == null ? "" : s);
        return (w * widthScalePercent + 50) / 100;
    }

    public int getBBoxWidth(String s) {
        return stringWidth(s);
    }

    public int getBBoxHeight(String s) {
        return getHeight();
    }

    public int getHeight() {
        return height;
    }

    public int getLineBreak(String str, int off, int len, int width) {
        int i;
        int last = off;
        if (str == null) return off;
        for (i = off + 1; i <= off + len && i <= str.length(); i++) {
            if (stringWidth(str.substring(off, i)) > width) {
                return last == off ? i - 1 : last;
            }
            last = i;
        }
        return off + len;
    }
}
