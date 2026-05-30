import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/* Direct SP resource bridge for Makai Toushi SaGa.
 *
 * Runtime no longer carries raw scratchpad, ZIP bundles, or developer manifests.
 * Fixed resources are served from compact JAR paths and mutable scratchpad ranges
 * are overlaid from a single RMS store.
 */
public final class SpData {
    private static final int[] TOKEN_POS = new int[] {
        1604, 128918, 168972, 296958, 465070, 507703, 664167, 703288, 710578
    };
    private static final int[] TOKEN_LEN = new int[] {
        127314, 40054, 127968, 168112, 42633, 156450, 39121, 7290, 40132
    };
    private static final String[] TOKEN_TAG = new String[] {
        "127314_ba15a58b",
        "40054_ce8ff3c5",
        "127968_70367932",
        "168112_50756bee",
        "42633_f0ed6a8c",
        "156450_f08ba4cb",
        "39121_58f504c3",
        "7290_ae311457",
        "40132_28748bc0"
    };
    private static final String[] TOKEN_DIR = new String[] {
        "0", "1", "2", "3", "4", "5", "6", "7", "8"
    };

    private static final String RMS_NAME = "SagaSave";
    private static final int RMS_MAGIC = 0x53475356; /* SGSV */
    private static final int RMS_VERSION = 1;

    private static byte[] headCache;
    private static boolean rmsLoaded;
    private static int[] savePos = new int[16];
    private static byte[][] saveData = new byte[16][];
    private static int saveCount;

    private SpData() {}

    public static void preflight() throws IOException {
        if (readResourceFully("/s/h.bin") == null) {
            throw new IOException("missing /s/h.bin");
        }
        if (readResourceFully("/s/t1564.bin") == null) {
            throw new IOException("missing /s/t1564.bin");
        }
        if (readResourceFully("/e/0/o/0000.bin") == null) {
            throw new IOException("missing /e/0/o/0000.bin");
        }
        if (readResourceFully("/e/s/d/0000.bin") == null) {
            throw new IOException("missing /e/s/d/0000.bin");
        }
        ensureRmsLoaded();
    }

    public static byte[] d(int pos, int length) throws Exception {
        byte[] base;
        String tag;
        int len = length < 0 ? 0 : length;

        tag = tokenTag(pos, length);
        if (tag != null) {
            return tokenBytes(tag);
        }
        if (pos == 778240 && length == 4) {
            return new byte[] { 0, 0, 0, 0 };
        }
        if (pos == 778244 && length == 0) {
            return new byte[0];
        }

        base = baseRange(pos, len);
        if (isMutableRange(pos, len)) {
            ensureRmsLoaded();
            overlaySaved(base, pos);
        }
        return base;
    }

