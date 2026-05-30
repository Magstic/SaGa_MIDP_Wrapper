package com.nttdocomo.ui;

public class AudioPresenter extends MediaPresenter implements SoundPlayer.Listener {
    public static final int AUDIO_PLAYING = 1;
    public static final int AUDIO_STOPPED = 2;
    public static final int AUDIO_COMPLETE = 3;

    private static final int ATTR_KEY = 3;
    private static final int ATTR_VOLUME = 4;
    private static final int ATTR_TEMPO = 5;
    private static final int ATTR_LOOP_COUNT = 6;
    private static final int FALLBACK_FINISH_DELAY_MS = 300;
    private static final int FINITE_MARGIN_MS = 250;
    private static final FallbackScheduler FALLBACK_SCHEDULER = new FallbackScheduler();

    private final SoundPlayer player = new SoundPlayer();
    private MediaSound sound;
    private boolean playing;
    private int volume = 100;
    private int tempo = 100;
    private int key = 5;
    private int loopCount = 0;
    private int playGeneration;
    private boolean musicEnabled = true;
    private boolean sfxEnabled = true;
    private boolean physicalSuppressed;
    private long suppressedStartedAtMillis;
    private int suppressedStartMillis;

    public static AudioPresenter getAudioPresenter() {
        return new AudioPresenter();
    }

    public static AudioPresenter getAudioPresenter(int type) {
        return new AudioPresenter();
    }

    public void setSound(MediaSound sound) {
        stop();
        this.sound = sound;
        if (sound != null) {
            try {
                sound.use();
            } catch (Throwable ignored) {
            }
        }
    }

    public void setAttribute(int attr, int value) {
        super.setAttribute(attr, value);
        switch (attr) {
            case ATTR_KEY:
                key = value;
                player.setPitchSemitones(value - 5);
                break;
            case ATTR_VOLUME:
                volume = value;
                player.setVolume(value);
                break;
            case ATTR_TEMPO:
                tempo = value;
                player.setRatePercent(value);
                break;
            case ATTR_LOOP_COUNT:
                loopCount = value;
                break;
            default:
                break;
        }
    }

    public void setOutputEnabled(boolean enabled) {
        setOutputPolicy(enabled, enabled);
    }

    public void setOutputPolicy(boolean musicEnabled, boolean sfxEnabled) {
        SoundRes converted = null;
        SoundRes suppressed = null;
        int startMillis = 0;
        int suppressedStartMillis = 0;
        int generation = 0;
        int suppressedGeneration = 0;
        int normalizedLoops = 0;
        int suppressedLoops = 0;
        boolean restart = false;
        boolean finishSuppressed = false;

        synchronized (this) {
            if (this.musicEnabled == musicEnabled && this.sfxEnabled == sfxEnabled) {
                return;
            }
            this.musicEnabled = musicEnabled;
            this.sfxEnabled = sfxEnabled;
            if (playing && sound instanceof SoundRes) {
                converted = (SoundRes) sound;
                if (isOutputAllowed(converted)) {
                    if (physicalSuppressed) {
                        startMillis = suppressedCurrentTimeMillis();
                        generation = playGeneration;
                        normalizedLoops = normalizedLoopCount(converted);
                        restart = true;
                    }
                } else if (!physicalSuppressed) {
                    physicalSuppressed = true;
                    startSuppressedClock(currentTimeForSuppression());
                    suppressed = converted;
                    suppressedStartMillis = this.suppressedStartMillis;
                    suppressedGeneration = playGeneration;
                    suppressedLoops = normalizedLoopCount(suppressed);
                    finishSuppressed = true;
                }
            } else if (!playing) {
                physicalSuppressed = false;
                clearSuppressedClock();
            }
        }

        if (finishSuppressed) {
            player.suspendForOutput();
            scheduleSuppressedCompletion(suppressed, suppressedLoops, suppressedStartMillis, suppressedGeneration);
        }
        if (restart) {
            startOutput(converted, normalizedLoops, startMillis, generation);
        }
    }

    public void play() {
        play(0);
    }

    public void play(int startMillis) {
        SoundRes converted;
        final int generation;
        int normalizedLoops;
        boolean startOutput;

        synchronized (this) {
            playing = true;
            generation = ++playGeneration;
        }

        if (!(sound instanceof SoundRes)) {
            scheduleFallbackFinish(generation, FALLBACK_FINISH_DELAY_MS, true);
            return;
        }

        converted = (SoundRes) sound;
        if (startMillis < 0) startMillis = 0;
        normalizedLoops = normalizedLoopCount(converted);
        synchronized (this) {
            startOutput = isOutputAllowed(converted);
            physicalSuppressed = !startOutput;
            if (physicalSuppressed) {
                startSuppressedClock(startMillis);
            } else {
                clearSuppressedClock();
            }
        }

        if (!startOutput) {
            scheduleSuppressedCompletion(converted, normalizedLoops, startMillis, generation);
            if (shouldNotifyPlaybackStarted(converted)) {
                notifyPlaybackStarted(generation);
            }
            return;
        }

        startOutput(converted, normalizedLoops, startMillis, generation);
    }

