package com.nttdocomo.ui;

import java.io.IOException;

final class SoftKeys {
    private static final String RESOURCE = "/softkey_bg.png";
    private static javax.microedition.lcdui.Image bg;
    private static boolean attempted;
    private static final int DEFAULT_W = 44;
    private static final int DEFAULT_H = 20;
    private static final int PAD_X = 5;

    private SoftKeys() {}

    static void paint(javax.microedition.lcdui.Graphics g, Frame frame, int screenW, int screenH) {
        if (g == null || frame == null || !frame.isSoftLabelVisible()) return;
        String left = frame.getSoftLabel(Frame.SOFT_KEY_1);
        String right = frame.getSoftLabel(Frame.SOFT_KEY_2);
        boolean hasLeft = left != null && left.length() > 0;
        boolean hasRight = right != null && right.length() > 0;
        if (!hasLeft && !hasRight) return;
        ensureBg();
        if (hasLeft) drawLabel(g, left, 0, screenH, false);
        if (hasRight) drawLabel(g, right, screenW, screenH, true);
    }

    private static void drawLabel(javax.microedition.lcdui.Graphics g, String text, int edgeX, int screenH, boolean rightAligned) {
        int bgW = bg == null ? DEFAULT_W : bg.getWidth();
        int bgH = bg == null ? DEFAULT_H : bg.getHeight();
        int textW = BitmapFont.stringWidth(text);
        int boxW = textW + PAD_X * 2;
        if (boxW < bgW) boxW = bgW;
        int x = rightAligned ? edgeX - boxW : edgeX;
        int y = screenH - bgH;
        if (y < 0) y = 0;
        drawBackground(g, x, y, boxW, bgH);
        int tx = x + ((boxW - textW) >> 1);
        int baseline = y + ((bgH - BitmapFont.getHeight()) >> 1) + BitmapFont.getAscent();
        BitmapFont.drawRaw(g, text, tx, baseline, 0xFFFFFFFF);
    }

    private static void drawBackground(javax.microedition.lcdui.Graphics g, int x, int y, int w, int h) {
        if (bg == null) {
            g.setColor(0x173a8b);
            g.fillRect(x, y, w, h);
            g.setColor(0xffffff);
            g.drawRect(x, y, w - 1, h - 1);
            return;
        }
        int bw = bg.getWidth();
        int bh = bg.getHeight();
        if (w <= bw) {
            g.drawImage(bg, x, y, javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
            return;
        }
        int cap = bw >> 1;
        if (cap < 1) cap = 1;
        if (cap > w >> 1) cap = w >> 1;
        g.drawRegion(bg, 0, 0, cap, bh, 0, x, y, javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
        int mx = x + cap;
        int mw = w - cap * 2;
        int sx = bw >> 1;
        while (mw > 0) {
            int cw = mw > 1 ? 1 : mw;
            g.drawRegion(bg, sx, 0, cw, bh, 0, mx, y, javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
            mx += cw;
            mw -= cw;
        }
        g.drawRegion(bg, bw - cap, 0, cap, bh, 0, x + w - cap, y, javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
    }

    private static void ensureBg() {
        if (attempted) return;
        attempted = true;
        try {
            bg = javax.microedition.lcdui.Image.createImage(RESOURCE);
        } catch (IOException e) {
            bg = null;
        } catch (Throwable t) {
            bg = null;
        }
    }
}
