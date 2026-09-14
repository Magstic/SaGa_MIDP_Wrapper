package translation;

import doja.tools.font.FontUsage;
import doja.tools.io.FileIO;
import doja.tools.translation.ClassText;
import doja.tools.translation.RawTranslationResource;
import doja.tools.translation.TextOccurrence;
import doja.tools.translation.TranslationTable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Makai Toushi SaGa-specific translation source model. */
final class TranslationSupport {
    private static final String[] CLASS_NAMES = { "f.class", "g.class", "i.class" };
    private static final int TABLE_CHUNK_TEXT = 13;
    private static final int TABLE_CHUNK_INPUT = 14;
    private static final int TABLE_CHUNK_CREDITS = 20;
    private static final int SCRIPT_FIRST_CHUNK = 21;
    private static final int SCRIPT_LAST_CHUNK = 25;
    private static final int EXPECTED_OUTER_OFFSETS = 27;

    private TranslationSupport() {}

    static final class DataText {
        final String sourceKind;
        final String location;
        final String text;
        final byte[] encoded;

        DataText(String sourceKind, String location, String text, byte[] encoded) {
            this.sourceKind = sourceKind;
            this.location = location;
            this.text = text;
            this.encoded = encoded;
        }
    }

    static List<TextOccurrence> extractClassText(File jar) throws IOException {
        return filterClassText(ClassText.extractJar(jar, CLASS_NAMES));
    }

    static List<TextOccurrence> extractClassTextDirectory(File classes) throws IOException {
        return filterClassText(ClassText.extractDirectory(classes, CLASS_NAMES));
    }

    private static List<TextOccurrence> filterClassText(List<TextOccurrence> input) {
        List<TextOccurrence> result = new ArrayList<TextOccurrence>();
        for (int i = 0; i < input.size(); i++) {
            TextOccurrence entry = input.get(i);
            if (containsJapaneseDisplayText(entry.source)) result.add(entry);
        }
        return result;
    }

