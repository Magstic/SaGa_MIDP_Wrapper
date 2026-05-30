package com.nttdocomo.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;

/* Converted sound backend with a single MIDI transport.
 *
 * SaGa uses two DoJa AudioPresenters: channel 0 for the normal BGM and channel 1
 * for event music / effects.  On DoJa, starting the inn/rest cue (slot 020) does
 * not leave the previous BGM audible; it temporarily owns the MIDI transport and
 * the previous BGM is restarted by the game when the cue completes.  MIDP/MMAPI
 * allows several Player instances to drive the same sequencer at once, so simply
 * mapping every presenter to an independent Player makes two MIDIs overlap.
 *
 * This class therefore arbitrates MIDI at the converted-sound layer.  WAV SFX
 * still use a small slot pool and may overlap; MIDI is exclusive.  Slot 020 is a
 * modal MIDI cue: while it is active, non-modal MIDI play requests are deferred
 * rather than allowed to stop the cue. */
final class SoundPlayer implements PlayerListener {
    static interface Listener {
        void onPlaybackCompleted();
        void onPlaybackError(String message);
        void onPlaybackStopped();
    }

    private static final int MAX_SLOT = 81;
    private static final int SFX_POOL_SIZE = 2;
    private static final int MAX_MIDI_PLAYERS = 4;

    private static final SoundPool[] POOLS = new SoundPool[MAX_SLOT];
    private static final byte[][] BYTE_CACHE = new byte[MAX_SLOT][];
    private static final boolean[] DISABLED = new boolean[MAX_SLOT];

    private static final Object MIDI_LOCK = new Object();
    private static final SoundPlayer[] MIDI_PLAYERS = new SoundPlayer[MAX_MIDI_PLAYERS];
    private static int midiPlayerCount;
    private static SoundPlayer modalOwner;
    private static final SoundPlayer[] DEFERRED_PLAYERS = new SoundPlayer[MAX_MIDI_PLAYERS];
    private static int deferredPlayerCount;

    private PooledPlayer active;
    private SoundRes activeSound;
    private Listener listener;
    private long startedAtMillis;
    private int startOffsetMillis;

    private SoundRes deferredSound;
    private int deferredLoops;
    private int deferredStartMillis;
    private Listener deferredListener;

    synchronized boolean play(SoundRes sound, int loopCount, int startMillis, Listener callback)
            throws IOException, MediaException {
        if (sound == null) {
            throw new IOException("No converted sound");
        }
        if (startMillis < 0) startMillis = 0;
        clearDeferred();

        if (sound.isMidi()) {
            if (shouldDeferMidi(this, sound)) {
                setDeferred(sound, loopCount, startMillis, callback);
                return false;
            }
            if (sound.isModalCue()) {
                beginModal(this);
            } else {
                claimNormalMidi(this);
            }
        }

        startNow(sound, loopCount, startMillis, callback);

        if (sound.isMidi() && !sound.isModalCue()) {
            registerMidi(this);
        }
        return true;
    }

    synchronized int getCurrentTimeMillis() {
        PooledPlayer current = active;
        long mediaTime;
        int mediaMillis = -1;
        int wallMillis;
        if (current != null && current.player != null) {
            try {
                mediaTime = current.player.getMediaTime();
                if (mediaTime >= 0L) {
                    mediaMillis = (int) (mediaTime / 1000L);
                }
            } catch (Throwable ignored) {
            }
        }
        if (startedAtMillis == 0L) {
            return mediaMillis >= 0 ? mediaMillis : startOffsetMillis;
        }
        wallMillis = startOffsetMillis + (int) (System.currentTimeMillis() - startedAtMillis);
        /* Some MMAPI MIDI implementations report a frozen media time while the
         * sequencer is actually playing.  SaGa polls getCurrentTime() and treats
         * a frozen value as a stalled presenter, so use the larger monotonic value. */
        if (mediaMillis > wallMillis) {
            return mediaMillis;
        }
        return wallMillis;
    }

    synchronized void close() {
        clearDeferred();
        closeActive(false);
    }

    synchronized void suspendForOutput() {
        SoundRes closed;
        clearDeferred();
        closed = closeActive(true);
        if (closed != null && closed.isModalCue()) {
            startDeferredMidiAfterModal();
        }
    }

    synchronized void setVolume(int level) {
        VolumeControl control = volumeControl();
        if (control != null) {
            try {
                control.setLevel(clamp(0, 100, level));
            } catch (Throwable ignored) {
            }
        }
    }