    private void notifyPlaybackStarted(int generation) {
        boolean notify;
        synchronized (this) {
            notify = playing && generation == playGeneration;
        }
        if (notify) {
            fireMediaAction(AUDIO_PLAYING, 0);
        }
    }

    private boolean shouldNotifyPlaybackStarted(SoundRes converted) {
        return converted != null && converted.isMidi() && converted.isLoopDefault();
    }

    public int getCurrentTime() {
        synchronized (this) {
            if (playing && physicalSuppressed) {
                return suppressedCurrentTimeMillis();
            }
        }
        return player.getCurrentTimeMillis();
    }

    public MediaResource getMediaResource() {
        return sound;
    }

    public void stop() {
        boolean notifyStopped;
        synchronized (this) {
            notifyStopped = playing && sound instanceof SoundRes && ((SoundRes) sound).isMidi();
            playing = false;
            physicalSuppressed = false;
            clearSuppressedClock();
            playGeneration++;
        }
        player.close();
        if (notifyStopped) {
            fireMediaAction(AUDIO_STOPPED, 0);
        }
    }

    public void onPlaybackCompleted() {
        boolean notify;
        synchronized (this) {
            notify = playing;
            if (notify) {
                playing = false;
                physicalSuppressed = false;
                clearSuppressedClock();
                playGeneration++;
            }
        }
        if (notify) {
            fireMediaAction(AUDIO_COMPLETE, 0);
        }
    }

    public void onPlaybackError(String message) {
        int generation;
        synchronized (this) {
            if (!playing) {
                return;
            }
            physicalSuppressed = true;
            startSuppressedClock(player.getCurrentTimeMillis());
            generation = ++playGeneration;
        }
        scheduleFallbackFinish(generation, FALLBACK_FINISH_DELAY_MS, true);
    }

    public void onPlaybackStopped() {
        boolean notify;
        synchronized (this) {
            notify = playing;
            if (notify) {
                playing = false;
                physicalSuppressed = false;
                clearSuppressedClock();
                playGeneration++;
            }
        }
        if (notify) {
            fireMediaAction(AUDIO_STOPPED, 0);
        }
    }

    private int normalizedLoopCount(SoundRes converted) {
        if (converted != null && converted.isLoopDefault()) {
            return -1;
        }
        if (loopCount < 0) {
            return -1;
        }
        return max(1, loopCount);
    }

    private boolean isOutputAllowed(SoundRes converted) {
        if (converted == null) {
            return musicEnabled || sfxEnabled;
        }
        return converted.isMidi() ? musicEnabled : sfxEnabled;
    }

    private void scheduleFiniteCompletion(SoundRes converted, int loops, int startMillis, int generation) {
        int duration;
        int delay;
        if (converted == null || loops < 0 || converted.getDurationMillis() <= 0) {
            return;
        }
        duration = converted.getDurationMillis();
        delay = duration - startMillis;
        if (delay < 1) delay = 1;
        scheduleFallbackFinish(generation, delay + FINITE_MARGIN_MS, true);
    }

    private void scheduleSuppressedCompletion(SoundRes converted, int loops, int startMillis, int generation) {
        if (converted == null || converted.isLoopDefault() || loops < 0) {
            return;
        }
        if (converted.getDurationMillis() > 0) {
            scheduleFiniteCompletion(converted, loops, startMillis, generation);
        } else {
            scheduleFallbackFinish(generation, FALLBACK_FINISH_DELAY_MS, true);
        }
    }

    private void scheduleFallbackFinish(final int generation, int delayMs, boolean closeOnComplete) {
        if (listener == null) {
            return;
        }
        if (delayMs < 1) delayMs = 1;
        FALLBACK_SCHEDULER.schedule(this, generation, delayMs, closeOnComplete);
    }

    private void completeFallback(int generation, boolean closeOnComplete) {
        boolean notify;
        synchronized (this) {
            notify = playing && generation == playGeneration;
            if (notify) {
                playing = false;
                physicalSuppressed = false;
                clearSuppressedClock();
                playGeneration++;
            }
        }
        if (notify) {
            if (closeOnComplete) {
                player.close();
            }
            fireMediaAction(AUDIO_COMPLETE, 0);
        }
    }

    private void applyLiveAttributes() {
        player.setVolume(volume);
        player.setRatePercent(tempo);
        player.setPitchSemitones(key - 5);
    }