    private static boolean containsJapaneseDisplayText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x3040 && c <= 0x30ff) || (c >= 0x3400 && c <= 0x9fff)
                    || (c >= 0xf900 && c <= 0xfaff) || (c >= 0xff00 && c <= 0xffef)) {
                return true;
            }
        }
        return false;
    }

    static List<DataText> extractDataText(File assetsDir) throws IOException {
        File tableData = findSingle(assetsDir, "TableData.out");
        byte[] data = FileIO.read(tableData);
        OuterTable outer = OuterTable.read(data, tableData);
        if (outer.offsets.length != EXPECTED_OUTER_OFFSETS) {
            throw new IOException(tableData + ": expected " + EXPECTED_OUTER_OFFSETS
                    + " outer offsets, got " + outer.offsets.length);
        }

        List<DataText> result = new ArrayList<DataText>();
        extractStringChunk(result, outer.chunk(TABLE_CHUNK_TEXT), TABLE_CHUNK_TEXT, 1058);
        extractStringChunk(result, outer.chunk(TABLE_CHUNK_CREDITS), TABLE_CHUNK_CREDITS, 1048);
        for (int chunk = SCRIPT_FIRST_CHUNK; chunk <= SCRIPT_LAST_CHUNK; chunk++) {
            extractScriptChunk(result, outer.chunk(chunk), chunk);
        }
        return result;
    }

    /** Text used by the game UI but intentionally excluded from Translation.tsv. */
    static List<DataText> extractFontOnlyText(File assetsDir) throws IOException {
        File tableData = findSingle(assetsDir, "TableData.out");
        byte[] data = FileIO.read(tableData);
        OuterTable outer = OuterTable.read(data, tableData);
        if (outer.offsets.length != EXPECTED_OUTER_OFFSETS) {
            throw new IOException(tableData + ": expected " + EXPECTED_OUTER_OFFSETS
                    + " outer offsets, got " + outer.offsets.length);
        }
        List<DataText> result = new ArrayList<DataText>();
        extractAllStringChunk(result, outer.chunk(TABLE_CHUNK_INPUT), TABLE_CHUNK_INPUT, 239, "INPUT");
        return result;
    }

    private static void extractStringChunk(List<DataText> output, byte[] chunk, int chunkIndex,
            int expectedRows) throws IOException {
        if (chunk.length < 5) throw new IOException("TableData chunk " + chunkIndex + " is too small");
        int fields = u16be(chunk, 0);
        int rows = u16be(chunk, 2);
        if (fields != 1 || rows != expectedRows || (chunk[4] & 255) != 1) {
            throw new IOException("TableData chunk " + chunkIndex + " string table changed: fields="
                    + fields + ", rows=" + rows + ", type=" + (chunk[4] & 255));
        }
        int p = 5;
        for (int row = 0; row < rows; row++) {
            if (p + 2 > chunk.length) throw new IOException("TableData chunk " + chunkIndex + " truncated");
            int length = u16be(chunk, p);
            p += 2;
            if (p + length > chunk.length) throw new IOException("TableData chunk " + chunkIndex + " bad string length");
            byte[] encoded = copy(chunk, p, length);
            String text = decodeSjis(encoded, 0, encoded.length);
            p += length;
            if (worthExporting(text)) {
                output.add(new DataText("TABLE", "T" + chunkIndex + ":R" + four(row), text, encoded));
            }
        }
        if (p != chunk.length) throw new IOException("TableData chunk " + chunkIndex + " has trailing bytes");
    }

    private static void extractAllStringChunk(List<DataText> output, byte[] chunk, int chunkIndex,
            int expectedRows, String kind) throws IOException {
        if (chunk.length < 5) throw new IOException("TableData chunk " + chunkIndex + " is too small");
        int fields = u16be(chunk, 0);
        int rows = u16be(chunk, 2);
        if (fields != 1 || rows != expectedRows || (chunk[4] & 255) != 1) {
            throw new IOException("TableData chunk " + chunkIndex + " string table changed: fields="
                    + fields + ", rows=" + rows + ", type=" + (chunk[4] & 255));
        }
        int p = 5;
        for (int row = 0; row < rows; row++) {
            if (p + 2 > chunk.length) throw new IOException("TableData chunk " + chunkIndex + " truncated");
            int length = u16be(chunk, p);
            p += 2;
            if (p + length > chunk.length) throw new IOException("TableData chunk " + chunkIndex + " bad string length");
            byte[] encoded = copy(chunk, p, length);
            String text = decodeSjis(encoded, 0, encoded.length);
            p += length;
            if (text.length() != 0) {
                output.add(new DataText(kind, "T" + chunkIndex + ":R" + four(row), text, encoded));
            }
        }
        if (p != chunk.length) throw new IOException("TableData chunk " + chunkIndex + " has trailing bytes");
    }

    private static void extractScriptChunk(List<DataText> output, byte[] chunk, int chunkIndex)
            throws IOException {
        if (chunk.length < 16) throw new IOException("script chunk " + chunkIndex + " is too small");
        int total = le32(chunk, 0);
        int tableStart = le32(chunk, 4);
        int tableBytes = le32(chunk, 8);
        int codeBytes = le32(chunk, 12);
        if (total != chunk.length || tableStart != 16 || tableBytes < 0 || (tableBytes & 3) != 0
                || codeBytes < 0 || tableStart + tableBytes + codeBytes != chunk.length) {
            throw new IOException("script chunk " + chunkIndex + " header changed");
        }

        int methodCount = tableBytes / 4;
        int[] methodIds = new int[methodCount];
        int[] methodOffsets = new int[methodCount];
        int p = tableStart;
        int previousOffset = -1;
        for (int i = 0; i < methodCount; i++) {
            methodIds[i] = u16le(chunk, p);
            methodOffsets[i] = u16le(chunk, p + 2);
            p += 4;
            if (methodOffsets[i] < previousOffset || methodOffsets[i] >= codeBytes) {
                throw new IOException("script chunk " + chunkIndex + " method table changed at " + i);
            }
            previousOffset = methodOffsets[i];
        }

        int codeStart = tableStart + tableBytes;
        int end = codeStart + codeBytes;
        int method = 0;
        p = codeStart;
        while (p < end) {
            int commandOffset = p - codeStart;
            while (method + 1 < methodCount && methodOffsets[method + 1] <= commandOffset) method++;
            int opcode = chunk[p++] & 255;
            if (!knownOpcode(opcode)) {
                throw new IOException("script chunk " + chunkIndex + " unknown opcode " + opcode
                        + " at +" + commandOffset);
            }
            if (opcode == 32) {
                int textStart = p;
                while (p < end && chunk[p] != 0) {
                    if (p + 1 >= end) throw new IOException("script chunk " + chunkIndex + " truncated text");
                    p += 2;
                }
                if (p >= end) throw new IOException("script chunk " + chunkIndex + " unterminated text");
                int length = p - textStart;
                byte[] encoded = copy(chunk, textStart, length);
                String text = decodeSjis(encoded, 0, encoded.length);
                p++;
                if (worthExporting(text)) {
                    output.add(new DataText("SCRIPT", "S" + chunkIndex + ":M"
                            + methodIds[method] + "@" + four(commandOffset), text, encoded));
                }
            } else {
                int parameters = parameterBytes(opcode);
                if (p + parameters > end) throw new IOException("script chunk " + chunkIndex + " truncated command");
                p += parameters;
            }
        }
        if (p != end) throw new IOException("script chunk " + chunkIndex + " parser did not end cleanly");
    }

    private static boolean knownOpcode(int opcode) {
        switch (opcode) {
            case 0: case 1: case 2: case 4: case 12: case 13: case 14:
            case 17: case 18: case 19: case 20: case 21: case 22: case 25:
            case 26: case 32:
                return true;
            default:
                return false;
        }
    }

    private static int parameterBytes(int opcode) {
        switch (opcode) {
            case 1: case 2: case 17: case 18: return 2;
            case 22: case 25: return 1;
            default: return 0;
        }
    }

    private static boolean worthExporting(String text) {
        if (text == null || text.length() == 0) return false;
        boolean visible = false;
        boolean numericOnly = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && c != 0x3000) visible = true;
            if (c < '0' || c > '9') {
                if (!Character.isWhitespace(c) && c != 0x3000) numericOnly = false;
            }
        }
        return visible && !numericOnly;
    }

    static String resolvedText(TranslationTable translations, String source) throws IOException {
        String text = translations.resolve(source);
        validateUnicode(text, source);
        return text;
    }

    static int writeTranslationResource(File file, TranslationTable translations,
            List<DataText> dataText) throws IOException {
        return RawTranslationResource.write(file, runtimeTranslations(translations, dataText));
    }

    static Map<String,String> expectedRuntimeTranslations(TranslationTable translations,
            List<DataText> dataText) throws IOException {
        return RawTranslationResource.expected(runtimeTranslations(translations, dataText));
    }

    static void writeFontUsage(File file, TranslationTable translations, List<TextOccurrence> classText,
            List<DataText> dataText, List<DataText> fontOnlyText) throws IOException {
        FontUsage usage = new FontUsage();
        for (int i = 0; i < classText.size(); i++) {
            usage.addRenderText(resolvedText(translations, classText.get(i).source));
        }
        for (int i = 0; i < dataText.size(); i++) {
            DataText entry = dataText.get(i);
            String rendered = resolvedText(translations, entry.text);
            usage.addRenderText(rendered);
            if (rendered.equals(entry.text)) usage.addShiftJisBytes(entry.encoded);
        }
        for (int i = 0; i < fontOnlyText.size(); i++) {
            DataText entry = fontOnlyText.get(i);
            usage.addRenderText(entry.text);
            usage.addShiftJisBytes(entry.encoded);
        }
        usage.write(file);
    }

    private static List<RawTranslationResource.Entry> runtimeTranslations(
            TranslationTable translations, List<DataText> dataText) throws IOException {
        List<RawTranslationResource.Entry> result = new ArrayList<RawTranslationResource.Entry>();
        for (int i = 0; i < dataText.size(); i++) {
            DataText entry = dataText.get(i);
            String translated = resolvedText(translations, entry.text);
            if (!translated.equals(entry.text)) {
                result.add(new RawTranslationResource.Entry(entry.encoded, translated));
            }
        }
        return result;
    }

    private static void validateUnicode(String text, String label) throws IOException {
        if (text.length() > 65535) throw new IOException("translation is too long for: " + label);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || Character.isSurrogate(c)) {
                throw new IOException("translation contains unsupported character U+"
                        + Integer.toHexString(c).toUpperCase() + " for: " + label);
            }
        }
    }

    private static File findSingle(File root, String name) throws IOException {
        List<File> found = new ArrayList<File>();
        collect(root, name, found);
        if (found.size() != 1) {
            throw new IOException(root + ": expected exactly one " + name + ", got " + found.size());
        }
        return found.get(0);
    }

    private static void collect(File file, String name, List<File> found) {
        if (file == null || !file.exists()) return;
        if (file.isFile()) {
            if (name.equals(file.getName())) found.add(file);
            return;
        }
        File[] children = file.listFiles();
        if (children == null) return;
        for (int i = 0; i < children.length; i++) collect(children[i], name, found);
    }

    private static byte[] copy(byte[] data, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(data, offset, result, 0, length);
        return result;
    }

    private static String decodeSjis(byte[] data, int offset, int length) throws IOException {
        try {
            return new String(data, offset, length, "Windows-31J");
        } catch (java.io.UnsupportedEncodingException ex) {
            return new String(data, offset, length, "Shift_JIS");
        }
    }

    private static String four(int value) {
        String text = String.valueOf(value);
        while (text.length() < 4) text = "0" + text;
        return text;
    }

    private static int u16be(byte[] data, int p) {
        return ((data[p] & 255) << 8) | (data[p + 1] & 255);
    }

    private static int u16le(byte[] data, int p) {
        return (data[p] & 255) | ((data[p + 1] & 255) << 8);
    }

    private static int be32(byte[] data, int p) {
        return ((data[p] & 255) << 24) | ((data[p + 1] & 255) << 16)
                | ((data[p + 2] & 255) << 8) | (data[p + 3] & 255);
    }

    private static int le32(byte[] data, int p) {
        return (data[p] & 255) | ((data[p + 1] & 255) << 8)
                | ((data[p + 2] & 255) << 16) | ((data[p + 3] & 255) << 24);
    }

    private static final class OuterTable {
        final byte[] data;
        final int[] offsets;
        final File source;

        OuterTable(byte[] data, int[] offsets, File source) {
            this.data = data;
            this.offsets = offsets;
            this.source = source;
        }

        static OuterTable read(byte[] data, File source) throws IOException {
            if (data.length < 6) throw new IOException(source + ": too small");
            int count = u16be(data, 0);
            if (count < 2 || 2 + count * 4 > data.length) throw new IOException(source + ": invalid outer table");
            int[] offsets = new int[count];
            int previous = -1;
            for (int i = 0; i < count; i++) {
                int offset = be32(data, 2 + i * 4);
                if (offset < 2 + count * 4 || offset > data.length || offset < previous) {
                    throw new IOException(source + ": invalid outer offset " + i);
                }
                offsets[i] = offset;
                previous = offset;
            }
            if (offsets[count - 1] != data.length) throw new IOException(source + ": final outer offset is not EOF");
            return new OuterTable(data, offsets, source);
        }

        byte[] chunk(int index) throws IOException {
            if (index < 0 || index + 1 >= offsets.length) throw new IOException(source + ": missing chunk " + index);
            int start = offsets[index];
            int end = offsets[index + 1];
            byte[] chunk = new byte[end - start];
            System.arraycopy(data, start, chunk, 0, chunk.length);
            return chunk;
        }
    }
}
