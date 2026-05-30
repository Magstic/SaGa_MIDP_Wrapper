package com.nttdocomo.ui;

final class SoundRes implements MediaSound {
    private final int slot;
    private final String resourcePath;
    private final String contentType;
    private final boolean loopDefault;
    private final int durationMillis;

    SoundRes(int slot, String resourcePath, String contentType, boolean loopDefault, int durationMillis) {
        this.slot = slot;
        this.resourcePath = resourcePath;
        this.contentType = contentType;
        this.loopDefault = loopDefault;
        this.durationMillis = durationMillis;
    }

    public void use() {
        // The data is already converted and packaged in the MIDlet JAR.
        // Defer actual stream creation to AudioPresenter.play(), so unused sounds
        // do not consume heap on small CLDC runtimes.
    }

    int getSlot() {
        return slot;
    }

    String getResourcePath() {
        return resourcePath;
    }

    String getContentType() {
        return contentType;
    }

    boolean isLoopDefault() {
        return loopDefault;
    }

    boolean isMidi() {
        return "audio/midi".equals(contentType) || "audio/sp-midi".equals(contentType) || "audio/mid".equals(contentType);
    }

    boolean isModalCue() {
        return slot == 20;
    }

    int getDurationMillis() {
        return durationMillis;
    }
}
