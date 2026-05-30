package com.nttdocomo.ui;

public class Display {
    public static final int KEY_0 = 0;
    public static final int KEY_1 = 1;
    public static final int KEY_2 = 2;
    public static final int KEY_3 = 3;
    public static final int KEY_4 = 4;
    public static final int KEY_5 = 5;
    public static final int KEY_6 = 6;
    public static final int KEY_7 = 7;
    public static final int KEY_8 = 8;
    public static final int KEY_9 = 9;
    public static final int KEY_ASTERISK = 10;
    public static final int KEY_POUND = 11;
    public static final int KEY_LEFT = 16;
    public static final int KEY_UP = 17;
    public static final int KEY_RIGHT = 18;
    public static final int KEY_DOWN = 19;
    public static final int KEY_SELECT = 20;
    public static final int KEY_SOFT1 = 21;
    public static final int KEY_SOFT2 = 22;

    public static final int KEY_PRESSED_EVENT = 0;
    public static final int KEY_RELEASED_EVENT = 1;
    public static final int TIMER_EXPIRED_EVENT = 7;
    public static final int MEDIA_EVENT = 8;

    public static void setCurrent(Frame frame) {
        javax.microedition.midlet.MIDlet midlet = getMidlet();
        if (midlet != null) {
            javax.microedition.lcdui.Display.getDisplay(midlet).setCurrent(frame);
        }
    }

    public static Frame getCurrent() {
        javax.microedition.midlet.MIDlet midlet = getMidlet();
        if (midlet == null) {
            return null;
        }
        javax.microedition.lcdui.Displayable current = javax.microedition.lcdui.Display.getDisplay(midlet).getCurrent();
        if (current instanceof Frame) {
            return (Frame)current;
        }
        return null;
    }

    public static int getWidth() {
        Frame current = getCurrent();
        return current == null ? 240 : current.getWidth();
    }

    public static int getHeight() {
        Frame current = getCurrent();
        return current == null ? 240 : current.getHeight();
    }

    public static boolean isColor() {
        return true;
    }

    public static int numColors() {
        return 65536;
    }

    private static javax.microedition.midlet.MIDlet getMidlet() {
        return DisplayMidletHolder.midlet;
    }

    static void setMidlet(javax.microedition.midlet.MIDlet midlet) {
        DisplayMidletHolder.midlet = midlet;
    }

    private static final class DisplayMidletHolder {
        static javax.microedition.midlet.MIDlet midlet;
    }
}
