import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.Frame;

/* Patched soft-key sound mode controller.
 *
 * The original game cycles both audio channels through volume levels.  The
 * wrapper patches the left-soft-key call sites to this class and turns the key
 * into a category selector instead:
 *   music only -> SFX only -> music+SFX -> mute -> ...
 */
public final class SoundCtl {
    private static final int MUSIC_ONLY = 0;
    private static final int SFX_ONLY = 1;
    private static final int BOTH = 2;
    private static final int MUTE = 3;

    private static final int MUSIC_CHANNEL = 0;
    private static final int SFX_CHANNEL = 1;
    private static final int ATTR_VOLUME = 4;

    private static final int MUSIC_VOLUME = 70;
    private static final int SFX_VOLUME = 60;

    private static int mode = BOTH;

    private static final String[] LABELS = {
        "音楽",
        "効果",
        "音声",
        "ﾐｭｰﾄ"
    };

    private SoundCtl() {}

    /* Replacement for j.e(): called when the left soft key is pressed. */
    public static void e() {
        mode++;
        if (mode > MUTE) mode = MUSIC_ONLY;
        apply();
    }

    /* Replacement for j.f(): called when the soft label is refreshed. */
    public static void f() {
        syncFromOriginalStateOnce();
        apply();
    }

    private static void syncFromOriginalStateOnce() {
        /* j.c() initializes both original volume slots to level 1 before the
         * first label refresh.  Keep our default as BOTH.  If future code has
         * already forced both original slots to zero before refresh, reflect
         * that as mute. */
        try {
            if (j.I != null && j.I.length >= 2 && j.I[0] == 0 && j.I[1] == 0) {
                mode = MUTE;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void apply() {
        boolean music = mode == MUSIC_ONLY || mode == BOTH;
        boolean sfx = mode == SFX_ONLY || mode == BOTH;
        setChannel(MUSIC_CHANNEL, music, MUSIC_VOLUME, music, sfx);
        setChannel(SFX_CHANNEL, music || sfx, SFX_VOLUME, music, sfx);
        setLabel();
    }

    private static void setChannel(int channel, boolean enabled, int volume, boolean music, boolean sfx) {
        int level = enabled ? 2 : 0;
        int actualVolume = enabled ? volume : 0;
        try {
            if (j.I != null && channel >= 0 && channel < j.I.length) {
                j.I[channel] = level;
            }
            if (j.A != null && channel >= 0 && channel < j.A.length) {
                AudioPresenter presenter = j.A[channel];
                if (presenter != null) {
                    presenter.setAttribute(ATTR_VOLUME, actualVolume);
                    presenter.setOutputPolicy(music, sfx);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setLabel() {
        try {
            Frame frame = i.l;
            if (frame != null) {
                frame.setSoftLabel(Frame.SOFT_KEY_1, LABELS[mode]);
            }
        } catch (Throwable ignored) {
        }
    }
}
