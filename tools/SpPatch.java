import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class SpPatch {
    private static final String OLD_OWNER = "i";
    private static final String NEW_OWNER = "SpData";

    private static final String[][] METHODS = new String[][] {
        {"d", "(II)[B"},
        {"i", "(I)[I"},
        {"a", "([BLjava/lang/String;II)[[B"},
        {"a", "([BI)Z"}
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IOException("Usage: SpPatch <class-dir>");
        }
        patchDirectory(new File(args[0]));
    }

    private static void patchDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        int i;
        if (files == null) return;
        for (i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) patchDirectory(f);
            else if (f.getName().endsWith(".class")) patchFile(f);
        }
    }

    private static void patchFile(File file) throws Exception {
        byte[] data = readAll(file);
        ClassFile cf = ClassFile.parse(data);
        if (cf.patch()) {
            writeAll(file, cf.toBytes());
        }
    }

    private static byte[] readAll(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        byte[] data = new byte[(int)file.length()];
        int off = 0;
        try {
            while (off < data.length) {
                int r = in.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
        } finally {
            in.close();
        }
        return data;
    }

    private static void writeAll(File file, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(data); } finally { out.close(); }
    }

    private static boolean isTarget(String name, String desc) {
        int i;
        for (i = 0; i < METHODS.length; i++) {
            if (METHODS[i][0].equals(name) && METHODS[i][1].equals(desc)) return true;
        }
        return false;
    }

    private static final class ClassFile {
        byte[] header;
        CpEntry[] cp;
        byte[] rest;
        int originalCount;
        int newUtf8Index;
        int newClassIndex;
        boolean changed;

        static ClassFile parse(byte[] data) throws IOException {
            ClassFile cf = new ClassFile();
            int p = 0;
            int count;
            int i;
            if (u4(data, 0) != 0xCAFEBABE) throw new IOException("Not a class file");
            cf.header = new byte[8];
            System.arraycopy(data, 0, cf.header, 0, 8);
            p = 8;
            count = u2(data, p); p += 2;
            cf.originalCount = count;
            cf.cp = new CpEntry[count];
            for (i = 1; i < count; i++) {
                int tag = data[p++] & 0xff;
                CpEntry e = new CpEntry();
                e.tag = tag;
                switch (tag) {
                    case 1: {
                        int len = u2(data, p);
                        e.info = new byte[2 + len];
                        System.arraycopy(data, p, e.info, 0, e.info.length);
                        p += e.info.length;
                        break;
                    }
                    case 3: case 4:
                        e.info = copy(data, p, 4); p += 4; break;
                    case 5: case 6:
                        e.info = copy(data, p, 8); p += 8; cf.cp[i] = e; i++; continue;
                    case 7: case 8:
                        e.info = copy(data, p, 2); p += 2; break;
                    case 9: case 10: case 11: case 12:
                        e.info = copy(data, p, 4); p += 4; break;
                    default:
                        throw new IOException("Unsupported constant tag " + tag);
                }
                cf.cp[i] = e;
            }
            cf.rest = copy(data, p, data.length - p);
            return cf;
        }

        boolean patch() throws IOException {
            int idx = classIndex(NEW_OWNER);
            int i;
            if (idx <= 0) {
                newUtf8Index = originalCount;
                newClassIndex = originalCount + 1;
            } else {
                newClassIndex = idx;
            }
            for (i = 1; i < originalCount; i++) {
                CpEntry e = cp[i];
                if (e == null || e.tag != 10) continue;
                int cls = u2(e.info, 0);
                int nat = u2(e.info, 2);
                if (OLD_OWNER.equals(className(cls)) && isTarget(natName(nat), natDesc(nat))) {
                    putU2(e.info, 0, newClassIndex);
                    changed = true;
                }
            }
            return changed;
        }

        byte[] toBytes() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int count = originalCount;
            int i;
            out.write(header);
            if (changed && newUtf8Index > 0) count += 2;
            writeU2(out, count);
            for (i = 1; i < originalCount; i++) {
                CpEntry e = cp[i];
                if (e == null) continue;
                out.write(e.tag);
                out.write(e.info);
            }
            if (changed && newUtf8Index > 0) {
                byte[] name = ascii(NEW_OWNER);
                out.write(1);
                writeU2(out, name.length);
                out.write(name);
                out.write(7);
                writeU2(out, newUtf8Index);
            }
            out.write(rest);
            return out.toByteArray();
        }

        int classIndex(String name) {
            int i;
            for (i = 1; i < originalCount; i++) {
                if (cp[i] != null && cp[i].tag == 7 && name.equals(className(i))) return i;
            }
            return -1;
        }

        String className(int classIndex) {
            CpEntry e = cp[classIndex];
            if (e == null || e.tag != 7) return null;
            return utf8(u2(e.info, 0));
        }

        String natName(int natIndex) {
            CpEntry e = cp[natIndex];
            if (e == null || e.tag != 12) return null;
            return utf8(u2(e.info, 0));
        }

        String natDesc(int natIndex) {
            CpEntry e = cp[natIndex];
            if (e == null || e.tag != 12) return null;
            return utf8(u2(e.info, 2));
        }

        String utf8(int index) {
            CpEntry e = cp[index];
            int len;
            if (e == null || e.tag != 1) return null;
            len = u2(e.info, 0);
            return new String(e.info, 2, len);
        }
    }

    private static final class CpEntry {
        int tag;
        byte[] info;
    }

    private static int u2(byte[] b, int p) {
        return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
    }

    private static int u4(byte[] b, int p) {
        return ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16) | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff);
    }

    private static void putU2(byte[] b, int p, int v) {
        b[p] = (byte)((v >>> 8) & 0xff);
        b[p + 1] = (byte)(v & 0xff);
    }

    private static void writeU2(ByteArrayOutputStream out, int v) {
        out.write((v >>> 8) & 0xff);
        out.write(v & 0xff);
    }

    private static byte[] copy(byte[] src, int p, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, p, out, 0, len);
        return out;
    }

    private static byte[] ascii(String s) {
        byte[] out = new byte[s.length()];
        int i;
        for (i = 0; i < out.length; i++) out[i] = (byte)s.charAt(i);
        return out;
    }
}
