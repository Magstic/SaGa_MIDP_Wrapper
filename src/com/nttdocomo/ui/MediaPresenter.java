package com.nttdocomo.ui;

public class MediaPresenter {
    protected MediaListener listener;

    public void setAttribute(int attr, int value) {}

    public void setMediaListener(MediaListener listener) {
        this.listener = listener;
    }

    protected void fireMediaAction(int type, int option) {
        MediaListener current = listener;
        if (current != null) {
            current.mediaAction(this, type, option);
        }
    }
}