    synchronized void setRatePercent(int percent) {
        /* Optional MMAPI controls are intentionally not referenced here; some real
         * CLDC VMs fail verification if optional control classes are absent. */
    }

    synchronized void setPitchSemitones(int semitones) {
        /* Optional PitchControl is intentionally unsupported in the light path. */
    }

    public void playerUpdate(Player source, String event, Object eventData) {
        if (event == null) {
            return;
        }
        if (PlayerListener.END_OF_MEDIA.equals(event)) {
            handleEndOfMedia(source);
        } else if (PlayerListener.ERROR.equals(event)) {
            handleError(source, eventData);
        }
    }

    private void startNow(SoundRes sound, int loopCount, int startMillis, Listener callback)
            throws IOException, MediaException {
        PooledPlayer next;
        int loops;

        closeActive(false);
        if (isDisabled(sound.getSlot())) {
            throw new MediaException("disabled converted sound slot " + sound.getSlot());
        }

        loops = loopCount < 0 ? -1 : max(1, loopCount);
        next = acquire(sound);
        try {
            next.bind(this);
            next.prepare(loops, startMillis);
            active = next;
            activeSound = sound;
            listener = callback;
            startOffsetMillis = startMillis;
            startedAtMillis = System.currentTimeMillis();
            next.player.start();
        } catch (MediaException e) {
            releaseBroken(next);
            clearActiveFields();
            throw e;
        } catch (Throwable t) {
            releaseBroken(next);
            clearActiveFields();
            throw new MediaException(t.toString());
        }
    }

    private void handleEndOfMedia(Player source) {
        Listener callback;
        PooledPlayer ended;
        SoundRes endedSound;
        synchronized (this) {
            if (active == null || source == null || source != active.player) {
                return;
            }
            ended = active;
            endedSound = activeSound;
            callback = listener;
            clearActiveFields();
        }
        ended.unbind(this);
        if (endedSound != null && endedSound.isMidi()) {
            unregisterMidi(this);
            if (endedSound.isModalCue()) {
                endModal(this);
            }
        }
        release(ended);
        if (callback != null) {
            callback.onPlaybackCompleted();
        }
        if (endedSound != null && endedSound.isModalCue()) {
            startDeferredMidiAfterModal();
        }
    }

    private void handleError(Player source, Object eventData) {
        Listener callback;
        PooledPlayer failed;
        SoundRes failedSound;
        String message;
        synchronized (this) {
            if (active == null || source == null || source != active.player) {
                return;
            }
            failed = active;
            failedSound = activeSound;
            callback = listener;
            message = eventData == null ? "converted sound unsupported" : eventData.toString();
            clearActiveFields();
        }
        failed.unbind(this);
        if (failedSound != null && failedSound.isMidi()) {
            unregisterMidi(this);
            if (failedSound.isModalCue()) {
                endModal(this);
            }
        }
        releaseBroken(failed);
        if (callback != null) {
            callback.onPlaybackError(message);
        }
        if (failedSound != null && failedSound.isModalCue()) {
            startDeferredMidiAfterModal();
        }
    }

    private SoundRes closeActive(boolean keepDeferred) {
        PooledPlayer current;
        SoundRes currentSound;
        synchronized (this) {
            current = active;
            currentSound = activeSound;
            clearActiveFields();
            if (!keepDeferred) {
                clearDeferred();
            }
        }
        if (current != null) {
            current.unbind(this);
            if (currentSound != null && currentSound.isMidi()) {
                unregisterMidi(this);
                if (currentSound.isModalCue()) {
                    endModal(this);
                }
            }
            release(current);
        }
        return currentSound;
    }

    private void clearActiveFields() {
        active = null;
        activeSound = null;
        listener = null;
        startedAtMillis = 0L;
        startOffsetMillis = 0;
    }

    private void setDeferred(SoundRes sound, int loops, int startMillis, Listener callback) {
        deferredSound = sound;
        deferredLoops = loops;
        deferredStartMillis = startMillis;
        deferredListener = callback;
        registerDeferredPlayer(this);
    }

    private void clearDeferred() {
        deferredSound = null;
        deferredLoops = 0;
        deferredStartMillis = 0;
        deferredListener = null;
        unregisterDeferredPlayer(this);
    }

    private boolean hasDeferredMidi() {
        return deferredSound != null && deferredSound.isMidi();
    }

