package net.phoenixvine.chronicles.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryDefinitionTest {

    @Test
    void shortConstructorDefaultsThemeFieldsToZeroAndEmpty() {
        CategoryDefinition def = new CategoryDefinition("groundwork", "Groundwork", List.of("MAIN"));

        assertEquals(0, def.color());
        assertEquals("", def.icon());
        assertEquals(0, def.nameColor());
    }

    @Test
    void withThemeReplacesOnlyThemeFieldsKeepingIdentityFields() {
        CategoryDefinition def = new CategoryDefinition("groundwork", "Groundwork", List.of("MAIN"));

        CategoryDefinition themed = def.withTheme(0xFF0000, "minecraft:diamond", 0x00FF00);

        assertEquals("groundwork", themed.id());
        assertEquals("Groundwork", themed.displayName());
        assertEquals(List.of("MAIN"), themed.chapters());
        assertEquals(0xFF0000, themed.color());
        assertEquals("minecraft:diamond", themed.icon());
        assertEquals(0x00FF00, themed.nameColor());
    }

    @Test
    void withThemeTreatsNullIconAsEmptyString() {
        CategoryDefinition def = new CategoryDefinition("groundwork", "Groundwork", List.of());
        CategoryDefinition themed = def.withTheme(1, null, 2);
        assertEquals("", themed.icon());
    }

    @Test
    void effectiveNameColorFallsBackToColorWhenNameColorUnset() {
        CategoryDefinition withoutNameColor = new CategoryDefinition("g", "G", List.of(), 0xABCDEF, "", 0);
        assertEquals(0xABCDEF, withoutNameColor.effectiveNameColor());

        CategoryDefinition withNameColor = new CategoryDefinition("g", "G", List.of(), 0xABCDEF, "", 0x111111);
        assertEquals(0x111111, withNameColor.effectiveNameColor());
    }
}
