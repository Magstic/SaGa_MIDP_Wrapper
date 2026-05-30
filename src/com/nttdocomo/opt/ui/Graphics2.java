package com.nttdocomo.opt.ui;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;

public class Graphics2 extends Graphics {
    public static final int RENDER_SPEED = 0;
    public static final int RENDER_QUALITY = 1;

    public Graphics2() {
        super();
    }

    public void setRenderMode(int mode, int alpha, int option) {
        setRenderModeState(mode, alpha, option);
    }

    public Image getImage(int x, int y, int w, int h) {
        if (backBuffer == null) return null;
        int[] rgb = new int[w * h];
        backBuffer.getRGB(rgb, 0, w, x, y, w, h);
        javax.microedition.lcdui.Image captured = javax.microedition.lcdui.Image.createRGBImage(rgb, w, h, true);
        return new Image(captured);
    }
}
