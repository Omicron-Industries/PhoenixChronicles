package net.phoenixvine.chronicles.capability.importer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FtbQuestsImporterTest {

    @Test
    void ampersandColorCodesBecomeSectionSigns() {
        assertEquals("§cRed §ltext", FtbQuestsImporter.convertFormatting("&cRed &ltext", false));
    }

    @Test
    void ampersandCodesAreCaseInsensitive() {
        assertEquals("§CRed", FtbQuestsImporter.convertFormatting("&CRed", false));
    }

    @Test
    void hexColorBecomesRichTokenOnlyWhenRichCapable() {
        assertEquals("{#FF00AA}text", FtbQuestsImporter.convertFormatting("&#FF00AAtext", true));
        assertEquals("text", FtbQuestsImporter.convertFormatting("&#FF00AAtext", false));
    }

    @Test
    void nullInputBecomesEmptyString() {
        assertEquals("", FtbQuestsImporter.convertFormatting(null, true));
    }

    @Test
    void plainTextPassesThroughUnchanged() {
        assertEquals("Just plain text.", FtbQuestsImporter.convertFormatting("Just plain text.", true));
    }

    @Test
    void plainRawTextIsFormattingConvertedAndTrimmed() {
        List<String> warnings = new ArrayList<>();
        String result = FtbQuestsImporter.resolveText("  &cHello  ", Map.of(), warnings, false);
        assertEquals("§cHello", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void nullOrBlankRawTextResolvesToEmptyString() {
        List<String> warnings = new ArrayList<>();
        assertEquals("", FtbQuestsImporter.resolveText(null, Map.of(), warnings, false));
        assertEquals("", FtbQuestsImporter.resolveText("   ", Map.of(), warnings, false));
    }

    @Test
    void bareLangKeyResolvesFromMapAndConvertsFormatting() {
        List<String> warnings = new ArrayList<>();
        Map<String, String> lang = Map.of("quest.title.foo", "&aResolved Title");

        String result = FtbQuestsImporter.resolveText("{quest.title.foo}", lang, warnings, false);

        assertEquals("§aResolved Title", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void unresolvedLangKeyRecordsAWarningAndFallsBackToTheRawKeyText() {
        List<String> warnings = new ArrayList<>();

        String result = FtbQuestsImporter.resolveText("{quest.title.missing}", Map.of(), warnings, false);

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("quest.title.missing"));
        assertEquals("{quest.title.missing}", result);
    }

    @Test
    void jsonTextComponentArrayIsFlattenedAndConcatenated() {
        List<String> warnings = new ArrayList<>();
        String json = "[{\"text\":\"Hello \"},{\"text\":\"World\"}]";

        String result = FtbQuestsImporter.resolveText(json, Map.of(), warnings, false);

        assertEquals("Hello World", result);
    }

    @Test
    void jsonTextComponentTranslateKeyResolvesFromLangMapAndWarnsIfMissing() {
        List<String> warnings = new ArrayList<>();
        Map<String, String> lang = Map.of("known.key", "Known Text");

        String resolved = FtbQuestsImporter.resolveText("{\"translate\":\"known.key\"}", lang, warnings, false);
        assertEquals("Known Text", resolved);
        assertTrue(warnings.isEmpty());

        warnings.clear();
        String unresolved = FtbQuestsImporter.resolveText("{\"translate\":\"missing.key\"}", Map.of(), warnings,
                false);
        assertEquals("missing.key", unresolved);
        assertEquals(1, warnings.size());
    }

    @Test
    void malformedJsonLooksLikeJsonButFallsBackToRawTextTreatment() {
        List<String> warnings = new ArrayList<>();

        String result = FtbQuestsImporter.resolveText("{not: \"valid json", Map.of(), warnings, false);
        assertEquals("{not: \"valid json", result);
    }
}
