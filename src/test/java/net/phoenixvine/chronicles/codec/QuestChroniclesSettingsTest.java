package net.phoenixvine.chronicles.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestChroniclesSettingsTest {

    @Test
    void freshInstanceUsesHardcodedDefaults() {
        QuestChroniclesSettings s = new QuestChroniclesSettings();

        assertTrue(s.isShowToasts());
        assertFalse(s.isReduceMotion());
        assertTrue(s.isShowLineArrows());
        assertEquals(8, s.getDefaultGridSnap());
    }

    @Test
    void explicitOverrideWinsOverHardcodedDefault() {
        QuestChroniclesSettings s = new QuestChroniclesSettings();
        s.setShowToasts(false);
        assertFalse(s.isShowToasts());
    }

    @Test
    void defaultGridSnapTreatsZeroOrNegativeStoredValueAsEightNotLiterallyZero() {
        QuestChroniclesSettings s = new QuestChroniclesSettings();

        s.setDefaultGridSnap(-100);
        assertEquals(1, s.getDefaultGridSnap());
    }

    @Test
    void textScaleMultiplierMatchesEachEnumValue() {
        QuestChroniclesSettings s = new QuestChroniclesSettings();

        s.setTextScale(QuestChroniclesSettings.TextScale.SMALL);
        assertEquals(0.85f, s.getTextScaleMultiplier());

        s.setTextScale(QuestChroniclesSettings.TextScale.NORMAL);
        assertEquals(1.0f, s.getTextScaleMultiplier());

        s.setTextScale(QuestChroniclesSettings.TextScale.LARGE);
        assertEquals(1.2f, s.getTextScaleMultiplier());
    }

    @Test
    void marginMultiplierDependsOnDensity() {
        QuestChroniclesSettings s = new QuestChroniclesSettings();

        s.setDensity(QuestChroniclesSettings.Density.COMPACT);
        assertEquals(8, s.getMarginMultiplier());

        s.setDensity(QuestChroniclesSettings.Density.SPACIOUS);
        assertEquals(12, s.getMarginMultiplier());
    }

    @Test
    void cascadeHiddenQuestsDefaultsToFalse() {
        QuestChroniclesSettings s = new QuestChroniclesSettings();
        assertFalse(s.isCascadeHiddenQuests());

        s.setCascadeHiddenQuests(true);
        assertTrue(s.isCascadeHiddenQuests());
    }
}
