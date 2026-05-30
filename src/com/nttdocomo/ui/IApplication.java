package com.nttdocomo.ui;

import javax.microedition.midlet.MIDlet;

public class IApplication {
    public static final int LAUNCHED_AS_APPLICATION = 0;
    public static final int LAUNCHED_AS_IA_APPLI = 7;
    public static final int LAUNCHED_FROM_BROWSER = 9;

    private static final String DEFAULT_SOURCE_URL = "http://semb.jp/saga/docomo/mega/SH905i/";

    private static IApplication instance;
    private static MIDlet midlet;
    private static String[] args = new String[0];
    private static int launchType = LAUNCHED_AS_APPLICATION;

    public static IApplication getCurrentApp() {
        return instance;
    }

    public static void setCurrentApp(IApplication app) {
        instance = app;
    }

    public static void setMidlet(MIDlet appMidlet) {
        midlet = appMidlet;
        Display.setMidlet(appMidlet);
    }

    public void start() {
    }

    public void terminate() {
        if (midlet != null) {
            midlet.notifyDestroyed();
        }
    }

    public int getLaunchType() {
        return launchType;
    }

    public String getParameter(String name) {
        if ("AppParam".equals(name) || "d".equals(name)) {
            return DEFAULT_SOURCE_URL;
        }
        return null;
    }

    public String getSourceURL() {
        return DEFAULT_SOURCE_URL;
    }

    public String[] getArgs() {
        return args;
    }

    public void launch(int type, String[] args) {
        if (args != null && args.length > 0 && midlet != null) {
            try {
                midlet.platformRequest(args[0]);
            } catch (Exception e) {
            }
        }
    }

    public static MIDlet getMIDlet() {
        return midlet;
    }
}
