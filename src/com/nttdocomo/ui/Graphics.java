package com.nttdocomo.ui;

public class Graphics {
    public static final int BLACK = 0;
    public static final int BLUE = 1;
    public static final int LIME = 2;
    public static final int AQUA = 3;
    public static final int RED = 4;
    public static final int FUCHSIA = 5;
    public static final int YELLOW = 6;
    public static final int WHITE = 7;
    public static final int GRAY = 8;
    public static final int NAVY = 9;
    public static final int GREEN = 10;
    public static final int TEAL = 11;
    public static final int MAROON = 12;
    public static final int PURPLE = 13;
    public static final int OLIVE = 14;
    public static final int SILVER = 15;

    public static final int FLIP_NONE = 0;
    public static final int FLIP_HORIZONTAL = 1;
    public static final int FLIP_VERTICAL = 2;
    public static final int FLIP_ROTATE = 3;
    public static final int FLIP_ROTATE_LEFT = 4;
    public static final int FLIP_ROTATE_RIGHT = 5;
    public static final int FLIP_ROTATE_RIGHT_HORIZONTAL = 6;
    public static final int FLIP_ROTATE_RIGHT_VERTICAL = 7;

    protected javax.microedition.lcdui.Graphics midpGraphics;
    protected javax.microedition.lcdui.Image backBuffer;
    private javax.microedition.lcdui.Image frontBuffer;
    private javax.microedition.lcdui.Graphics frontGraphics;
    protected javax.microedition.lcdui.Canvas parentCanvas;
    protected int screenWidth;
    protected int screenHeight;
    private int currentARGB = 0xFF000000;
    private int originX;
    private int originY;
    private int flipMode;
    private Font currentFont;
    protected int renderAlpha = 255;
    protected int renderMode = 0;
    protected int renderOption = 0;
    private long lastPassiveFlush;
    private static final long PASSIVE_FLUSH_INTERVAL_MS = 250L;
    private int[] alphaDstScratch;
    private int[] alphaOutScratch;
    private int[] fillScratch;
    private int[] pixelScratch;
    private static final int COMPOSITE_PIXELS = 4096;

    public Graphics() {
    }

    public void init(int width, int height) {
        if (width <= 0) width = 240;
        if (height <= 0) height = 240;
        screenWidth = width;
        screenHeight = height;
        backBuffer = javax.microedition.lcdui.Image.createImage(width, height);
        midpGraphics = backBuffer.getGraphics();
        frontBuffer = javax.microedition.lcdui.Image.createImage(width, height);
        frontGraphics = frontBuffer.getGraphics();
        originX = 0;
        originY = 0;
        setColor(currentARGB);
        if (currentFont != null) setFont(currentFont);
    }

    void init(javax.microedition.lcdui.Image mutableImage) {
        backBuffer = mutableImage;
        midpGraphics = mutableImage.getGraphics();
        frontBuffer = mutableImage;
        frontGraphics = midpGraphics;
        screenWidth = mutableImage.getWidth();
        screenHeight = mutableImage.getHeight();
        originX = 0;
        originY = 0;
        setColor(currentARGB);
        if (currentFont != null) setFont(currentFont);
    }

    protected void ensureSurface() {
        if (backBuffer != null && midpGraphics != null) {
            return;
        }
        int width = screenWidth;
        int height = screenHeight;
        if (parentCanvas != null) {
            int canvasWidth = parentCanvas.getWidth();
            int canvasHeight = parentCanvas.getHeight();
            if (canvasWidth > 0) width = canvasWidth;
            if (canvasHeight > 0) height = canvasHeight;
        }
        if (width <= 0) width = 240;
        if (height <= 0) height = 240;
        init(width, height);
    }

    public javax.microedition.lcdui.Graphics getMIDPGraphics() {
        ensureSurface();
        return midpGraphics;
    }

    public javax.microedition.lcdui.Image getBackBuffer() {
        ensureSurface();
        return backBuffer;
    }

    public javax.microedition.lcdui.Image getDisplayBuffer() {
        ensureSurface();
        return frontBuffer;
    }

