import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class ConnPatch {
    private static final byte[] FROM = bytes("javax/microedition/io/Connector");
    private static final byte[] TO = bytes("com/nttdocomo/io/ConnectorProxy");

    public static void main(String[] args) throws IOException {
        if (FROM.length != TO.length) {
            throw new IOException("Patch strings must have equal byte length");
        }
        if (args.length != 1) {
            throw new IOException("Usage: ConnPatch <class-dir>");
        }
        patchDirectory(new File(args[0]));
    }

    private static void patchDirectory(File dir) throws IOException {
        File[] files = dir.listFiles();
        int i;

        if (files == null) {
            return;
        }
        for (i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                patchDirectory(file);
            } else if (file.getName().endsWith(".class")) {
                patchFile(file);
            }
        }
    }

    private static void patchFile(File file) throws IOException {
        byte[] data = readAll(file);
        boolean changed = false;
        int i;

        for (i = 0; i <= data.length - FROM.length; i++) {
            if (matches(data, i)) {
                System.arraycopy(TO, 0, data, i, TO.length);
                changed = true;
                i += FROM.length - 1;
            }
        }

        if (changed) {
            writeAll(file, data);
        }
    }

    private static boolean matches(byte[] data, int offset) {
        int i;
        for (i = 0; i < FROM.length; i++) {
            if (data[offset + i] != FROM[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] readAll(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        byte[] data;
        int offset = 0;

        try {
            data = new byte[(int) file.length()];
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        } finally {
            input.close();
        }
        return data;
    }

    private static void writeAll(File file, byte[] data) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(data);
        } finally {
            output.close();
        }
    }

    private static byte[] bytes(String text) {
        byte[] data = new byte[text.length()];
        int i;
        for (i = 0; i < text.length(); i++) {
            data[i] = (byte) text.charAt(i);
        }
        return data;
    }
}
