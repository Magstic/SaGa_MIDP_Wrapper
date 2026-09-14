package translation;

import doja.tools.translation.ClassText;
import doja.tools.translation.ResolvedText;
import doja.tools.translation.ResolvedTextTable;
import doja.tools.translation.TextOccurrence;
import doja.tools.translation.TranslationTable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies class translations and emits the optional Runtime Shift-JIS translation dictionary. */
public final class TranslationApply {
    private TranslationApply() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Usage: TranslationApply <game-classes> <generated-assets> <config.properties> <Translation.tsv> <resolved.tsv>");
        }
        File classes = new File(args[0]);
        File assets = new File(args[1]);
        File config = new File(args[2]);
        File translationFile = new File(args[3]);
        File resolvedFile = new File(args[4]);
        if (!config.isFile()) throw new IllegalArgumentException("missing " + config);

        TranslationTable translations = TranslationTable.read(translationFile);
        List<TranslationSupport.DataText> dataText = TranslationSupport.extractDataText(assets);
        List<TranslationSupport.DataText> fontOnlyText = TranslationSupport.extractFontOnlyText(assets);
        List<ResolvedText> resolved = new ArrayList<ResolvedText>();
        List<TextOccurrence> classEntries = TranslationSupport.extractClassTextDirectory(classes);
        Map<String,Map<Integer,String>> patches = ClassText.newPatchMap();
        for (int i = 0; i < classEntries.size(); i++) {
            TextOccurrence entry = classEntries.get(i);
            String text = TranslationSupport.resolvedText(translations, entry.source);
            resolved.add(new ResolvedText("class", entry.file, entry.location, text));
            if (!text.equals(entry.source)) ClassText.addPatch(patches, entry.file, entry.location, text);
        }
        ClassText.patchDirectory(classes, patches);
        ResolvedTextTable.write(resolvedFile, resolved);

        int runtimeEntries = TranslationSupport.writeTranslationResource(
                new File(assets, "translation.bin"), translations, dataText);
        TranslationSupport.writeFontUsage(new File(assets.getParentFile(), "font-usage.bin"),
                translations, classEntries, dataText, fontOnlyText);
        System.out.println("TranslationApply: " + translations.translatedCount() + " translated source text(s), "
                + classEntries.size() + " class occurrence(s), " + runtimeEntries
                + " raw Runtime mapping(s); font usage emitted");
    }
}