    private void startOutput(SoundRes converted, int normalizedLoops, int startMillis, int generation) {
        boolean started;
        boolean closeStarted = false;
        try {
            started = player.play(converted, normalizedLoops, startMillis, this);
            synchronized (this) {
                if (!playing || generation != playGeneration || !isOutputAllowed(converted)) {
                    closeStarted = true;
                } else if (started) {
                    physicalSuppressed = false;
                    clearSuppressedClock();
                } else {
                    physicalSuppressed = true;
                    startSuppressedClock(startMillis);
                }
            }
            if (closeStarted) {
                player.close();
                return;
            }
            applyLiveAttributes();
            scheduleFiniteCompletion(converted, normalizedLoops, startMillis, generation);
        } catch (Throwable ignored) {
            synchronized (this) {
                if (playing && generation == playGeneration) {
                    physicalSuppressed = true;
                    startSuppressedClock(startMillis);
                }
            }
            scheduleFallbackFinish(generation, FALLBACK_FINISH_DELAY_MS, true);
            return;
        }
        if (shouldNotifyPlaybackStarted(converted)) {
            notifyPlaybackStarted(generation);
        }
    }

    private int currentTimeForSuppression() {
        int playerTime = player.getCurrentTimeMillis();
        int suppressedTime = suppressedCurrentTimeMillis();
        if (physicalSuppressed && suppressedTime > playerTime) {
            return suppressedTime;
        }
        return playerTime;
    }

    private void startSuppressedClock(int startMillis) {
        if (startMillis < 0) startMillis = 0;
        suppressedStartMillis = startMillis;
        suppressedStartedAtMillis = System.currentTimeMillis();
    }

    private void clearSuppressedClock() {
        suppressedStartedAtMillis = 0L;
        suppressedStartMillis = 0;
    }

    private int suppressedCurrentTimeMillis() {
        if (suppressedStartedAtMillis == 0L) {
            return suppressedStartMillis;
        }
        return suppressedStartMillis + (int) (System.currentTimeMillis() - suppressedStartedAtMillis);
    }

    private static int max(int left, int right) {
        return left > right ? left : right;
    }

    private static final class FallbackScheduler implements Runnable {
        private AudioPresenter[] presenters = new AudioPresenter[8];
        private int[] generations = new int[8];
        private long[] dueTimes = new long[8];
        private boolean[] closeFlags = new boolean[8];
        private int count;
        private boolean started;

        synchronized void schedule(AudioPresenter presenter, int generation, int delayMs, boolean closeOnComplete) {
            int i;
            long due = System.currentTimeMillis() + delayMs;
            if (presenter == null) return;
            for (i = 0; i < count; i++) {
                if (presenters[i] == presenter) {
                    generations[i] = generation;
                    dueTimes[i] = due;
                    closeFlags[i] = closeOnComplete;
                    notify();
                    startThread();
                    return;
                }
            }
            ensureCapacity(count + 1);
            presenters[count] = presenter;
            generations[count] = generation;
            dueTimes[count] = due;
            closeFlags[count] = closeOnComplete;
            count++;
            startThread();
            notify();
        }

        private void startThread() {
            if (!started) {
                started = true;
                new Thread(this).start();
            }
        }

        public void run() {
            while (true) {
                AudioPresenter presenter = null;
                int generation = 0;
                boolean close = false;
                synchronized (this) {
                    while (count == 0) {
                        try { wait(); } catch (InterruptedException ignored) {}
                    }
                    int idx = earliestIndex();
                    long now = System.currentTimeMillis();
                    long delay = dueTimes[idx] - now;
                    if (delay > 0) {
                        try { wait(delay); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    presenter = presenters[idx];
                    generation = generations[idx];
                    close = closeFlags[idx];
                    removeAt(idx);
                }
                if (presenter != null) {
                    presenter.completeFallback(generation, close);
                }
            }
        }

        private int earliestIndex() {
            int best = 0;
            int i;
            for (i = 1; i < count; i++) {
                if (dueTimes[i] < dueTimes[best]) best = i;
            }
            return best;
        }

        private void removeAt(int idx) {
            count--;
            presenters[idx] = presenters[count];
            generations[idx] = generations[count];
            dueTimes[idx] = dueTimes[count];
            closeFlags[idx] = closeFlags[count];
            presenters[count] = null;
        }

        private void ensureCapacity(int needed) {
            if (needed <= presenters.length) return;
            int n = presenters.length * 2;
            AudioPresenter[] p = new AudioPresenter[n];
            int[] g = new int[n];
            long[] d = new long[n];
            boolean[] c = new boolean[n];
            System.arraycopy(presenters, 0, p, 0, presenters.length);
            System.arraycopy(generations, 0, g, 0, generations.length);
            System.arraycopy(dueTimes, 0, d, 0, dueTimes.length);
            System.arraycopy(closeFlags, 0, c, 0, closeFlags.length);
            presenters = p;
            generations = g;
            dueTimes = d;
            closeFlags = c;
        }
    }
}
