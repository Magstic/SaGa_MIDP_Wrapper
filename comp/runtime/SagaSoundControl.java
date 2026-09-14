import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.Frame;
import doja.SoundPolicy;

/** Makai Toushi SaGa left-soft-key audio mode and slot classification. */
public final class SagaSoundControl implements SoundPolicy {
    private static final int MUSIC_ONLY = 0;
    private static final int SFX_ONLY = 1;
    private static final int BOTH = 2;
    private static final int MUTE = 3;

    private static final int ATTR_VOLUME = 4;
    private static final int PORT0_VOLUME = 70;
    private static final int PORT1_VOLUME = 60;
    private static final String[] LABELS = { "音楽", "効果", "音声", "ﾐｭｰﾄ" };
    private static final SagaSoundControl POLICY = new SagaSoundControl();
    private static int mode = BOTH;

    private SagaSoundControl() {}

    public int classify(String resourcePath) {
        int slot = soundSlot(resourcePath);
        if (slot < 0 || slot > 80) {
            throw new IllegalArgumentException("unknown SaGa sound: " + resourcePath);
        }
        return slot <= 20 ? BGM : SFX;
    }

    public static void next() {
        mode = mode == MUTE ? MUSIC_ONLY : mode + 1;
        apply();
    }

    public static void refresh() {
        if (j.I != null && j.I.length >= 2 && j.I[0] == 0 && j.I[1] == 0) mode = MUTE;
        apply();
    }

    private static void apply() {
        boolean music = mode == MUSIC_ONLY || mode == BOTH;
        boolean sfx = mode == SFX_ONLY || mode == BOTH;
        boolean any = music || sfx;

        // Keep SaGa's two original volume-state slots coherent, but do not use
        // the physical presenter port as the BGM/SFX classifier. Some BGM slots
        // intentionally use port 1.
        setState(0, music ? 2 : 0);
        setState(1, any ? 2 : 0);
        configurePort(0, any ? PORT0_VOLUME : 0, music, sfx);
        configurePort(1, any ? PORT1_VOLUME : 0, music, sfx);

        if (i.l != null) i.l.setSoftLabel(Frame.SOFT_KEY_1, LABELS[mode]);
    }

    private static void setState(int port, int value) {
        if (j.I != null && port < j.I.length) j.I[port] = value;
    }

    private static void configurePort(int port, int volume, boolean music, boolean sfx) {
        if (j.A == null || port >= j.A.length) return;
        AudioPresenter presenter = j.A[port];
        if (presenter == null) return;
        presenter.setAttribute(ATTR_VOLUME, volume);
        presenter.setSoundPolicy(POLICY, music, sfx);
    }

    private static int soundSlot(String path) {
        if (path == null) return -1;
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        int start = slash + 1;
        if (start < 0 || dot <= start) return -1;
        int value = 0;
        for (int p = start; p < dot; p++) {
            char c = path.charAt(p);
            if (c < '0' || c > '9') return -1;
            value = value * 10 + (c - '0');
        }
        return value;
    }
}
