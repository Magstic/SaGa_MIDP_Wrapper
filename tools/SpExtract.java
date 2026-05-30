import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SpExtract {
    private static final int SP_HEADER = 64;
    private static final int SP_SIZE = 819200;

    private static final int[][] TABLES = new int[][] {
        {1564}, {1586}, {296940}, {664153}
    };

    private static final int[][] ZIP_RANGES = new int[][] {
        {1604,   127314},
        {128918,  40054},
        {168972, 127968},
        {296958, 168112},
        {465070,  42633},
        {507703, 156450},
        {664167,  39121},
        {703288,   7290},
        {710578,  40132}
    };

    private SpExtract() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: SpExtract <input.sp> <meta-dir>");
        }
        File spFile = new File(args[0]);
        File metaDir = new File(args[1]);
        File sDir = new File(metaDir, "s");
        File eDir = new File(metaDir, "e");
        File manifest = new File(metaDir, "sp_resources_manifest.csv");
        byte[] sp = readAll(spFile);
        byte[] logical = normalize(sp);
        StringBuffer csv = new StringBuffer();
        int i;

        mkdirs(sDir);
        mkdirs(eDir);
        csv.append("kind,offset,length,tag,path,entry_size\n");

        for (i = 0; i < TABLES.length; i++) {
            int pos = TABLES[i][0];
            byte[] table = readTableBytes(logical, pos);
            File out = new File(sDir, "t" + pos + ".bin");
            write(out, table);
            csv.append("table,").append(pos).append(',').append(table.length).append(",,s/")
               .append(out.getName()).append(',').append(table.length).append('\n');
        }

        byte[] head = slice(logical, 0, 1604);
        clearInitialSaveArea(head);
        write(new File(sDir, "h.bin"), head);
        csv.append("head,0,1604,,s/h.bin,1604\n");

        byte[] soundTable = buildTokenSoundTable();
        splitTableEntry(soundTable, new File(new File(eDir, "s"), "d"), csv, 0, 0, "s", "d");

        for (i = 0; i < ZIP_RANGES.length; i++) {
            int off = ZIP_RANGES[i][0];
            int len = ZIP_RANGES[i][1];
            byte[] zip = slice(logical, off, len);
            String tag = len + "_" + hex8(fnv1a(zip));
            String dir = String.valueOf(i);
            int entries = extractZip(zip, new File(eDir, dir), csv, off, len, tag, dir);
            if (entries == 0) {
                throw new IOException("No ZIP entries extracted at offset " + off + " length " + len);
            }
        }

        /* Kept in build/meta for developer inspection only. build.xml does not
         * package this file into the MIDP JAR.
         */
        write(manifest, csv.toString().getBytes("UTF-8"));
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

    private static byte[] readTableBytes(byte[] sp, int pos) throws IOException {
        if (pos < 0 || pos + 2 > sp.length) throw new IOException("Bad table pos " + pos);
        int count = ((sp[pos] & 0xff) << 8) | (sp[pos + 1] & 0xff);
        int len = 2 + count * 4;
        if (count < 0 || count > 4096 || pos + len > sp.length) {
            throw new IOException("Bad table count " + count + " at " + pos);
        }
        return slice(sp, pos, len);
    }


    private static void clearInitialSaveArea(byte[] head) {
        int i;
        if (head == null || head.length == 0) return;
        /* Keep the DoJa scratchpad initialized marker at pos 0, but do not
         * ship the original phone's save metadata or save slots.
         *
         * Known mutable layout used by this title:
         *   0                  initialization marker, must remain 0xAD
         *   1                  save-slot bit flags
         *   2                  last/current slot
         *   3                  launch/download scratch flag
         *   28 + slot * 300    save slot body, 4 slots of 300 bytes
         *
         * The offset tables begin at 1564 and are extracted separately, so the
         * 1..1227 region can be safely blanked to create a clean first boot.
         */
        head[0] = (byte)0xad;
        for (i = 1; i <= 1227 && i < head.length; i++) {
            head[i] = 0;
        }
    }

    private static byte[] buildTokenSoundTable() throws IOException {
        int[] slots = new int[] {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 49, 50, 51, 52,
            53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67,
            68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80
        };
        boolean[] hasSound = new boolean[81];
        byte[][] payload = new byte[81][];
        int count = 82;
        int header = 2 + count * 4;
        int[] offsets = new int[count];
        ByteArrayOutputStream payloads = new ByteArrayOutputStream();
        byte[] out;
        int i;
        int p;

        for (i = 0; i < slots.length; i++) {
            hasSound[slots[i]] = true;
        }
        for (i = 0; i < payload.length; i++) {
            payload[i] = hasSound[i] ? tokenPayload(i) : new byte[0];
        }

        p = header;
        for (i = 0; i < 81; i++) {
            offsets[i] = p;
            payloads.write(payload[i]);
            p += payload[i].length;
        }
        offsets[81] = p;

        out = new byte[p];
        out[0] = (byte)((count >>> 8) & 0xff);
        out[1] = (byte)(count & 0xff);
        for (i = 0; i < count; i++) {
            int q = 2 + i * 4;
            int off = offsets[i];
            out[q]     = (byte)((off >>> 24) & 0xff);
            out[q + 1] = (byte)((off >>> 16) & 0xff);
            out[q + 2] = (byte)((off >>> 8) & 0xff);
            out[q + 3] = (byte)(off & 0xff);
        }
        byte[] body = payloads.toByteArray();
        System.arraycopy(body, 0, out, header, body.length);
        return out;
    }

    private static byte[] tokenPayload(int slot) throws IOException {
        String s;
        if (slot < 10) {
            s = "SND:00" + slot;
        } else if (slot < 100) {
            s = "SND:0" + slot;
        } else {
            s = "SND:" + slot;
        }
        return s.getBytes("ISO-8859-1");
    }

    private static int extractZip(byte[] zip, File tagDir, StringBuffer csv, int offset, int length, String tag, String dir) throws IOException {
        ZipInputStream zis = null;
        int count = 0;
        try {
            zis = new ZipInputStream(new ByteArrayInputStream(zip));
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = sanitize(entry.getName());
                String shortName = entryShortName(name);
                ByteArrayOutputStream entryData = new ByteArrayOutputStream();
                int total = 0;
                int r;
                while ((r = zis.read(buffer)) >= 0) {
                    if (r > 0) {
                        entryData.write(buffer, 0, r);
                        total += r;
                    }
                }
                byte[] entryBytes = entryData.toByteArray();
                csv.append("zip,").append(offset).append(',').append(length).append(',').append(tag)
                   .append(",e/").append(dir).append('/').append(shortName).append(',').append(total).append('\n');
                splitTableEntry(entryBytes, new File(tagDir, shortName), csv, offset, length, dir, shortName);
                count++;
            }
        } finally {
            if (zis != null) zis.close();
        }
        return count;
    }

    private static String entryShortName(String name) {
        if ("out.out".equals(name)) return "o";
        if ("out1.out".equals(name)) return "o1";
        if ("out2.out".equals(name)) return "o2";
        if ("event.out".equals(name)) return "ev";
        if ("Character.out".equals(name)) return "ch";
        if ("Other.out".equals(name)) return "ot";
        if ("TableData.out".equals(name)) return "td";
        if ("data.out".equals(name)) return "d";
        return name;
    }

    private static void splitTableEntry(byte[] data, File baseDir, StringBuffer csv, int offset, int length, String tag, String entryName) throws IOException {
        int count;
        int i;
        int[] offsets;
        if (data == null || data.length < 6) return;
        count = ((data[0] & 0xff) << 8) | (data[1] & 0xff);
        if (count < 2 || count > 4096 || 2 + count * 4 > data.length) return;
        offsets = new int[count];
        for (i = 0; i < count; i++) {
            offsets[i] = readIntBE(data, 2 + i * 4);
            if (offsets[i] < 0 || offsets[i] > data.length) return;
            if (i > 0 && offsets[i] < offsets[i - 1]) return;
        }
        mkdirs(baseDir);
        for (i = 0; i < count - 1; i++) {
            int p = offsets[i];
            int len = offsets[i + 1] - p;
            if (len < 0 || p + len > data.length) return;
            File out = new File(baseDir, four(i) + ".bin");
            write(out, slice(data, p, len));
            csv.append("split,").append(offset).append(',').append(length).append(',').append(tag)
               .append(",e/").append(tag).append('/').append(entryName).append('/').append(four(i)).append(".bin,").append(len).append('\n');
        }
    }

    private static String four(int v) {
        String s = String.valueOf(v);
        while (s.length() < 4) s = "0" + s;
        return s;
    }

    private static int readIntBE(byte[] data, int p) {
        return ((data[p] & 0xff) << 24) | ((data[p + 1] & 0xff) << 16) |
               ((data[p + 2] & 0xff) << 8) | (data[p + 3] & 0xff);
    }

    private static String sanitize(String name) throws IOException {
        String s = name.replace('\\', '/');
        if (s.indexOf("../") >= 0 || s.startsWith("/") || s.length() == 0) {
            throw new IOException("Unsafe ZIP entry: " + name);
        }
        return s;
    }

    private static int fnv1a(byte[] data) {
        int h = 0x811c9dc5;
        int i;
        for (i = 0; i < data.length; i++) {
            h ^= data[i] & 0xff;
            h *= 0x01000193;
        }
        return h;
    }

    private static String hex8(int v) {
        String s = Integer.toHexString(v);
        while (s.length() < 8) s = "0" + s;
        return s;
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

    private static void write(File file, byte[] data) throws IOException {
        mkdirs(file.getParentFile());
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(data);
        } finally {
            if (out != null) out.close();
        }
    }

    private static void mkdirs(File dir) throws IOException {
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory " + dir);
        }
    }
}