    public static int[] i(int pos) {
        byte[] table;
        int count;
        int[] out;
        int p;
        int n;
        try {
            table = readResourceFully("/s/t" + pos + ".bin");
            if (table == null || table.length < 2) {
                return null;
            }
            count = readU16(table, 0);
            out = new int[count];
            for (n = 0; n < count; n++) {
                p = 2 + n * 4;
                out[n] = readIntBE(table, p) + pos;
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean a(byte[] data, int pos) throws Exception {
        byte[] copy;
        int len = data == null ? 0 : data.length;
        if (!isMutableRange(pos, len)) {
            return true;
        }
        copy = new byte[len];
        if (len > 0) System.arraycopy(data, 0, copy, 0, len);
        ensureRmsLoaded();
        putSaved(pos, copy);
        flushRms();
        return true;
    }

    public static byte[][] a(byte[] archive, String entryName, int start, int count) throws Exception {
        byte[][] split = entrySplitBytes(archive, entryName, start, count);
        return split == null ? new byte[count][] : split;
    }

    private static byte[] baseRange(int pos, int length) throws IOException {
        byte[] head;
        byte[] out;
        if (length <= 0) return new byte[0];
        out = new byte[length];
        head = head();
        if (pos >= 0 && pos < head.length) {
            int n = head.length - pos;
            if (n > length) n = length;
            if (n > 0) System.arraycopy(head, pos, out, 0, n);
        }
        if (pos == 0 && length > 0 && out[0] == 0) {
            out[0] = (byte)0xad;
        }
        return out;
    }

    private static boolean isMutableRange(int pos, int len) {
        if (pos < 0 || len < 0) return false;
        if (pos == 0 && len == 1) return true;
        if (pos >= 1 && pos + len <= 1228) return true;
        return false;
    }

    private static void overlaySaved(byte[] out, int basePos) {
        int i;
        if (out == null || out.length == 0) return;
        for (i = 0; i < saveCount; i++) {
            int sPos = savePos[i];
            byte[] s = saveData[i];
            int src;
            int dst;
            int len;
            if (s == null || s.length == 0) continue;
            if (sPos >= basePos + out.length) continue;
            if (sPos + s.length <= basePos) continue;
            src = basePos > sPos ? basePos - sPos : 0;
            dst = sPos > basePos ? sPos - basePos : 0;
            len = s.length - src;
            if (len > out.length - dst) len = out.length - dst;
            if (len > 0) System.arraycopy(s, src, out, dst, len);
        }
    }

    private static void putSaved(int pos, byte[] data) {
        int i;
        for (i = 0; i < saveCount; i++) {
            if (savePos[i] == pos) {
                saveData[i] = data;
                return;
            }
        }
        if (saveCount >= savePos.length) {
            int[] np = new int[savePos.length * 2];
            byte[][] nd = new byte[saveData.length * 2][];
            System.arraycopy(savePos, 0, np, 0, savePos.length);
            System.arraycopy(saveData, 0, nd, 0, saveData.length);
            savePos = np;
            saveData = nd;
        }
        savePos[saveCount] = pos;
        saveData[saveCount] = data;
        saveCount++;
    }

    private static synchronized void ensureRmsLoaded() {
        javax.microedition.rms.RecordStore rs = null;
        byte[] blob;
        if (rmsLoaded) return;
        rmsLoaded = true;
        try {
            rs = javax.microedition.rms.RecordStore.openRecordStore(RMS_NAME, false);
            if (rs.getNumRecords() > 0) {
                blob = rs.getRecord(1);
                parseRmsBlob(blob);
            }
        } catch (Throwable ignored) {
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Throwable ignored) {}
        }
    }

    private static void parseRmsBlob(byte[] blob) {
        int p;
        int count;
        int i;
        if (blob == null || blob.length < 12) return;
        p = 0;
        if (readIntBE(blob, p) != RMS_MAGIC) return; p += 4;
        if (readIntBE(blob, p) != RMS_VERSION) return; p += 4;
        count = readIntBE(blob, p); p += 4;
        if (count < 0 || count > 64) return;
        for (i = 0; i < count; i++) {
            int pos;
            int len;
            byte[] data;
            if (p + 8 > blob.length) return;
            pos = readIntBE(blob, p); p += 4;
            len = readIntBE(blob, p); p += 4;
            if (len < 0 || p + len > blob.length) return;
            if (isMutableRange(pos, len)) {
                data = new byte[len];
                System.arraycopy(blob, p, data, 0, len);
                putSaved(pos, data);
            }
            p += len;
        }
    }

    private static synchronized void flushRms() {
        javax.microedition.rms.RecordStore rs = null;
        byte[] blob;
        try {
            blob = buildRmsBlob();
            rs = javax.microedition.rms.RecordStore.openRecordStore(RMS_NAME, true);
            if (rs.getNumRecords() <= 0) {
                rs.addRecord(blob, 0, blob.length);
            } else {
                rs.setRecord(1, blob, 0, blob.length);
            }
        } catch (Throwable ignored) {
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Throwable ignored) {}
        }
    }

    private static byte[] buildRmsBlob() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i;
        writeInt(out, RMS_MAGIC);
        writeInt(out, RMS_VERSION);
        writeInt(out, saveCount);
        for (i = 0; i < saveCount; i++) {
            byte[] data = saveData[i] == null ? new byte[0] : saveData[i];
            writeInt(out, savePos[i]);
            writeInt(out, data.length);
            out.write(data);
        }
        return out.toByteArray();
    }

