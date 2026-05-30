package com.nttdocomo.ui;

import com.nttdocomo.opt.ui.Graphics2;

public abstract class Canvas extends Frame implements Runnable {
    public static final int KEY_LEFT = 16;
    public static final int KEY_UP = 17;
    public static final int KEY_RIGHT = 18;
    public static final int KEY_DOWN = 19;
    public static final int KEY_SELECT = 20;
    public static final int KEY_SOFT1 = 21;
    public static final int KEY_SOFT2 = 22;

    private Graphics2 dojaGraphics;
    private volatile int keyState;

    protected Canvas() {
        setFullScreenMode(true);
    }

    public Graphics getGraphics() {
        if (dojaGraphics == null) {
            dojaGraphics = new Graphics2();
            dojaGraphics.parentCanvas = this;
        }
        dojaGraphics.lock();
        return dojaGraphics;
    }

    public int getKeypadState() {
        return keyState;
    }

    public int getKeypadState(int group) {
        return keyState;
    }

    public void processEvent(int type, int param) {
    }

    public void processIMEEvent(int type, String text) {
    }

    public void imeOn(String text, int displayMode, int inputMode) {
        processIMEEvent(0, text == null ? "" : text);
    }

    public void imeOn(String text, int displayMode, int inputMode, int inputSize) {
        imeOn(text, displayMode, inputMode);
    }

    public void paint(Graphics g) {
    }

    public void run() {
    }

    protected void paint(javax.microedition.lcdui.Graphics g) {
        if (dojaGraphics == null) {
            getGraphics();
            paint(dojaGraphics);
            dojaGraphics.unlock(false);
        }
        if (dojaGraphics != null && dojaGraphics.getDisplayBuffer() != null) {
            g.drawImage(dojaGraphics.getDisplayBuffer(), 0, 0, javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
        }
        SoftKeys.paint(g, this, getWidth(), getHeight());
    }

    protected void keyPressed(int keyCode) {
        int mask = mapKey(keyCode);
        keyState |= mask;
        if (mask != 0) {
            processEvent(Display.KEY_PRESSED_EVENT, maskToKeyParam(mask));
        }
    }

    protected void keyReleased(int keyCode) {
        int mask = mapKey(keyCode);
        keyState &= ~mask;
        if (mask != 0) {
            processEvent(Display.KEY_RELEASED_EVENT, maskToKeyParam(mask));
        }
    }

    private int maskToKeyParam(int mask) {
        int i;
        for (i = 0; i < 31; i++) {
            if (mask == (1 << i)) {
                return i;
            }
        }
        return 0;
    }

    private int mapKey(int keyCode) {
        int action = 0;
        try { action = getGameAction(keyCode); } catch (Exception e) {}
        switch (action) {
            case UP:    return 1 << Display.KEY_UP;
            case DOWN:  return 1 << Display.KEY_DOWN;
            case LEFT:  return 1 << Display.KEY_LEFT;
            case RIGHT: return 1 << Display.KEY_RIGHT;
            case FIRE:  return 1 << Display.KEY_SELECT;
        }
        if (keyCode == -6 || keyCode == -21) return 1 << Display.KEY_SOFT1;
        if (keyCode == -7 || keyCode == -22) return 1 << Display.KEY_SOFT2;
        if (keyCode >= '0' && keyCode <= '9') return 1 << (keyCode - '0');
        if (keyCode == '*') return 1 << Display.KEY_ASTERISK;
        if (keyCode == '#') return 1 << Display.KEY_POUND;
        return 0;
    }
}
