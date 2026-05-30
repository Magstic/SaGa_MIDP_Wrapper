import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/*
 * Redirects original-game calls:
 *   j.e()V -> SoundCtl.e()V
 *   j.f()V -> SoundCtl.f()V
 *
 * The stack shape is unchanged because both are static no-arg void methods.
 */
public final class SoundPatch {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IOException("Usage: SoundPatch <class-dir>");
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
        int modalPatched = "j.class".equals(f.getName()) ? patchModalResumeGate(data, ci) : 0;
        if (ci.oldERef == 0 && ci.oldFRef == 0) {
            if (modalPatched > 0) {
                writeAll(f, data);
                System.out.println("[SoundPatch] " + f.getName() + " patched " + modalPatched + " modal resume gate(s)");
            }
            return;
        }

        int newERef = ci.cpCount + 5;
        int newFRef = ci.cpCount + 8;
        int patched = patchMethods(data, ci, newERef, newFRef);
        if (patched <= 0) {
            if (modalPatched > 0) {
                writeAll(f, data);
                System.out.println("[SoundPatch] " + f.getName() + " patched " + modalPatched + " modal resume gate(s)");
            }
            return;
        }

        byte[] appended = appendedConstants(ci.cpCount);
        byte[] out = new byte[data.length + appended.length];
        int cpEnd = ci.cpEnd;
        System.arraycopy(data, 0, out, 0, 8);
        putU2(out, 8, ci.cpCount + 9);
        System.arraycopy(data, 10, out, 10, cpEnd - 10);
        System.arraycopy(appended, 0, out, cpEnd, appended.length);
        System.arraycopy(data, cpEnd, out, cpEnd + appended.length, data.length - cpEnd);
        writeAll(f, out);
        System.out.println("[SoundPatch] " + f.getName() + " patched " + patched + " call(s)");
        if (modalPatched > 0) {
            System.out.println("[SoundPatch] " + f.getName() + " patched " + modalPatched + " modal resume gate(s)");
        }
    }

    private static byte[] appendedConstants(int oldCpCount) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int utfClass = oldCpCount;
        int cls = oldCpCount + 1;
        int utfE = oldCpCount + 2;
        int utfDesc = oldCpCount + 3;
        int ntE = oldCpCount + 4;
        int mrE = oldCpCount + 5;
        int utfF = oldCpCount + 6;
        int ntF = oldCpCount + 7;
        int mrF = oldCpCount + 8;
        writeUtf8(out, "SoundCtl");
        out.write(7); writeU2(out, utfClass);
        writeUtf8(out, "e");
        writeUtf8(out, "()V");
        out.write(12); writeU2(out, utfE); writeU2(out, utfDesc);
        out.write(10); writeU2(out, cls); writeU2(out, ntE);
        writeUtf8(out, "f");
        out.write(12); writeU2(out, utfF); writeU2(out, utfDesc);
        out.write(10); writeU2(out, cls); writeU2(out, ntF);
        return out.toByteArray();
    }

    private static int patchMethods(byte[] data, ClassInfo ci, int newERef, int newFRef) throws IOException {
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
                    patched += patchCode(data, codeOff, codeLen, ci.oldERef, newERef, ci.oldFRef, newFRef);
                }
                p = info + len;
            }
        }
        return patched;
    }

    private static int patchCode(byte[] data, int off, int len, int oldE, int newE, int oldF, int newF) {
        int end = off + len;
        int n = 0;
        for (int p = off; p < end - 2; p++) {
            if ((data[p] & 0xff) == 0xb8) {
                int ref = u2(data, p + 1);
                if (oldE != 0 && ref == oldE) {
                    putU2(data, p + 1, newE);
                    n++;
                } else if (oldF != 0 && ref == oldF) {
                    putU2(data, p + 1, newF);
                    n++;
                }
            }
        }
        return n;
    }

    private static int patchModalResumeGate(byte[] data, ClassInfo ci) throws IOException {
        int patched = patchModalResumeMethods(data, ci);
        if (patched != 1) {
            throw new IOException("expected exactly one modal resume gate in j.class, patched " + patched);
        }
        return patched;
    }

    private static int patchModalResumeMethods(byte[] data, ClassInfo ci) throws IOException {
        int p = ci.cpEnd;
        int patched = 0;
        p += 6;
        int ifaces = u2(data, p); p += 2 + ifaces * 2;
        int fields = u2(data, p); p += 2;
        for (int i = 0; i < fields; i++) p = skipMember(data, p);
        int methods = u2(data, p); p += 2;
        for (int i = 0; i < methods; i++) {
            String methodName = ci.utf[u2(data, p + 2)];
            String methodDesc = ci.utf[u2(data, p + 4)];
            boolean targetMethod = "mediaAction".equals(methodName)
                    && "(Lcom/nttdocomo/ui/MediaPresenter;II)V".equals(methodDesc);
            p += 6;
            int attrs = u2(data, p); p += 2;
            for (int a = 0; a < attrs; a++) {
                int nameIndex = u2(data, p);
                int len = u4(data, p + 2);
                int info = p + 6;
                if (targetMethod && "Code".equals(ci.utf[nameIndex])) {
                    int codeLen = u4(data, info + 4);
                    int codeOff = info + 8;
                    patched += patchModalResumeCode(data, codeOff, codeLen, ci.fieldBRef);
                }
                p = info + len;
            }
        }
        return patched;
    }

    private static int patchModalResumeCode(byte[] data, int off, int len, int fieldBRef) {
        int end = off + len;
        int n = 0;
        if (fieldBRef == 0) return 0;
        for (int p = off; p < end - 6; p++) {
            if ((data[p] & 0xff) == 0xb2 && u2(data, p + 1) == fieldBRef
                    && (data[p + 3] & 0xff) == 0x03
                    && (data[p + 4] & 0xff) == 0x2e
                    && (data[p + 5] & 0xff) == 0x02
                    && (data[p + 6] & 0xff) == 0xa0) {
                data[p + 6] = (byte)0xa3;
                n++;
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
        ci.utf = new String[ci.cpCount + 10];
        ci.tag = new int[ci.cpCount + 10];
        int[] className = new int[ci.cpCount + 10];
        int[] ntName = new int[ci.cpCount + 10];
        int[] ntDesc = new int[ci.cpCount + 10];
        int[] mrClass = new int[ci.cpCount + 10];
        int[] mrNameType = new int[ci.cpCount + 10];
        int p = 10;
        for (int i = 1; i < ci.cpCount; i++) {
            int tag = data[p++] & 0xff;
            ci.tag[i] = tag;
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
                case 8: p += 2; break;
                case 9: case 10: case 11:
                    mrClass[i] = u2(data, p);
                    mrNameType[i] = u2(data, p + 2);
                    p += 4;
                    break;
                case 12:
                    ntName[i] = u2(data, p);
                    ntDesc[i] = u2(data, p + 2);
                    p += 4;
                    break;
                default:
                    throw new IOException("unsupported cp tag " + tag);
            }
        }
        ci.cpEnd = p;
        int jClass = 0;
        for (int i = 1; i < ci.cpCount; i++) {
            if (className[i] != 0 && "j".equals(ci.utf[className[i]])) {
                jClass = i;
                break;
            }
        }
        if (jClass == 0) return ci;
        for (int i = 1; i < ci.cpCount; i++) {
            if (mrClass[i] == jClass) {
                int nt = mrNameType[i];
                String name = ci.utf[ntName[nt]];
                String desc = ci.utf[ntDesc[nt]];
                if (ci.tag[i] == 9 && "B".equals(name) && "[I".equals(desc)) {
                    ci.fieldBRef = i;
                } else if ("()V".equals(desc)) {
                    if ("e".equals(name)) ci.oldERef = i;
                    else if ("f".equals(name)) ci.oldFRef = i;
                }
            }
        }
        return ci;
    }

    private static void writeUtf8(ByteArrayOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        out.write(1); writeU2(out, b.length); out.write(b);
    }
    private static void writeU2(ByteArrayOutputStream out, int v) { out.write((v >>> 8) & 0xff); out.write(v & 0xff); }
    private static byte[] readAll(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        byte[] data = new byte[(int)file.length()];
        int off = 0;
        try { while (off < data.length) { int r = in.read(data, off, data.length - off); if (r < 0) break; off += r; } } finally { in.close(); }
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
        int[] tag;
        int oldERef;
        int oldFRef;
        int fieldBRef;
    }
}
