package com.nttdocomo.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PalettedImage extends Image {
    private int[] indexedPixels;
    private int[] basePalette;
    private Palette palette;
    private Palette originalPalette;
    private int width;
    private int height;
    private int transparentIndex = -1;
    private boolean transparentEnabled = true;

    private PalettedImage(int w, int h) {
        super(javax.microedition.lcdui.Image.createImage(w, h));
        width = w;
        height = h;
        indexedPixels = new int[w * h];
        basePalette = new int[256];
        int i;
        for (i = 0; i < basePalette.length; i++) {
            basePalette[i] = 0xFF000000;
        }
        palette = new Palette(basePalette);
        originalPalette = palette;
        rebuildImage();
    }

    private PalettedImage(GifImage gif) {
        super(javax.microedition.lcdui.Image.createImage(1, 1));
        applyGif(gif);
    }

    public static PalettedImage createPalettedImage(byte[] data) {
        try {
            return new PalettedImage(GifImage.decode(data));
        } catch (Throwable t) {
            return createFromDecodedImage(javax.microedition.lcdui.Image.createImage(data, 0, data.length));
        }
    }

    public static PalettedImage createPalettedImage(InputStream in) throws IOException {
        return createPalettedImage(readAll(in));
    }

    public static PalettedImage createPalettedImage(int width, int height) {
        return new PalettedImage(width, height);
    }

    public void changeData(byte[] data) {
        try {
            applyGif(GifImage.decode(data));
        } catch (Throwable t) {
            PalettedImage decoded = createFromDecodedImage(javax.microedition.lcdui.Image.createImage(data, 0, data.length));
            this.width = decoded.width;
            this.height = decoded.height;
            this.indexedPixels = decoded.indexedPixels;
            this.basePalette = decoded.basePalette;
            this.palette = decoded.palette;
            this.originalPalette = decoded.originalPalette;
            this.transparentIndex = decoded.transparentIndex;
            this.transparentEnabled = decoded.transparentEnabled;
            rebuildImage();
        }
    }

    public void changeData(InputStream in) throws IOException {
        changeData(readAll(in));
    }

    public Palette getPalette() {
        return palette;
    }

    public void setPalette(Palette newPalette) {
        if (newPalette == null) {
            return;
        }
        palette = newPalette;
        rebuildImage();
    }

    public void setTransparentIndex(int index) {
        transparentIndex = index;
        rebuildImage();
    }

    public int getTransparentIndex() {
        return transparentIndex;
    }

    public void setTransparentEnabled(boolean enabled) {
        transparentEnabled = enabled;
        rebuildImage();
    }

    public void setTransparentColor(int color) {
        int i;
        int rgb = color & 0x00FFFFFF;
        if (palette != null) {
            for (i = 0; i < palette.getEntryCount(); i++) {
                if ((palette.getEntry(i) & 0x00FFFFFF) == rgb) {
                    transparentIndex = i;
                    break;
                }
            }
        }
        rebuildImage();
    }

    public int getTransparentColor() {
        if (transparentIndex >= 0 && palette != null && transparentIndex < palette.getEntryCount()) {
            return palette.getEntry(transparentIndex);
        }
        return 0;
    }

    private void applyGif(GifImage gif) {
        width = gif.width;
        height = gif.height;
        indexedPixels = gif.indexedPixels;
        basePalette = gif.palette;
        palette = new Palette(basePalette);
        originalPalette = palette;
        transparentIndex = gif.transparentIndex;
        transparentEnabled = true;
        rebuildImage();
    }

    private void rebuildImage() {
        int count;
        int[] out;
        int i;
        int idx;
        int color;
        Palette active = palette;
        boolean useBase = (active == originalPalette && basePalette != null);
        if (indexedPixels == null || width <= 0 || height <= 0) {
            setMIDPImage(javax.microedition.lcdui.Image.createImage(1, 1));
            return;
        }
        count = width * height;
        out = new int[count];
        for (i = 0; i < count; i++) {
            idx = indexedPixels[i];
            if (transparentEnabled && idx == transparentIndex) {
                out[i] = 0x00000000;
            } else if (useBase && idx >= 0 && idx < basePalette.length) {
                out[i] = normalizeColor(basePalette[idx]);
            } else if (idx >= 0 && active != null && idx < active.getEntryCount()) {
                color = active.getEntry(idx);
                out[i] = normalizeColor(color);
            } else if (idx >= 0 && basePalette != null && idx < basePalette.length) {
                out[i] = normalizeColor(basePalette[idx]);
            } else {
                out[i] = 0x00000000;
            }
        }
        setMIDPImage(javax.microedition.lcdui.Image.createRGBImage(out, width, height, true));
    }

    private static int normalizeColor(int color) {
        if ((color & 0xFF000000) == 0 && (color & 0x00FFFFFF) != 0) {
            return 0xFF000000 | (color & 0x00FFFFFF);
        }
        if (color == 0) {
            return 0xFF000000;
        }
        return color;
    }

    private static PalettedImage createFromDecodedImage(javax.microedition.lcdui.Image image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] rgb = new int[w * h];
        int[] colors = new int[256];
        int[] indices = new int[w * h];
        int count = 0;
        int i;
        int j;
        int color;
        image.getRGB(rgb, 0, w, 0, 0, w, h);
        for (i = 0; i < rgb.length; i++) {
            color = rgb[i];
            if ((color & 0xFF000000) == 0) {
                indices[i] = 0;
                continue;
            }
            for (j = 0; j < count; j++) {
                if ((colors[j] & 0x00FFFFFF) == (color & 0x00FFFFFF)) {
                    break;
                }
            }
            if (j == count && count < colors.length) {
                colors[count++] = color;
                j = count - 1;
            }
            indices[i] = j;
        }
        if (count == 0) {
            count = 1;
            colors[0] = 0xFF000000;
        }
        int[] palette = new int[count];
        System.arraycopy(colors, 0, palette, 0, count);
        GifImage gif = new GifImage();
        gif.width = w;
        gif.height = h;
        gif.indexedPixels = indices;
        gif.palette = palette;
        gif.transparentIndex = -1;
        return new PalettedImage(gif);
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }

    private static final class GifImage {
        int width;
        int height;
        int[] indexedPixels;
        int[] palette;
        int transparentIndex;

        static GifImage decode(byte[] data) throws IOException {
            GifReader r = new GifReader(data);
            return r.decode();
        }
    }

    private static final class GifReader {
        private final byte[] data;
        private int pos;
        private int screenWidth;
        private int screenHeight;
        private int[] globalPalette;
        private int transparentIndex = -1;

        GifReader(byte[] bytes) {
            data = bytes == null ? new byte[0] : bytes;
        }

        GifImage decode() throws IOException {
            int packed;
            int gctSize;
            if (data.length < 13 || data[0] != 'G' || data[1] != 'I' || data[2] != 'F') {
                throw new IOException("not a GIF");
            }
            pos = 6;
            screenWidth = readUnsignedShortLE();
            screenHeight = readUnsignedShortLE();
            packed = readUnsignedByte();
            readUnsignedByte();
            readUnsignedByte();
            if ((packed & 0x80) != 0) {
                gctSize = 1 << ((packed & 0x07) + 1);
                globalPalette = readColorTable(gctSize);
            }
            while (pos < data.length) {
                int b = readUnsignedByte();
                if (b == 0x2C) {
                    return readImage();
                } else if (b == 0x21) {
                    readExtension();
                } else if (b == 0x3B) {
                    break;
                } else {
                    throw new IOException("bad GIF block");
                }
            }
            throw new IOException("no GIF image block");
        }

        private GifImage readImage() throws IOException {
            int left = readUnsignedShortLE();
            int top = readUnsignedShortLE();
            int imageWidth = readUnsignedShortLE();
            int imageHeight = readUnsignedShortLE();
            int packed = readUnsignedByte();
            boolean hasLocalPalette = (packed & 0x80) != 0;
            boolean interlaced = (packed & 0x40) != 0;
            int[] activePalette = hasLocalPalette ? readColorTable(1 << ((packed & 0x07) + 1)) : globalPalette;
            int lzwMinCodeSize = readUnsignedByte();
            byte[] compressed = readSubBlocks();
            int[] imageIndices = decodeLzw(compressed, lzwMinCodeSize, imageWidth * imageHeight);
            int outWidth = (left == 0 && top == 0) ? imageWidth : screenWidth;
            int outHeight = (left == 0 && top == 0) ? imageHeight : screenHeight;
            int[] out = new int[outWidth * outHeight];
            int fillIndex = transparentIndex >= 0 ? transparentIndex : 0;
            int i;
            for (i = 0; i < out.length; i++) {
                out[i] = fillIndex;
            }
            blitIndices(imageIndices, imageWidth, imageHeight, interlaced, out, outWidth, outHeight, left, top);
            GifImage gif = new GifImage();
            gif.width = outWidth;
            gif.height = outHeight;
            gif.indexedPixels = out;
            gif.palette = activePalette == null ? new int[0] : activePalette;
            gif.transparentIndex = transparentIndex;
            return gif;
        }

        private void blitIndices(int[] src, int sw, int sh, boolean interlaced, int[] dst, int dw, int dh, int left, int top) {
            int[] rowOrder = interlaced ? interlacedRows(sh) : null;
            int sourceRow;
            int targetRow;
            int x;
            int y;
            int si;
            for (y = 0; y < sh; y++) {
                sourceRow = interlaced ? rowOrder[y] : y;
                targetRow = top + sourceRow;
                if (targetRow < 0 || targetRow >= dh) {
                    continue;
                }
                for (x = 0; x < sw; x++) {
                    si = y * sw + x;
                    if (si >= src.length) {
                        return;
                    }
                    if (left + x >= 0 && left + x < dw) {
                        dst[targetRow * dw + left + x] = src[si];
                    }
                }
            }
        }

        private int[] interlacedRows(int height) {
            int[] rows = new int[height];
            int count = 0;
            int y;
            for (y = 0; y < height; y += 8) rows[count++] = y;
            for (y = 4; y < height; y += 8) rows[count++] = y;
            for (y = 2; y < height; y += 4) rows[count++] = y;
            for (y = 1; y < height; y += 2) rows[count++] = y;
            return rows;
        }

        private void readExtension() throws IOException {
            int label = readUnsignedByte();
            if (label == 0xF9) {
                int size = readUnsignedByte();
                if (size != 4) {
                    skipBytes(size);
                    skipSubBlocks();
                    return;
                }
                int packed = readUnsignedByte();
                readUnsignedShortLE();
                int t = readUnsignedByte();
                readUnsignedByte();
                transparentIndex = ((packed & 0x01) != 0) ? t : -1;
            } else {
                skipSubBlocks();
            }
        }

        private int[] readColorTable(int count) throws IOException {
            int[] table = new int[count];
            int i;
            for (i = 0; i < count; i++) {
                int r = readUnsignedByte();
                int g = readUnsignedByte();
                int b = readUnsignedByte();
                table[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            return table;
        }

        private byte[] readSubBlocks() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (true) {
                int size = readUnsignedByte();
                if (size == 0) {
                    break;
                }
                ensure(size);
                out.write(data, pos, size);
                pos += size;
            }
            return out.toByteArray();
        }

        private void skipSubBlocks() throws IOException {
            while (true) {
                int size = readUnsignedByte();
                if (size == 0) {
                    return;
                }
                skipBytes(size);
            }
        }

        private void skipBytes(int count) throws IOException {
            ensure(count);
            pos += count;
        }

        private int readUnsignedByte() throws IOException {
            ensure(1);
            return data[pos++] & 0xFF;
        }

        private int readUnsignedShortLE() throws IOException {
            int lo = readUnsignedByte();
            int hi = readUnsignedByte();
            return lo | (hi << 8);
        }

        private void ensure(int count) throws IOException {
            if (count < 0 || pos + count > data.length) {
                throw new IOException("truncated GIF");
            }
        }
    }

    private static int[] decodeLzw(byte[] compressed, int minCodeSize, int expectedPixels) throws IOException {
        int clearCode = 1 << minCodeSize;
        int endCode = clearCode + 1;
        int nextCode = endCode + 1;
        int codeSize = minCodeSize + 1;
        int codeMask = (1 << codeSize) - 1;
        int[] prefix = new int[4096];
        byte[] suffix = new byte[4096];
        byte[] stack = new byte[4097];
        int[] output = new int[expectedPixels];
        BitReader reader = new BitReader(compressed);
        int oldCode = -1;
        int first = 0;
        int outCount = 0;
        int i;
        for (i = 0; i < clearCode; i++) {
            prefix[i] = 0;
            suffix[i] = (byte)i;
        }
        while (outCount < expectedPixels) {
            int code = reader.readBits(codeSize);
            int inCode;
            int stackTop = 0;
            if (code < 0) {
                break;
            }
            if (code == clearCode) {
                codeSize = minCodeSize + 1;
                codeMask = (1 << codeSize) - 1;
                nextCode = endCode + 1;
                oldCode = -1;
                continue;
            }
            if (code == endCode) {
                break;
            }
            if (oldCode == -1) {
                output[outCount++] = code;
                first = code;
                oldCode = code;
                continue;
            }
            inCode = code;
            if (code >= nextCode) {
                stack[stackTop++] = (byte)first;
                code = oldCode;
            }
            while (code >= clearCode) {
                if (code >= 4096) {
                    throw new IOException("bad GIF LZW code");
                }
                stack[stackTop++] = suffix[code];
                code = prefix[code];
            }
            first = suffix[code] & 0xFF;
            stack[stackTop++] = (byte)first;
            while (stackTop > 0 && outCount < expectedPixels) {
                output[outCount++] = stack[--stackTop] & 0xFF;
            }
            if (nextCode < 4096) {
                prefix[nextCode] = oldCode;
                suffix[nextCode] = (byte)first;
                nextCode++;
                if (nextCode > codeMask && codeSize < 12) {
                    codeSize++;
                    codeMask = (1 << codeSize) - 1;
                }
            }
            oldCode = inCode;
        }
        while (outCount < expectedPixels) {
            output[outCount++] = 0;
        }
        return output;
    }

    private static final class BitReader {
        private final byte[] data;
        private int bitPos;

        BitReader(byte[] bytes) {
            data = bytes == null ? new byte[0] : bytes;
        }

        int readBits(int count) {
            int value = 0;
            int i;
            for (i = 0; i < count; i++) {
                int bytePos = bitPos >> 3;
                if (bytePos >= data.length) {
                    return -1;
                }
                if ((data[bytePos] & (1 << (bitPos & 7))) != 0) {
                    value |= 1 << i;
                }
                bitPos++;
            }
            return value;
        }
    }
}
