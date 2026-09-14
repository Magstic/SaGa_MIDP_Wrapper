import java.io.IOException;
import doja.tools.scratchpad.Scratchpad;

/** Makai Toushi SaGa scratchpad layout. */
public final class GameScratchpad implements Scratchpad.Schema {
    private static final int SOUND_ARCHIVE_OFFSET = 778244;
    private static final int SOUND_SLOT_COUNT = 81;
    private static final int SFX_FIRST_SLOT = 21;

    public void apply(Scratchpad sp) throws Exception {
        sp.state("save data", 1, 1227).clear();
        replaceSoundTable(sp);

        // This title reads embedded ZIPs only as complete archive ranges through JarInflater.
        // Raw ZIP bytes are therefore redundant once the archives have been extracted.
        for (Scratchpad.Archive archive : sp.archives()) archive.omitBaseline();
    }

    private static void replaceSoundTable(Scratchpad sp) throws Exception {
        Scratchpad.Archive archive = sp.archiveAt(SOUND_ARCHIVE_OFFSET);
        Scratchpad.OffsetTable table = archive.entry("data.out").offsetTable16_32BE();
        if (table.size() != SOUND_SLOT_COUNT) {
            throw new IOException("sound table: expected " + SOUND_SLOT_COUNT
                    + " slots, got " + table.size());
        }

        byte[][] entries = new byte[table.size()][];
        for (int i = 0; i < entries.length; i++) {
            Scratchpad.Blob sound = table.entry(i);
            if (sound.length() == 0) {
                entries[i] = new byte[0];
            } else {
                if (sound.kind() != Scratchpad.Kind.MLD) {
                    throw new IOException("sound table slot " + i + " is not MLD");
                }
                entries[i] = i >= SFX_FIRST_SLOT
                        ? sp.soundWav("sound/" + three(i), sound)
                        : sp.sound("sound/" + three(i), sound);
            }
        }
        archive.replace("data.out", Scratchpad.offsetTable16_32BE(entries));
    }

    private static String three(int value) {
        String text = String.valueOf(value);
        while (text.length() < 3) text = "0" + text;
        return text;
    }
}