    private void startDeferredIfPresent() {
        SoundRes sound;
        int loops;
        int start;
        Listener callback;
        synchronized (this) {
            if (!hasDeferredMidi()) return;
            sound = deferredSound;
            loops = deferredLoops;
            start = deferredStartMillis;
            callback = deferredListener;
            clearDeferred();
        }
        try {
            play(sound, loops, start, callback);
        } catch (Throwable ignored) {
            if (callback != null) callback.onPlaybackError("deferred MIDI failed");
        }
    }

    private void suspendMidiForModal(boolean notifyStopped) {
        PooledPlayer current;
        SoundRes currentSound;
        Listener callback;
        synchronized (this) {
            current = active;
            currentSound = activeSound;
            if (current == null || currentSound == null || !currentSound.isMidi()) {
                return;
            }
            callback = listener;
            clearActiveFields();
        }
        current.unbind(this);
        unregisterMidi(this);
        release(current);
        if (notifyStopped && callback != null) {
            callback.onPlaybackStopped();
        }
    }

    private VolumeControl volumeControl() {
        PooledPlayer current = active;
        if (current == null || current.player == null) {
            return null;
        }
        try {
            return (VolumeControl) current.player.getControl("VolumeControl");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean shouldDeferMidi(SoundPlayer owner, SoundRes sound) {
        if (owner == null || sound == null || !sound.isMidi() || sound.isModalCue()) return false;
        synchronized (MIDI_LOCK) {
            return modalOwner != null && modalOwner != owner;
        }
    }

    private static void beginModal(SoundPlayer owner) {
        SoundPlayer[] victims;
        int count;
        int i;
        synchronized (MIDI_LOCK) {
            modalOwner = owner;
            victims = new SoundPlayer[midiPlayerCount];
            count = 0;
            for (i = 0; i < midiPlayerCount; i++) {
                if (MIDI_PLAYERS[i] != null && MIDI_PLAYERS[i] != owner) {
                    victims[count++] = MIDI_PLAYERS[i];
                }
            }
        }
        for (i = 0; i < count; i++) {
            victims[i].suspendMidiForModal(true);
        }
    }

    private static void endModal(SoundPlayer owner) {
        synchronized (MIDI_LOCK) {
            if (modalOwner == owner) {
                modalOwner = null;
            }
        }
    }

    private static void claimNormalMidi(SoundPlayer owner) {
        SoundPlayer[] victims;
        int count;
        int i;
        synchronized (MIDI_LOCK) {
            victims = new SoundPlayer[midiPlayerCount];
            count = 0;
            for (i = 0; i < midiPlayerCount; i++) {
                if (MIDI_PLAYERS[i] != null && MIDI_PLAYERS[i] != owner) {
                    victims[count++] = MIDI_PLAYERS[i];
                }
            }
        }
        for (i = 0; i < count; i++) {
            victims[i].suspendMidiForModal(false);
        }
    }

    private static void registerMidi(SoundPlayer owner) {
        int i;
        if (owner == null) return;
        synchronized (MIDI_LOCK) {
            for (i = 0; i < midiPlayerCount; i++) {
                if (MIDI_PLAYERS[i] == owner) return;
            }
            if (midiPlayerCount < MIDI_PLAYERS.length) {
                MIDI_PLAYERS[midiPlayerCount++] = owner;
            }
        }
    }

    private static void unregisterMidi(SoundPlayer owner) {
        int i;
        int j;
        if (owner == null) return;
        synchronized (MIDI_LOCK) {
            for (i = 0; i < midiPlayerCount; i++) {
                if (MIDI_PLAYERS[i] == owner) {
                    for (j = i + 1; j < midiPlayerCount; j++) {
                        MIDI_PLAYERS[j - 1] = MIDI_PLAYERS[j];
                    }
                    MIDI_PLAYERS[--midiPlayerCount] = null;
                    break;
                }
            }
        }
    }

    private static void startDeferredMidiAfterModal() {
        SoundPlayer[] owners;
        int count;
        int i;
        synchronized (MIDI_LOCK) {
            if (modalOwner != null) return;
            owners = new SoundPlayer[MAX_MIDI_PLAYERS];
            count = deferredPlayerCount;
            for (i = 0; i < count && i < DEFERRED_PLAYERS.length; i++) {
                owners[i] = DEFERRED_PLAYERS[i];
            }
        }
        for (i = 0; i < count; i++) {
            if (owners[i] != null) owners[i].startDeferredIfPresent();
        }
    }

    private static void registerDeferredPlayer(SoundPlayer owner) {
        int i;
        if (owner == null) return;
        synchronized (MIDI_LOCK) {
            for (i = 0; i < deferredPlayerCount; i++) {
                if (DEFERRED_PLAYERS[i] == owner) return;
            }
            if (deferredPlayerCount < DEFERRED_PLAYERS.length) {
                DEFERRED_PLAYERS[deferredPlayerCount++] = owner;
            }
        }
    }

    private static void unregisterDeferredPlayer(SoundPlayer owner) {
        int i;
        int j;
        if (owner == null) return;
        synchronized (MIDI_LOCK) {
            for (i = 0; i < deferredPlayerCount; i++) {
                if (DEFERRED_PLAYERS[i] == owner) {
                    for (j = i + 1; j < deferredPlayerCount; j++) {
                        DEFERRED_PLAYERS[j - 1] = DEFERRED_PLAYERS[j];
                    }
                    DEFERRED_PLAYERS[--deferredPlayerCount] = null;
                    break;
                }
            }
        }
    }

    private static synchronized boolean isDisabled(int slot) {
        return slot >= 0 && slot < DISABLED.length && DISABLED[slot];
    }

    private static synchronized void disableSlot(int slot) {
        if (slot >= 0 && slot < DISABLED.length) {
            DISABLED[slot] = true;
        }
    }

    private static PooledPlayer acquire(SoundRes sound) throws IOException, MediaException {
        int slot = sound.getSlot();
        SoundPool pool;
        if (slot < 0 || slot >= MAX_SLOT) {
            return createPooled(sound, false, null);
        }
        if (sound.isMidi() || sound.isLoopDefault()) {
            /* Do not retain MIDI players after stop.  Several real phones keep
             * native sequencer state per Player and can leak notes/resources. */
            return createPooled(sound, false, null);
        }
        synchronized (SoundPlayer.class) {
            pool = POOLS[slot];
            if (pool == null) {
                pool = new SoundPool(SFX_POOL_SIZE);
                POOLS[slot] = pool;
            }
        }
        synchronized (pool) {
            PooledPlayer free = pool.freePlayer();
            if (free != null) {
                free.inUse = true;
                return free;
            }
            if (pool.count < pool.players.length) {
                PooledPlayer created = createPooled(sound, true, pool);
                created.inUse = true;
                pool.players[pool.count++] = created;
                return created;
            }
        }
        return createPooled(sound, false, null);
    }

    private static PooledPlayer createPooled(SoundRes sound, boolean cached, SoundPool owner)
            throws IOException, MediaException {
        byte[] bytes;
        CreatedPlayer created = null;
        try {
            bytes = loadResourceCached(sound);
            created = createPlayer(bytes, sound.getContentType());
            try { created.player.realize(); } catch (Throwable ignored) {}
            try { created.player.prefetch(); } catch (MediaException e) { throw e; } catch (Throwable ignored) {}
            return new PooledPlayer(sound.getSlot(), created.player, created.stream, cached, owner);
        } catch (IOException e) {
            disableSlot(sound.getSlot());
            closeCreatedPlayer(created);
            throw e;
        } catch (MediaException e) {
            disableSlot(sound.getSlot());
            closeCreatedPlayer(created);
            throw e;
        } catch (Throwable t) {
            disableSlot(sound.getSlot());
            closeCreatedPlayer(created);
            throw new MediaException(t.toString());
        }
    }

    private static void release(PooledPlayer player) {
        if (player == null) return;
        if (!player.cached || player.owner == null) {
            player.close();
            return;
        }
        try { player.player.stop(); } catch (Throwable ignored) {}
        try { player.player.setMediaTime(0L); } catch (Throwable ignored) {}
        synchronized (player.owner) {
            player.inUse = false;
        }
    }

    private static void releaseBroken(PooledPlayer player) {
        if (player == null) return;
        player.close();
        if (player.owner != null) {
            synchronized (player.owner) {
                int i;
                int j;
                for (i = 0; i < player.owner.count; i++) {
                    if (player.owner.players[i] == player) {
                        for (j = i + 1; j < player.owner.count; j++) {
                            player.owner.players[j - 1] = player.owner.players[j];
                        }
                        player.owner.players[--player.owner.count] = null;
                        break;
                    }
                }
            }
        }
    }

    private static byte[] loadResourceCached(SoundRes sound) throws IOException {
        int slot = sound.getSlot();
        byte[] bytes = null;
        if (slot >= 0 && slot < BYTE_CACHE.length) {
            synchronized (SoundPlayer.class) {
                bytes = BYTE_CACHE[slot];
            }
            if (bytes != null) {
                return bytes;
            }
        }
        bytes = loadResource(sound.getResourcePath());
        if (slot >= 0 && slot < BYTE_CACHE.length) {
            synchronized (SoundPlayer.class) {
                if (BYTE_CACHE[slot] == null) {
                    BYTE_CACHE[slot] = bytes;
                } else {
                    bytes = BYTE_CACHE[slot];
                }
            }
        }
        return bytes;
    }

    private static CreatedPlayer createPlayer(byte[] bytes, String preferredType) throws IOException, MediaException {
        String[] candidates = contentTypeCandidates(preferredType);
        IOException ioError = null;
        MediaException mediaError = null;
        int i;
        for (i = 0; i < candidates.length; i++) {
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            try {
                return new CreatedPlayer(Manager.createPlayer(stream, candidates[i]), stream);
            } catch (IOException e) {
                ioError = e;
                closeQuietly(stream);
            } catch (MediaException e) {
                mediaError = e;
                closeQuietly(stream);
            } catch (Throwable t) {
                closeQuietly(stream);
            }
        }
        if (mediaError != null) throw mediaError;
        if (ioError != null) throw ioError;
        throw new MediaException("No supported content type");
    }

    private static String[] contentTypeCandidates(String preferredType) {
        if ("audio/midi".equals(preferredType)) {
            return new String[] { "audio/midi", "audio/sp-midi", "audio/mid" };
        }
        if ("audio/x-wav".equals(preferredType) || "audio/wav".equals(preferredType)) {
            return new String[] { "audio/x-wav", "audio/wav", "audio/vnd.wave" };
        }
        return new String[] { preferredType };
    }

    private static byte[] loadResource(String path) throws IOException {
        InputStream input = null;
        try {
            input = SoundPlayer.class.getResourceAsStream(path);
            if (input == null) {
                throw new IOException("sound resource not found: " + path);
            }
            return readAll(input);
        } finally {
            closeQuietly(input);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static int max(int left, int right) { return left > right ? left : right; }
    private static int clamp(int min, int max, int value) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static void closeCreatedPlayer(CreatedPlayer created) {
        if (created == null) return;
        if (created.player != null) {
            try { created.player.close(); } catch (Throwable ignored) {}
        }
        closeQuietly(created.stream);
    }

    private static void closeQuietly(InputStream stream) {
        if (stream != null) {
            try { stream.close(); } catch (IOException ignored) {}
        }
    }

    private static final class SoundPool {
        final PooledPlayer[] players;
        int count;
        SoundPool(int size) { players = new PooledPlayer[size]; }
        PooledPlayer freePlayer() {
            int i;
            for (i = 0; i < count; i++) {
                if (players[i] != null && !players[i].inUse) return players[i];
            }
            return null;
        }
    }

    private static final class PooledPlayer {
        final int slot;
        final Player player;
        final InputStream stream;
        final boolean cached;
        final SoundPool owner;
        boolean inUse;
        PooledPlayer(int slot, Player player, InputStream stream, boolean cached, SoundPool owner) {
            this.slot = slot;
            this.player = player;
            this.stream = stream;
            this.cached = cached;
            this.owner = owner;
            this.inUse = !cached;
        }
        void bind(PlayerListener listener) {
            try { player.addPlayerListener(listener); } catch (Throwable ignored) {}
        }
        void unbind(PlayerListener listener) {
            try { player.removePlayerListener(listener); } catch (Throwable ignored) {}
        }
        void prepare(int loopCount, int startMillis) throws MediaException {
            try { player.stop(); } catch (Throwable ignored) {}
            try { player.setMediaTime(((long) startMillis) * 1000L); } catch (Throwable ignored) {}
            try { player.setLoopCount(loopCount); } catch (Throwable ignored) {}
        }
        void close() {
            try { player.stop(); } catch (Throwable ignored) {}
            try { player.deallocate(); } catch (Throwable ignored) {}
            try { player.close(); } catch (Throwable ignored) {}
            closeQuietly(stream);
            inUse = false;
        }
    }

    private static final class CreatedPlayer {
        final Player player;
        final InputStream stream;
        CreatedPlayer(Player player, InputStream stream) {
            this.player = player;
            this.stream = stream;
        }
    }
}
