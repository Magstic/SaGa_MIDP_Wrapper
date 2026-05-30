package com.nttdocomo.ui;

public class Image {
    private javax.microedition.lcdui.Image midpImage;
    private javax.microedition.lcdui.Image originalImage;
    private int alpha = 255;
    private Graphics graphics;
    private int transparentColor;
    private boolean transparentEnabled;

    public Image(javax.microedition.lcdui.Image img) {
        this.midpImage = img;
        this.originalImage = img;
    }

    public static Image createImage(int width, int height) {
        return new Image(javax.microedition.lcdui.Image.createImage(width, height));
    }

    public static Image createImage(int width, int height, int[] data, int off) {
        int[] rgb = new int[width * height];
        if (data != null) {
            System.arraycopy(data, off, rgb, 0, rgb.length);
        }
        return new Image(javax.microedition.lcdui.Image.createRGBImage(rgb, width, height, true));
    }

    public static Image createImage(byte[] data) {
        return new Image(javax.microedition.lcdui.Image.createImage(data, 0, data.length));
    }

    public javax.microedition.lcdui.Image getMIDPImage() {
        return midpImage;
    }

    protected void setMIDPImage(javax.microedition.lcdui.Image img) {
        midpImage = img;
        originalImage = img;
        graphics = null;
    }

    public Graphics getGraphics() {
        if (graphics == null) {
            graphics = new Graphics();
            graphics.init(midpImage);
        }
        return graphics;
    }

    public void setTransparentColor(int color) {
        transparentColor = color;
        applyTransparency();
    }

    public int getTransparentColor() {
        return transparentColor;
    }

    public void setTransparentEnabled(boolean enabled) {
        transparentEnabled = enabled;
        applyTransparency();
    }

    public void setAlpha(int alpha) {
        this.alpha = (alpha < 0) ? 0 : (alpha > 255) ? 255 : alpha;
    }

    public int getAlpha() {
        return alpha;
    }

    public int getWidth() {
        return (midpImage != null) ? midpImage.getWidth() : 0;
    }

    public int getHeight() {
        return (midpImage != null) ? midpImage.getHeight() : 0;
    }

    public void dispose() {
        midpImage = null;
        originalImage = null;
        graphics = null;
    }

    private void applyTransparency() {
        if (originalImage == null) {
            return;
        }
        if (!transparentEnabled) {
            midpImage = originalImage;
            graphics = null;
            return;
        }
        int w = originalImage.getWidth();
        int h = originalImage.getHeight();
        int[] rgb = new int[w * h];
        int transparent = transparentColor & 0x00FFFFFF;
        int i;
        originalImage.getRGB(rgb, 0, w, 0, 0, w, h);
        for (i = 0; i < rgb.length; i++) {
            if ((rgb[i] & 0x00FFFFFF) == transparent) {
                rgb[i] = 0x00000000;
            }
        }
        midpImage = javax.microedition.lcdui.Image.createRGBImage(rgb, w, h, true);
        graphics = null;
    }
}
