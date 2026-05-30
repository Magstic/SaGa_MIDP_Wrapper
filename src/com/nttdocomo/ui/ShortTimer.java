package com.nttdocomo.ui;

public class ShortTimer implements Runnable {
    public static final int EVENT_TIMER = 1;

    private final Canvas canvas;
    private final int type;
    private final int interval;
    private final boolean repeat;
    private volatile boolean running;
    private Thread thread;

    private ShortTimer(Canvas canvas, int type, int interval, boolean repeat) {
        this.canvas = canvas;
        this.type = type;
        this.interval = interval <= 0 ? 1 : interval;
        this.repeat = repeat;
    }

    public static ShortTimer getShortTimer(Canvas canvas, int type, int interval, boolean repeat) {
        return new ShortTimer(canvas, type, interval, repeat);
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
    }

    public synchronized void dispose() {
        running = false;
        thread = null;
    }

    public void run() {
        do {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
            }
            if (running && canvas != null) {
                canvas.processEvent(Display.TIMER_EXPIRED_EVENT, type);
            }
        } while (running && repeat);
        running = false;
    }
}
