package net.phoenixvine.chronicles.client;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChapterConfigJsonTest {

    @Test
    void roundTripsThroughJsonPreservingAllFields() {
        ChapterConfig cfg = new ChapterConfig();
        cfg.setStyle(ChapterConfig.BgStyle.HEX_GRID);
        cfg.setColor(0xFF00AA);
        cfg.setNameColor(0x00FF00);
        cfg.setTexture("phoenix_chronicles:textures/gui/bg.png");
        cfg.setDisplayName("Groundwork");
        cfg.setIcon("minecraft:diamond");
        cfg.setParentChapter("main");

        ChapterConfig roundTripped = ChapterConfig.fromJson(cfg.toJson());

        assertEquals(ChapterConfig.BgStyle.HEX_GRID, roundTripped.getStyle());
        assertEquals(0xFF00AA, roundTripped.getColor());
        assertEquals(0x00FF00, roundTripped.getNameColor());
        assertEquals("phoenix_chronicles:textures/gui/bg.png", roundTripped.getTexture());
        assertEquals("Groundwork", roundTripped.getDisplayName());
        assertEquals("minecraft:diamond", roundTripped.getIcon());
        assertEquals("MAIN", roundTripped.getParentChapter());
    }

    @Test
    void toJsonOmitsZeroOrEmptyFieldsToKeepFilesCompact() {
        ChapterConfig cfg = new ChapterConfig();
        JsonObject json = cfg.toJson();

        assertFalse(json.has("color"));
        assertFalse(json.has("name_color"));
        assertFalse(json.has("texture"));
        assertFalse(json.has("display_name"));
        assertFalse(json.has("icon"));
        assertFalse(json.has("parent"));
        assertEquals("DOT_GRID", json.get("style").getAsString());
    }

    @Test
    void colorRoundTripsThroughHexStringFormat() {
        ChapterConfig cfg = new ChapterConfig();
        cfg.setColor(0x123456);

        assertEquals("#123456", cfg.toJson().get("color").getAsString());
    }

    @Test
    void fromJsonIgnoresUnknownOrMalformedStyleRatherThanThrowing() {
        JsonObject json = new JsonObject();
        json.addProperty("style", "NOT_A_REAL_STYLE");

        ChapterConfig cfg = ChapterConfig.fromJson(json);

        assertEquals(ChapterConfig.BgStyle.DOT_GRID, cfg.getStyle());
    }

    @Test
    void fromJsonIgnoresMalformedColorHexRatherThanThrowing() {
        JsonObject json = new JsonObject();
        json.addProperty("color", "not-hex");

        ChapterConfig cfg = ChapterConfig.fromJson(json);

        assertEquals(0, cfg.getColor());
    }

    @Test
    void effectiveNameColorFallsBackToBaseColorWhenUnset() {
        ChapterConfig cfg = new ChapterConfig();
        cfg.setColor(0xABCDEF);

        assertEquals(0xABCDEF, cfg.getEffectiveNameColor());

        cfg.setNameColor(0x111111);
        assertEquals(0x111111, cfg.getEffectiveNameColor());
    }

    @Test
    void setParentChapterNormalizesToTrimmedUppercase() {
        ChapterConfig cfg = new ChapterConfig();
        cfg.setParentChapter("  main_hub  ");
        assertEquals("MAIN_HUB", cfg.getParentChapter());

        cfg.setParentChapter(null);
        assertEquals("", cfg.getParentChapter());
    }
}
