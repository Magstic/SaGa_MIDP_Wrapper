import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.sound.midi.MidiSystem;

import container.MldFile;
import container.MldParser;
import event.TrackDecodeResult;
import event.TrackDecoder;
import normalize.MdNormalizationResult;
import normalize.MdNormalizer;
import playback.PlaybackSequenceBuilder;
import timeline.PlaybackTimeline;
import timeline.TimelineCompiler;

public final class SoundBuild {
    private static final int SP_HEADER = 64;
    private static final int SP_SIZE = 819200;
    private static final int SOUND_ARCHIVE_POS = 778240;
    private static final int SOUND_SLOT_COUNT = 81;
    private static final int WAV_FIRST_SLOT = 21;

    private static final int[] SLOTS = new int[] {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 20,
        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 49, 50, 51, 52,
        53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67,
        68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80
    };

    private SoundBuild() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: SoundBuild <input.sp> <output-sound-dir>");
        }
        new SoundBuild().build(new File(args[0]), new File(args[1]));
    }

    private void build(File spFile, File outDir) throws Exception {
        byte[][] entries = soundEntries(normalize(readAll(spFile)));
        int midCount = 0;
        int wavCount = 0;
        int i;

        mkdirs(outDir);
        for (i = 0; i < SLOTS.length; i++) {
            int slot = SLOTS[i];
            byte[] mld = entries[slot];
            File mid = new File(outDir, three(slot) + ".mid");
            if (mld == null || mld.length <= 2) {
                throw new IOException("Missing MLD sound slot " + slot);
            }
            writeMidi(mld, mid);
            if (slot >= WAV_FIRST_SLOT) {
                File wav = new File(outDir, three(slot) + ".wav");
                MidToWav.render(mid, wav);
                if (!mid.delete()) {
                    throw new IOException("Failed to delete intermediate MIDI " + mid);
                }
                wavCount++;
            } else {
                midCount++;
            }
        }
        validateOutput(outDir, midCount, wavCount);
        System.out.println("[SoundBuild] generated " + midCount + " MIDI and " + wavCount + " WAV sound file(s)");
    }

    private static byte[][] soundEntries(byte[] sp) throws IOException {
        int archiveLen;
        byte[] archive;
        byte[] dataOut;
        byte[][] entries;
        int p = SOUND_ARCHIVE_POS;

        if (p < 0 || p + 4 > sp.length) {
            throw new IOException("Bad sound archive position " + p);
        }
        archiveLen = readIntBE(sp, p);
        if (archiveLen <= 0 || p + 4 + archiveLen > sp.length) {
            throw new IOException("Bad sound archive length " + archiveLen);
        }
        archive = slice(sp, p + 4, archiveLen);
        dataOut = zipEntry(archive, "data.out");
        if (dataOut == null) {
            throw new IOException("Sound archive does not contain data.out");
        }
        entries = splitTable(dataOut);
        if (entries.length < SOUND_SLOT_COUNT) {
            throw new IOException("Sound table has only " + entries.length + " slot(s)");
        }
        return entries;
    }

    private static byte[] normalize(byte[] raw) throws IOException {
        if (raw.length >= SP_HEADER + SP_SIZE && (raw[SP_HEADER] & 0xff) == 0xad) {
            return slice(raw, SP_HEADER, SP_SIZE);
        }
        if (raw.length == SP_SIZE && (raw[0] & 0xff) == 0xad) {
            return raw;
        }
        throw new IOException("Unexpected scratchpad size/header: " + raw.length);
    }

    private static byte[] zipEntry(byte[] zip, String name) throws IOException {
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new ByteArrayInputStream(zip));
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (!name.equals(entry.getName())) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int r;
                while ((r = zis.read(buffer)) >= 0) {
                    if (r > 0) out.write(buffer, 0, r);
                }
                return out.toByteArray();
            }
            return null;
        } finally {
            if (zis != null) zis.close();
        }
    }

    private static byte[][] splitTable(byte[] data) throws IOException {
        int count;
        int[] offsets;
        byte[][] out;
        int i;
        if (data == null || data.length < 6) {
            throw new IOException("Sound table is too small");
        }
        count = readU16(data, 0);
        if (count < 2 || count > 4096 || 2 + count * 4 > data.length) {
            throw new IOException("Bad sound table count " + count);
        }
        offsets = new int[count];
        for (i = 0; i < count; i++) {
            offsets[i] = readIntBE(data, 2 + i * 4);
            if (offsets[i] < 0 || offsets[i] > data.length) {
                throw new IOException("Bad sound table offset " + offsets[i] + " at " + i);
            }
            if (i > 0 && offsets[i] < offsets[i - 1]) {
                throw new IOException("Unsorted sound table offset at " + i);
            }
        }
        out = new byte[count - 1][];
        for (i = 0; i < out.length; i++) {
            out[i] = slice(data, offsets[i], offsets[i + 1] - offsets[i]);
        }
        return out;
    }

    private static void writeMidi(byte[] mld, File mid) throws Exception {
        MldParser parser = new MldParser();
        MldFile file = parser.parse(mld);
        TrackDecoder decoder = new TrackDecoder();
        List<TrackDecodeResult> decoded = new ArrayList<TrackDecodeResult>();
        int i;
        for (i = 0; i < file.tracks.size(); i++) {
            decoded.add(decoder.decode(file, file.tracks.get(i)));
        }
        MdNormalizationResult normalization = new MdNormalizer().normalize(decoded);
        PlaybackTimeline timeline = new TimelineCompiler().compile(file, decoded, normalization);
        PlaybackSequenceBuilder.BuiltSequence built = new PlaybackSequenceBuilder().build(timeline);
        mkdirs(mid.getParentFile());
        MidiSystem.write(built.sequence, 1, mid);
    }

    private static void validateOutput(File outDir, int expectedMid, int expectedWav) throws IOException {
        String[] names = outDir.list();
        int mid = 0;
        int wav = 0;
        int i;
        if (names == null) {
            throw new IOException("Generated sound directory is missing: " + outDir);
        }
        for (i = 0; i < names.length; i++) {
            String name = names[i];
            if (name.endsWith(".mid")) mid++;
            else if (name.endsWith(".wav")) wav++;
        }
        if (mid != expectedMid || wav != expectedWav || mid + wav != SLOTS.length) {
            throw new IOException("Bad generated sound count: mid=" + mid + " wav=" + wav);
        }
    }

    private static String three(int v) {
        String s = String.valueOf(v);
        while (s.length() < 3) s = "0" + s;
        return s;
    }

    private static int readU16(byte[] data, int p) {
        return ((data[p] & 0xff) << 8) | (data[p + 1] & 0xff);
    }

    private static int readIntBE(byte[] data, int p) {
        return ((data[p] & 0xff) << 24) | ((data[p + 1] & 0xff) << 16) |
               ((data[p + 2] & 0xff) << 8) | (data[p + 3] & 0xff);
    }

    private static byte[] slice(byte[] src, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > src.length) {
            throw new IOException("Bad slice off=" + off + " len=" + len + " size=" + src.length);
        }
        byte[] out = new byte[len];
        System.arraycopy(src, off, out, 0, len);
        return out;
    }

    private static byte[] readAll(File file) throws IOException {
        FileInputStream in = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            in = new FileInputStream(file);
            int r;
            while ((r = in.read(buffer)) >= 0) {
                if (r > 0) out.write(buffer, 0, r);
            }
            return out.toByteArray();
        } finally {
            if (in != null) in.close();
        }
    }

    private static void mkdirs(File dir) throws IOException {
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Failed to create directory " + dir);
        }
    }
}