    public void lock() {
        ensureSurface();
    }

    public void unlock(boolean flush) {
        ensureSurface();
        if (parentCanvas != null) {
            if (flush) {
                flushToCanvas();
            } else {
                passiveFlushToCanvas();
            }
        }
    }

    private void flushToCanvas() {
        if (frontGraphics != null && backBuffer != null && frontGraphics != midpGraphics) {
            frontGraphics.drawImage(backBuffer, 0, 0, javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
        }
        parentCanvas.repaint();
    }

    private void passiveFlushToCanvas() {
        long now = System.currentTimeMillis();
        if (lastPassiveFlush == 0L || now - lastPassiveFlush >= PASSIVE_FLUSH_INTERVAL_MS) {
            flushToCanvas();
            lastPassiveFlush = now;
        }
    }

    public static int getColorOfRGB(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int getColorOfRGB(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int getColorOfName(int name) {
        switch (name) {
            case BLACK: return getColorOfRGB(0x00, 0x00, 0x00);
            case BLUE: return getColorOfRGB(0x00, 0x00, 0xFF);
            case LIME: return getColorOfRGB(0x00, 0xFF, 0x00);
            case AQUA: return getColorOfRGB(0x00, 0xFF, 0xFF);
            case RED: return getColorOfRGB(0xFF, 0x00, 0x00);
            case FUCHSIA: return getColorOfRGB(0xFF, 0x00, 0xFF);
            case YELLOW: return getColorOfRGB(0xFF, 0xFF, 0x00);
            case WHITE: return getColorOfRGB(0xFF, 0xFF, 0xFF);
            case GRAY: return getColorOfRGB(0x80, 0x80, 0x80);
            case NAVY: return getColorOfRGB(0x00, 0x00, 0x80);
            case GREEN: return getColorOfRGB(0x00, 0x80, 0x00);
            case TEAL: return getColorOfRGB(0x00, 0x80, 0x80);
            case MAROON: return getColorOfRGB(0x80, 0x00, 0x00);
            case PURPLE: return getColorOfRGB(0x80, 0x00, 0x80);
            case OLIVE: return getColorOfRGB(0x80, 0x80, 0x00);
            case SILVER: return getColorOfRGB(0xC0, 0xC0, 0xC0);
            default: return getColorOfRGB(0, 0, 0);
        }
    }

    public void setColor(int argb) {
        ensureSurface();
        /* DoJa historically accepts device color values that look like 0x00RRGGBB;
         * keep those opaque for legacy games.  Real semitransparency for this game
         * is driven through Graphics2.setRenderMode(). */
        if ((argb & 0xFF000000) == 0) {
            argb = 0xFF000000 | (argb & 0x00FFFFFF);
        }
        currentARGB = argb;
        midpGraphics.setColor(argb & 0x00FFFFFF);
    }

    protected void setRenderModeState(int mode, int alpha, int option) {
        renderMode = mode;
        renderAlpha = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        renderOption = option;
    }

    protected int getEffectiveAlpha(int objectAlpha) {
        int a = objectAlpha < 0 ? 0 : (objectAlpha > 255 ? 255 : objectAlpha);
        int colorAlpha = (currentARGB >>> 24) & 0xFF;
        if (colorAlpha < a) a = colorAlpha;
        if (renderAlpha < 255) {
            a = (a * renderAlpha + 127) / 255;
        }
        return a;
    }

    public void setFont(Font font) {
        ensureSurface();
        currentFont = font;
        if (font != null) {
            midpGraphics.setFont(font.getMIDPFont());
        }
    }

    public void setClip(int x, int y, int width, int height) {
        ensureSurface();
        midpGraphics.setClip(x, y, width, height);
    }

    public void clearClip() {
        ensureSurface();
        midpGraphics.setClip(-originX, -originY, screenWidth, screenHeight);
    }

    public void clipRect(int x, int y, int width, int height) {
        ensureSurface();
        midpGraphics.clipRect(x, y, width, height);
    }

    public void setOrigin(int x, int y) {
        ensureSurface();
        midpGraphics.translate(x - originX, y - originY);
        originX = x;
        originY = y;
    }

    public void setFlipMode(int mode) {
        flipMode = mode;
    }

    public void drawImage(Image img, int[] matrix) {
        if (matrix == null || matrix.length < 6 || img == null) return;
        drawImage(img, matrix[4], matrix[5], 0, 0, img.getWidth(), img.getHeight());
    }

    public void drawImage(Image img, int[] matrix, int sx, int sy, int width, int height) {
        if (matrix == null || matrix.length < 6) return;
        drawImage(img, matrix[4], matrix[5], sx, sy, width, height);
    }

    public void drawImage(Image img, int x, int y) {
        if (img == null) return;
        drawImage(img, x, y, 0, 0, img.getWidth(), img.getHeight());
    }

    public void drawImage(Image img, int dx, int dy, int sx, int sy, int width, int height) {
        drawSubImage(img, dx, dy, sx, sy, width, height, width, height);
    }

    public void drawScaledImage(Image img, int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh) {
        drawSubImage(img, dx, dy, sx, sy, sw, sh, dw, dh);
    }

    private void drawSubImage(Image img, int dx, int dy, int sx, int sy, int sw, int sh, int dw, int dh) {
        ensureSurface();
        if (img == null || img.getMIDPImage() == null) return;
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return;
        int imageAlpha = img.getAlpha();
        if (imageAlpha <= 0) return;
        javax.microedition.lcdui.Image src = img.getMIDPImage();
        int imgW = src.getWidth();
        int imgH = src.getHeight();
        if (sx < 0) { sw += sx; sx = 0; }
        if (sy < 0) { sh += sy; sy = 0; }
        if (sx + sw > imgW) sw = imgW - sx;
        if (sy + sh > imgH) sh = imgH - sy;
        if (sw <= 0 || sh <= 0) return;

        if (imageAlpha >= 255 && renderAlpha >= 255 && sw == dw && sh == dh) {
            if (flipMode == FLIP_NONE) {
                int cx = midpGraphics.getClipX();
                int cy = midpGraphics.getClipY();
                int cw = midpGraphics.getClipWidth();
                int ch = midpGraphics.getClipHeight();
                midpGraphics.clipRect(dx, dy, dw, dh);
                midpGraphics.drawImage(src, dx - sx, dy - sy, javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
                midpGraphics.setClip(cx, cy, cw, ch);
                return;
            }
            try {
                midpGraphics.drawRegion(src, sx, sy, sw, sh, toMIDPTransform(flipMode), dx, dy,
                    javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
                return;
            } catch (Throwable ignored) {
                /* Fall back to the manual path on emulators/devices with incomplete MIDP 2.0 drawRegion. */
            }
        }

        int[] pixels = new int[sw * sh];
        src.getRGB(pixels, 0, sw, sx, sy, sw, sh);
        if (imageAlpha < 255) {
            applyImageAlpha(pixels, imageAlpha);
        }
        int[] scaled = scalePixels(pixels, sw, sh, dw, dh);
        int outW = dw;
        int outH = dh;
        int[] transformed = transformPixels(scaled, dw, dh, flipMode);
        if (isRotated(flipMode)) {
            outW = dh;
            outH = dw;
        }
        drawRGBComposite(transformed, 0, outW, dx, dy, outW, outH, true);
    }

    private static int toMIDPTransform(int mode) {
        switch (mode) {
            case FLIP_HORIZONTAL: return 2;              /* Sprite.TRANS_MIRROR */
            case FLIP_VERTICAL: return 1;                /* Sprite.TRANS_MIRROR_ROT180 */
            case FLIP_ROTATE: return 3;                  /* Sprite.TRANS_ROT180 */
            case FLIP_ROTATE_LEFT: return 6;             /* Sprite.TRANS_ROT270 */
            case FLIP_ROTATE_RIGHT: return 5;            /* Sprite.TRANS_ROT90 */
            case FLIP_ROTATE_RIGHT_HORIZONTAL: return 4; /* Sprite.TRANS_MIRROR_ROT270 */
            case FLIP_ROTATE_RIGHT_VERTICAL: return 7;   /* Sprite.TRANS_MIRROR_ROT90 */
            default: return 0;                           /* Sprite.TRANS_NONE */
        }
    }

    private static boolean isRotated(int mode) {
        return mode == FLIP_ROTATE_LEFT || mode == FLIP_ROTATE_RIGHT || mode == FLIP_ROTATE_RIGHT_HORIZONTAL || mode == FLIP_ROTATE_RIGHT_VERTICAL;
    }

    private static int[] scalePixels(int[] src, int sw, int sh, int dw, int dh) {
        int[] dst;
        int x;
        int y;
        if (sw == dw && sh == dh) {
            return src;
        }
        dst = new int[dw * dh];
        for (y = 0; y < dh; y++) {
            int sy = y * sh / dh;
            for (x = 0; x < dw; x++) {
                dst[y * dw + x] = src[sy * sw + (x * sw / dw)];
            }
        }
        return dst;
    }

    private static int[] transformPixels(int[] src, int w, int h, int mode) {
        int outW = isRotated(mode) ? h : w;
        int outH = isRotated(mode) ? w : h;
        int[] dst = new int[outW * outH];
        int x;
        int y;
        for (y = 0; y < outH; y++) {
            for (x = 0; x < outW; x++) {
                int sx = x;
                int sy = y;
                switch (mode) {
                    case FLIP_HORIZONTAL: sx = w - 1 - x; sy = y; break;
                    case FLIP_VERTICAL: sx = x; sy = h - 1 - y; break;
                    case FLIP_ROTATE: sx = w - 1 - x; sy = h - 1 - y; break;
                    case FLIP_ROTATE_LEFT: sx = w - 1 - y; sy = x; break;
                    case FLIP_ROTATE_RIGHT: sx = y; sy = h - 1 - x; break;
                    case FLIP_ROTATE_RIGHT_HORIZONTAL: sx = y; sy = x; break;
                    case FLIP_ROTATE_RIGHT_VERTICAL: sx = w - 1 - y; sy = h - 1 - x; break;
                    default: sx = x; sy = y; break;
                }
                if (sx >= 0 && sx < w && sy >= 0 && sy < h) {
                    dst[y * outW + x] = src[sy * w + sx];
                }
            }
        }
        return dst;
    }

    private static void applyImageAlpha(int[] pixels, int alpha) {
        int i;
        for (i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int srcAlpha = (pixel >>> 24) & 0xFF;
            int outAlpha = (srcAlpha * alpha + 127) / 255;
            pixels[i] = (pixel & 0x00FFFFFF) | (outAlpha << 24);
        }
    }

    public void drawString(String str, int x, int y) {
        ensureSurface();
        if (str == null) str = "";
        if (BitmapFont.isLoaded()) {
            int alpha = getEffectiveAlpha(255);
            int argb = (currentARGB & 0x00FFFFFF) | (alpha << 24);
            if (BitmapFont.drawString(this, str, x, y, argb, currentFont)) {
                return;
            }
        }
        int yy = y;
        if (currentFont != null) {
            yy += currentFont.getBaselineShift();
        }
        midpGraphics.drawString(str, x, yy, javax.microedition.lcdui.Graphics.BASELINE | javax.microedition.lcdui.Graphics.LEFT);
    }

    public void drawChars(char[] data, int x, int y, int off, int len) {
        if (data == null || len <= 0) return;
        drawString(new String(data, off, len), x, y);
    }

    public void setPictoColorEnabled(boolean b) {
        /* Pictogram color is device specific; MIDP text drawing keeps the active color. */
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        ensureSurface();
        midpGraphics.drawLine(x1, y1, x2, y2);
    }

    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        drawPolyline(xPoints, yPoints, 0, nPoints);
    }

    public void drawPolyline(int[] xPoints, int[] yPoints, int offset, int count) {
        ensureSurface();
        int i;
        if (xPoints == null || yPoints == null || count < 2) return;
        for (i = offset; i < offset + count - 1; i++) {
            midpGraphics.drawLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }
    }

    public void drawRect(int x, int y, int w, int h) {
        ensureSurface();
        midpGraphics.drawRect(x, y, w, h);
    }

    public void fillRect(int x, int y, int w, int h) {
        ensureSurface();
        int alpha = getEffectiveAlpha(255);
        if (alpha == 0 || w <= 0 || h <= 0) return;
        if (alpha < 255) {
            fillRectAlphaTiled(x, y, w, h, alpha);
        } else {
            midpGraphics.fillRect(x, y, w, h);
        }
    }

    public void clearRect(int x, int y, int w, int h) {
        ensureSurface();
        int old = currentARGB;
        setColor(getColorOfRGB(255, 255, 255));
        midpGraphics.fillRect(x, y, w, h);
        setColor(old);
    }

    public void copyArea(int sx, int sy, int width, int height, int dx, int dy) {
        ensureSurface();
        if (width <= 0 || height <= 0) return;
        /* DoJa's dx/dy are offsets from the source rectangle. */
        int dstX = sx + dx;
        int dstY = sy + dy;
        try {
            midpGraphics.copyArea(sx, sy, width, height, dstX, dstY,
                javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
            return;
        } catch (Throwable ignored) {
            /* Keep the old getRGB path as a safety net. */
        }
        int srcPhysX = sx + originX;
        int srcPhysY = sy + originY;
        if (srcPhysX < 0) { int d = -srcPhysX; srcPhysX = 0; sx += d; dstX += d; width -= d; }
        if (srcPhysY < 0) { int d = -srcPhysY; srcPhysY = 0; sy += d; dstY += d; height -= d; }
        if (srcPhysX + width > screenWidth) width = screenWidth - srcPhysX;
        if (srcPhysY + height > screenHeight) height = screenHeight - srcPhysY;
        if (width <= 0 || height <= 0) return;
        ensureScratch(width * height);
        backBuffer.getRGB(fillScratch, 0, width, srcPhysX, srcPhysY, width, height);
        midpGraphics.drawRGB(fillScratch, 0, width, dstX, dstY, width, height, false);
    }

    public void setPixel(int x, int y) {
        ensureSurface();
        midpGraphics.drawLine(x, y, x, y);
    }

    public void setPixel(int x, int y, int color) {
        int old = currentARGB;
        setColor(color);
        setPixel(x, y);
        setColor(old);
    }

    public void setRGBPixel(int x, int y, int pixel) {
        ensureSurface();
        if (pixelScratch == null) pixelScratch = new int[1];
        pixelScratch[0] = 0xFF000000 | (pixel & 0x00FFFFFF);
        midpGraphics.drawRGB(pixelScratch, 0, 1, x, y, 1, 1, false);
    }

    public int getPixel(int x, int y) {
        ensureSurface();
        if (pixelScratch == null) pixelScratch = new int[1];
        backBuffer.getRGB(pixelScratch, 0, 1, x + originX, y + originY, 1, 1);
        return pixelScratch[0];
    }

    public int getRGBPixel(int x, int y) {
        return getPixel(x, y) & 0x00FFFFFF;
    }

    public int[] getPixels(int x, int y, int width, int height, int[] pixels, int off) {
        ensureSurface();
        if (pixels == null) {
            pixels = new int[off + width * height];
        }
        backBuffer.getRGB(pixels, off, width, x + originX, y + originY, width, height);
        return pixels;
    }

    public int[] getRGBPixels(int x, int y, int width, int height, int[] pixels, int off) {
        pixels = getPixels(x, y, width, height, pixels, off);
        int i;
        int count = width * height;
        for (i = 0; i < count; i++) {
            pixels[off + i] &= 0x00FFFFFF;
        }
        return pixels;
    }

    public void setPixels(int x, int y, int width, int height, int[] pixels, int off) {
        ensureSurface();
        if (pixels == null || width <= 0 || height <= 0) return;
        drawRGBComposite(pixels, off, width, x, y, width, height, true);
    }

    public void setRGBPixels(int x, int y, int width, int height, int[] pixels, int off) {
        ensureSurface();
        if (pixels == null || width <= 0 || height <= 0) return;
        int rowsPerChunk = COMPOSITE_PIXELS / width;
        int row;
        int r;
        int col;
        if (rowsPerChunk <= 0) rowsPerChunk = 1;
        ensureScratch(width * rowsPerChunk);
        for (row = 0; row < height; row += rowsPerChunk) {
            int rows = height - row;
            if (rows > rowsPerChunk) rows = rowsPerChunk;
            for (r = 0; r < rows; r++) {
                for (col = 0; col < width; col++) {
                    fillScratch[r * width + col] = 0xFF000000 | (pixels[off + (row + r) * width + col] & 0x00FFFFFF);
                }
            }
            midpGraphics.drawRGB(fillScratch, 0, width, x, y + row, width, rows, false);
        }
    }

    public void drawRGB(int[] rgb, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {
        ensureSurface();
        drawRGBComposite(rgb, offset, scanlength, x, y, width, height, processAlpha);
    }

    protected void drawRGBComposite(int[] rgb, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {
        ensureSurface();
        if (rgb == null || width <= 0 || height <= 0) return;
        if (!processAlpha) {
            midpGraphics.drawRGB(rgb, offset, scanlength, x, y, width, height, false);
            return;
        }
        int effective = renderAlpha;
        boolean hasAlpha = effective < 255;
        int row;
        int col;
        int idx;
        if (!hasAlpha) {
            outer: for (row = 0; row < height; row++) {
                idx = offset + row * scanlength;
                for (col = 0; col < width; col++) {
                    if (((rgb[idx + col] >>> 24) & 0xFF) != 255) { hasAlpha = true; break outer; }
                }
            }
        }
        if (!hasAlpha) {
            midpGraphics.drawRGB(rgb, offset, scanlength, x, y, width, height, false);
            return;
        }
        int physX = x + originX;
        int physY = y + originY;
        int drawX = x;
        int drawY = y;
        int srcOffset = offset;
        int w = width;
        int h = height;
        if (physX < 0) { int d = -physX; physX = 0; drawX += d; srcOffset += d; w -= d; }
        if (physY < 0) { int d = -physY; physY = 0; drawY += d; srcOffset += d * scanlength; h -= d; }
        if (physX + w > screenWidth) w = screenWidth - physX;
        if (physY + h > screenHeight) h = screenHeight - physY;
        if (w <= 0 || h <= 0) return;
        int rowsPerChunk = COMPOSITE_PIXELS / w;
        if (rowsPerChunk <= 0) rowsPerChunk = 1;
        ensureCompositeScratch(w * rowsPerChunk);
        for (row = 0; row < h; row += rowsPerChunk) {
            int rows = h - row;
            if (rows > rowsPerChunk) rows = rowsPerChunk;
            compositeChunk(rgb, srcOffset + row * scanlength, scanlength, physX, physY + row, drawX, drawY + row, w, rows, effective);
        }
    }

    private void fillRectAlphaTiled(int x, int y, int w, int h, int alpha) {
        int physX = x + originX;
        int physY = y + originY;
        int drawX = x;
        int drawY = y;
        if (physX < 0) { int d = -physX; physX = 0; drawX += d; w -= d; }
        if (physY < 0) { int d = -physY; physY = 0; drawY += d; h -= d; }
        if (physX + w > screenWidth) w = screenWidth - physX;
        if (physY + h > screenHeight) h = screenHeight - physY;
        if (w <= 0 || h <= 0) return;
        int rowsPerChunk = COMPOSITE_PIXELS / w;
        if (rowsPerChunk <= 0) rowsPerChunk = 1;
        ensureCompositeScratch(w * rowsPerChunk);
        int row;
        int color = currentARGB & 0x00FFFFFF;
        for (row = 0; row < h; row += rowsPerChunk) {
            int rows = h - row;
            if (rows > rowsPerChunk) rows = rowsPerChunk;
            int count = w * rows;
            backBuffer.getRGB(alphaDstScratch, 0, w, physX, physY + row, w, rows);
            int i;
            int inv = 255 - alpha;
            int sr = (color >> 16) & 0xFF;
            int sg = (color >> 8) & 0xFF;
            int sb = color & 0xFF;
            for (i = 0; i < count; i++) {
                int d = alphaDstScratch[i];
                int dr = (d >> 16) & 0xFF;
                int dg = (d >> 8) & 0xFF;
                int db = d & 0xFF;
                alphaOutScratch[i] = 0xFF000000
                    | (((sr * alpha + dr * inv + 127) / 255) << 16)
                    | (((sg * alpha + dg * inv + 127) / 255) << 8)
                    | ((sb * alpha + db * inv + 127) / 255);
            }
            midpGraphics.drawRGB(alphaOutScratch, 0, w, drawX, drawY + row, w, rows, false);
        }
    }

    private void compositeChunk(int[] rgb, int srcOffset, int scanlength, int physX, int physY, int drawX, int drawY, int w, int h, int effective) {
        int row;
        int col;
        int s;
        int d;
        int a;
        int inv;
        int sr;
        int sg;
        int sb;
        int dr;
        int dg;
        int db;
        int count = w * h;
        ensureCompositeScratch(count);
        backBuffer.getRGB(alphaDstScratch, 0, w, physX, physY, w, h);
        for (row = 0; row < h; row++) {
            for (col = 0; col < w; col++) {
                s = rgb[srcOffset + row * scanlength + col];
                a = (s >>> 24) & 0xFF;
                if (effective < 255) a = (a * effective + 127) / 255;
                d = alphaDstScratch[row * w + col];
                if (a <= 0) {
                    alphaOutScratch[row * w + col] = d | 0xFF000000;
                } else if (a >= 255) {
                    alphaOutScratch[row * w + col] = s | 0xFF000000;
                } else {
                    inv = 255 - a;
                    sr = (s >> 16) & 0xFF; sg = (s >> 8) & 0xFF; sb = s & 0xFF;
                    dr = (d >> 16) & 0xFF; dg = (d >> 8) & 0xFF; db = d & 0xFF;
                    alphaOutScratch[row * w + col] = 0xFF000000
                        | (((sr * a + dr * inv + 127) / 255) << 16)
                        | (((sg * a + dg * inv + 127) / 255) << 8)
                        | ((sb * a + db * inv + 127) / 255);
                }
            }
        }
        midpGraphics.drawRGB(alphaOutScratch, 0, w, drawX, drawY, w, h, false);
    }

    private void ensureCompositeScratch(int size) {
        if (alphaDstScratch == null || alphaDstScratch.length < size) {
            alphaDstScratch = new int[size];
            alphaOutScratch = new int[size];
        }
    }

    private void ensureScratch(int size) {
        if (fillScratch == null || fillScratch.length < size) {
            fillScratch = new int[size];
        }
    }

    public void fillArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        ensureSurface();
        midpGraphics.fillArc(x, y, w, h, startAngle, arcAngle);
    }

    public void drawArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        ensureSurface();
        midpGraphics.drawArc(x, y, w, h, startAngle, arcAngle);
    }

    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        fillPolygon(xPoints, yPoints, 0, nPoints);
    }

    public void fillPolygon(int[] xPoints, int[] yPoints, int offset, int count) {
        ensureSurface();
        int i;
        if (xPoints == null || yPoints == null || count < 3) return;
        for (i = offset + 1; i < offset + count - 1; i++) {
            midpGraphics.fillTriangle(xPoints[offset], yPoints[offset], xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }
    }

    public Graphics copy() {
        Graphics g = new Graphics();
        g.currentARGB = currentARGB;
        g.flipMode = flipMode;
        g.currentFont = currentFont;
        g.renderAlpha = renderAlpha;
        g.renderMode = renderMode;
        g.renderOption = renderOption;
        return g;
    }

    public void dispose() {
        backBuffer = null;
        frontBuffer = null;
        midpGraphics = null;
        frontGraphics = null;
    }
}
