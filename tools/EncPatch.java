import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/* Rewrites the original DoJa charset alias "SJIS" to "ISO-8859-1".
 * This is intentional: Nokia/S60 accepts ISO-8859-1 reliably, so the original
 * loader receives a non-throwing decoder and preserves the raw Shift-JIS bytes
 * as one Java char per byte.  The wrapper bitmap font then decodes those raw
 * bytes itself, avoiding device charset support entirely.
 */
public final class EncPatch {
    private static final String FROM = "SJIS";
    private static final String TO = "ISO-8859-1";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IOException("Usage: EncPatch <class-dir>");
        patchDir(new File(args[0]));
    }

    private static void patchDir(File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) patchDir(f);
            else if (f.getName().endsWith(".class")) patchFile(f);
        }
    }

    private static void patchFile(File f) throws Exception {
        byte[] data = readAll(f);
        byte[] patched = patchUtf8(data);
        if (patched != data) writeAll(f, patched);
    }

    private static byte[] patchUtf8(byte[] data) throws IOException {
        int p = 8;
        int count = u2(data, p); p += 2;
        byte[] current = data;
        for (int i = 1; i < count; i++) {
            int tag = current[p++] & 0xff;
            switch (tag) {
                case 1: {
                    int len = u2(current, p);
                    String s = new String(current, p + 2, len, "UTF-8");
                    if (FROM.equals(s)) {
                        byte[] nb = TO.getBytes("UTF-8");
                        byte[] out = new byte[current.length + nb.length - len];
                        System.arraycopy(current, 0, out, 0, p);
                        out[p] = (byte)((nb.length >>> 8) & 0xff);
                        out[p + 1] = (byte)(nb.length & 0xff);
                        System.arraycopy(nb, 0, out, p + 2, nb.length);
                        System.arraycopy(current, p + 2 + len, out, p + 2 + nb.length, current.length - (p + 2 + len));
                        current = out;
                        p += 2 + nb.length;
                    } else {
                        p += 2 + len;
                    }
                    break;
                }
                case 3: case 4: p += 4; break;
                case 5: case 6: p += 8; i++; break;
                case 7: case 8: p += 2; break;
                case 9: case 10: case 11: case 12: p += 4; break;
                default: throw new IOException("Unsupported constant tag " + tag + " in " + current.length + "-byte class");
            }
        }
        return current;
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
        } finally { in.close(); }
        return data;
    }

    private static void writeAll(File file, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(data); } finally { out.close(); }
    }

    private static int u2(byte[] b, int p) {
        return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
    }
}