    private static byte[][] entrySplitBytes(byte[] archive, String entryName, int start, int count) throws IOException {
        String dir;
        String entry;
        byte[][] out;
        int k;
        if (entryName == null || count < 0 || start < 0) return null;
        if (archive != null && archive.length == 0 && "data.out".equals(entryName)) {
            dir = "s";
            entry = "d";
        } else {
            dir = parseTokenDir(archive);
            entry = entryShortName(entryName);
        }
        if (dir == null) return null;
        out = new byte[count][];
        for (k = 0; k < count; k++) {
            String path = "/e/" + dir + "/" + entry + "/" + four(start + k) + ".bin";
            byte[] b = readResourceFully(path);
            if (b == null) {
                return null;
            }
            out[k] = b;
        }
        return out;
    }

    private static byte[] head() throws IOException {
        if (headCache == null) {
            headCache = readResourceFully("/s/h.bin");
            if (headCache == null) headCache = new byte[0];
        }
        return headCache;
    }

    private static String tokenTag(int pos, int length) {
        int k;
        for (k = 0; k < TOKEN_POS.length; k++) {
            if (TOKEN_POS[k] == pos && TOKEN_LEN[k] == length) return TOKEN_TAG[k];
        }
        return null;
    }

    private static String tokenDir(String tag) {
        int k;
        if (tag == null) return null;
        for (k = 0; k < TOKEN_TAG.length; k++) {
            if (tag.equals(TOKEN_TAG[k])) return TOKEN_DIR[k];
        }
        return null;
    }

    private static byte[] tokenBytes(String tag) throws IOException {
        String text = "SPTOK:" + tag + "\n";
        try {
            return text.getBytes("ISO-8859-1");
        } catch (Exception e) {
            return text.getBytes();
        }
    }

    private static String parseTokenDir(byte[] data) {
        byte[] p;
        int k;
        int start;
        int end;
        String tag;
        if (data == null || data.length < 7) return null;
        try {
            p = "SPTOK:".getBytes("ISO-8859-1");
        } catch (Exception e) {
            p = "SPTOK:".getBytes();
        }
        if (data.length < p.length) return null;
        for (k = 0; k < p.length; k++) {
            if (data[k] != p[k]) return null;
        }
        start = p.length;
        end = start;
        while (end < data.length && data[end] != 0 && data[end] != '\n' && data[end] != '\r') end++;
        if (end <= start) return null;
        try {
            tag = new String(data, start, end - start, "ISO-8859-1");
        } catch (Exception e) {
            tag = new String(data, start, end - start);
        }
        return tokenDir(tag);
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

    private static String four(int v) {
        String s = String.valueOf(v);
        while (s.length() < 4) s = "0" + s;
        return s;
    }

    private static int readU16(byte[] data, int p) {
        return ((data[p] & 0xff) << 8) | (data[p + 1] & 0xff);
    }

    private static int readIntBE(byte[] data, int p) {
        return ((data[p] & 0xff) << 24) | ((data[p + 1] & 0xff) << 16) |
               ((data[p + 2] & 0xff) << 8) | (data[p + 3] & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xff);
        out.write((v >>> 16) & 0xff);
        out.write((v >>> 8) & 0xff);
        out.write(v & 0xff);
    }

    private static byte[] readResourceFully(String path) throws IOException {
        InputStream in = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int r;
        try {
            in = SpData.class.getResourceAsStream(path);
            if (in == null) return null;
            while ((r = in.read(buf)) >= 0) {
                if (r > 0) out.write(buf, 0, r);
            }
            return out.toByteArray();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }
}
