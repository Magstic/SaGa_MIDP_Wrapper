import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/*
 * Rewrites bytecode sequences:
 *   new String; dup; <args>; ldc "SJIS"; invokespecial String.<init>
 * into static calls to Sjis.decode(...), padded with NOPs so Code
 * attribute lengths and branch targets remain unchanged.
 */
public final class SjisPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IOException("Usage: SjisPatch <class-dir>");
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
        ClassInfo ci = parseConstants(data);
        if (ci.stringClass == 0 || ci.sjisString == 0 || (ci.ctorBytes == 0 && ci.ctorBytesSlice == 0)) return;
        int decodeBytesRef = ci.cpCount + 5;
        int decodeSliceRef = ci.cpCount + 8;
        int patched = patchMethods(data, ci, decodeBytesRef, decodeSliceRef);
        if (patched <= 0) return;
        byte[] appended = appendedConstants(ci.cpCount);
        byte[] out = new byte[data.length + appended.length];
        int cpEnd = ci.cpEnd;
        System.arraycopy(data, 0, out, 0, 8);
        putU2(out, 8, ci.cpCount + 9);
        System.arraycopy(data, 10, out, 10, cpEnd - 10);
        System.arraycopy(appended, 0, out, cpEnd, appended.length);
        System.arraycopy(data, cpEnd, out, cpEnd + appended.length, data.length - cpEnd);
        writeAll(f, out);
        System.out.println("[SjisPatch] " + f.getName() + " patched " + patched + " constructor call(s)");
    }

    private static byte[] appendedConstants(int oldCpCount) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int utfClass = oldCpCount;
        int cls = oldCpCount + 1;
        int utfName = oldCpCount + 2;
        int utfDescBytes = oldCpCount + 3;
        int natBytes = oldCpCount + 4;
        int utfDescSlice = oldCpCount + 6;
        int natSlice = oldCpCount + 7;
        writeUtf8(out, "Sjis");
        out.write(7); writeU2(out, utfClass);
        writeUtf8(out, "decode");
        writeUtf8(out, "([B)Ljava/lang/String;");
        out.write(12); writeU2(out, utfName); writeU2(out, utfDescBytes);
        out.write(10); writeU2(out, cls); writeU2(out, natBytes);
        writeUtf8(out, "([BII)Ljava/lang/String;");
        out.write(12); writeU2(out, utfName); writeU2(out, utfDescSlice);
        out.write(10); writeU2(out, cls); writeU2(out, natSlice);
        return out.toByteArray();
    }

    private static void writeUtf8(ByteArrayOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        out.write(1);
        writeU2(out, b.length);
        out.write(b);
    }

    private static void writeU2(ByteArrayOutputStream out, int v) {
        out.write((v >>> 8) & 0xff);
        out.write(v & 0xff);
    }

    private static int patchMethods(byte[] data, ClassInfo ci, int decodeBytesRef, int decodeSliceRef) throws IOException {
        int p = ci.cpEnd;
        int patched = 0;
        p += 6;
        int ifaces = u2(data, p); p += 2 + ifaces * 2;
        int fields = u2(data, p); p += 2;
        for (int i = 0; i < fields; i++) p = skipMember(data, p);
        int methods = u2(data, p); p += 2;
        for (int i = 0; i < methods; i++) {
            p += 6;
            int attrs = u2(data, p); p += 2;
            for (int a = 0; a < attrs; a++) {
                int nameIndex = u2(data, p);
                int len = u4(data, p + 2);
                int info = p + 6;
                if ("Code".equals(ci.utf[nameIndex])) {
                    int codeLen = u4(data, info + 4);
                    int codeOff = info + 8;
                    patched += patchCode(data, codeOff, codeLen, ci, decodeBytesRef, decodeSliceRef);
                }
                p = info + len;
            }
        }
        return patched;
    }

    private static int patchCode(byte[] data, int off, int len, ClassInfo ci, int decodeBytesRef, int decodeSliceRef) {
        int end = off + len;
        int n = 0;
        for (int p = off; p < end - 9; p++) {
            if ((data[p] & 0xff) != 0xbb || u2(data, p + 1) != ci.stringClass) continue;
            if ((data[p + 3] & 0xff) != 0x59) continue;
            int searchEnd = p + 48;
            if (searchEnd > end - 5) searchEnd = end - 5;
            for (int q = p + 4; q < searchEnd; q++) {
                int op = data[q] & 0xff;
                int ldcLen;
                int cidx;
                if (op == 0x12) { ldcLen = 2; cidx = data[q + 1] & 0xff; }
                else if (op == 0x13) { ldcLen = 3; cidx = u2(data, q + 1); }
                else continue;
                if (cidx != ci.sjisString) continue;
                int inv = q + ldcLen;
                if (inv + 3 > end || (data[inv] & 0xff) != 0xb7) continue;
                int ctor = u2(data, inv + 1);
                int ref;
                if (ctor == ci.ctorBytes) ref = decodeBytesRef;
                else if (ctor == ci.ctorBytesSlice) ref = decodeSliceRef;
                else continue;
                int total = inv + 3 - p;
                for (int k = 0; k < 4; k++) data[p + k] = 0; // remove new+dup
                data[q] = (byte)0xb8;
                putU2(data, q + 1, ref);
                for (int k = q + 3; k < p + total; k++) data[k] = 0;
                n++;
                p += total - 1;
                break;
            }
        }
        return n;
    }

    private static int skipMember(byte[] data, int p) {
        p += 6;
        int attrs = u2(data, p); p += 2;
        for (int i = 0; i < attrs; i++) {
            int len = u4(data, p + 2);
            p += 6 + len;
        }
        return p;
    }

    private static ClassInfo parseConstants(byte[] data) throws IOException {
        ClassInfo ci = new ClassInfo();
        ci.cpCount = u2(data, 8);
        ci.utf = new String[ci.cpCount + 16];
        int[] className = new int[ci.cpCount + 16];
        int[] stringIndex = new int[ci.cpCount + 16];
        int[] ntName = new int[ci.cpCount + 16];
        int[] ntDesc = new int[ci.cpCount + 16];
        int[] mrClass = new int[ci.cpCount + 16];
        int[] mrNameType = new int[ci.cpCount + 16];
        int p = 10;
        for (int i = 1; i < ci.cpCount; i++) {
            int tag = data[p++] & 0xff;
            switch (tag) {
                case 1: {
                    int l = u2(data, p);
                    ci.utf[i] = new String(data, p + 2, l, "UTF-8");
                    p += 2 + l;
                    break;
                }
                case 3: case 4: p += 4; break;
                case 5: case 6: p += 8; i++; break;
                case 7: className[i] = u2(data, p); p += 2; break;
                case 8: stringIndex[i] = u2(data, p); p += 2; break;
                case 9: case 10: case 11: mrClass[i] = u2(data, p); mrNameType[i] = u2(data, p + 2); p += 4; break;
                case 12: ntName[i] = u2(data, p); ntDesc[i] = u2(data, p + 2); p += 4; break;
                default: throw new IOException("unsupported cp tag " + tag);
            }
        }
        ci.cpEnd = p;
        for (int i = 1; i < ci.cpCount; i++) {
            if (className[i] != 0 && "java/lang/String".equals(ci.utf[className[i]])) ci.stringClass = i;
            if (stringIndex[i] != 0 && "SJIS".equals(ci.utf[stringIndex[i]])) ci.sjisString = i;
        }
        for (int i = 1; i < ci.cpCount; i++) {
            if (mrClass[i] == ci.stringClass) {
                int nt = mrNameType[i];
                String name = ci.utf[ntName[nt]];
                String desc = ci.utf[ntDesc[nt]];
                if ("<init>".equals(name) && "([BLjava/lang/String;)V".equals(desc)) ci.ctorBytes = i;
                if ("<init>".equals(name) && "([BIILjava/lang/String;)V".equals(desc)) ci.ctorBytesSlice = i;
            }
        }
        return ci;
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

    private static int u2(byte[] b, int p) { return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff); }
    private static int u4(byte[] b, int p) { return ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16) | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff); }
    private static void putU2(byte[] b, int p, int v) { b[p] = (byte)((v >>> 8) & 0xff); b[p + 1] = (byte)(v & 0xff); }

    private static final class ClassInfo {
        int cpCount;
        int cpEnd;
        String[] utf;
        int stringClass;
        int sjisString;
        int ctorBytes;
        int ctorBytesSlice;
    }
}
