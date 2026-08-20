package net.phoenixvine.chronicles.model;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.registry.ChapterPrereqDefaults;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestNodeTest {

    @AfterEach
    void clearGlobalDefaults() {
        ChapterPrereqDefaults.clear();
    }

    private static QuestNode node(String path) {
        return new QuestNode(new ResourceLocation("phoenix_chronicles", path),
                Component.literal(path), Component.literal(""));
    }

    private static Function<QuestNode, QuestState> lookup(Map<QuestNode, QuestState> states) {
        return n -> states.getOrDefault(n, QuestState.LOCKED);
    }

    @Test
    void selfGatedHiddenOnlyWhenHiddenOrMysteryAndStillLocked() {
        QuestNode hidden = node("hidden");
        hidden.setVisibility(QuestNode.Visibility.HIDDEN);
        QuestNode mystery = node("mystery");
        mystery.setVisibility(QuestNode.Visibility.MYSTERY);
        QuestNode normal = node("normal");

        Map<QuestNode, QuestState> states = new HashMap<>();
        states.put(hidden, QuestState.LOCKED);
        states.put(mystery, QuestState.LOCKED);
        states.put(normal, QuestState.LOCKED);
        Function<QuestNode, QuestState> stateLookup = lookup(states);

        assertTrue(hidden.isSelfGatedHidden(stateLookup));
        assertTrue(mystery.isSelfGatedHidden(stateLookup));
        assertFalse(normal.isSelfGatedHidden(stateLookup));
    }

    @Test
    void selfGatedHiddenClearsOnceUnlocked() {
        QuestNode hidden = node("hidden");
        hidden.setVisibility(QuestNode.Visibility.HIDDEN);

        Map<QuestNode, QuestState> states = new HashMap<>();
        states.put(hidden, QuestState.UNLOCKED);

        assertFalse(hidden.isSelfGatedHidden(lookup(states)));
    }

    @Test
    void ancestorGatedHiddenCascadesThroughAHiddenLockedPrerequisite() {
        QuestNode gate = node("gate");
        gate.setVisibility(QuestNode.Visibility.HIDDEN);
        QuestNode descendant = node("descendant");
        descendant.addPrerequisite(gate);

        Map<QuestNode, QuestState> states = new HashMap<>();
        states.put(gate, QuestState.LOCKED);

        assertTrue(descendant.isAncestorGatedHidden(lookup(states)));
        assertTrue(descendant.isGatedHidden(lookup(states)));
    }

    @Test
    void ancestorGatedHiddenClearsOnceTheGateIsUnlocked() {
        QuestNode gate = node("gate");
        gate.setVisibility(QuestNode.Visibility.HIDDEN);
        QuestNode descendant = node("descendant");
        descendant.addPrerequisite(gate);

        Map<QuestNode, QuestState> states = new HashMap<>();
        states.put(gate, QuestState.COMPLETED);

        assertFalse(descendant.isAncestorGatedHidden(lookup(states)));
        assertFalse(descendant.isGatedHidden(lookup(states)));
    }

    @Test
    void ancestorGatingCascadesThroughMultipleGenerations() {
        QuestNode gate = node("gate");
        gate.setVisibility(QuestNode.Visibility.HIDDEN);
        QuestNode mid = node("mid");
        mid.addPrerequisite(gate);
        QuestNode leaf = node("leaf");
        leaf.addPrerequisite(mid);

        Map<QuestNode, QuestState> states = new HashMap<>();
        states.put(gate, QuestState.LOCKED);

        assertTrue(leaf.isAncestorGatedHidden(lookup(states)));
    }

    @Test
    void normalVisibilityAncestorDoesNotCascadeHiding() {
        QuestNode ancestor = node("ancestor");
        QuestNode descendant = node("descendant");
        descendant.addPrerequisite(ancestor);

        Map<QuestNode, QuestState> states = new HashMap<>();
        states.put(ancestor, QuestState.LOCKED);

        assertFalse(descendant.isAncestorGatedHidden(lookup(states)));
    }

    @Test
    void prerequisiteCycleDoesNotInfiniteLoop() {
        QuestNode a = node("a");
        QuestNode b = node("b");
        a.addPrerequisite(b);
        b.addPrerequisite(a);

        Map<QuestNode, QuestState> states = new HashMap<>();
        assertDoesNotThrow(() -> a.isAncestorGatedHidden(lookup(states)));
    }

    @Test
    void addPrerequisiteDeduplicates() {
        QuestNode a = node("a");
        QuestNode b = node("b");
        a.addPrerequisite(b);
        a.addPrerequisite(b);
        assertEquals(1, a.getPrerequisites().size());
    }

    @Test
    void addChildDeduplicates() {
        QuestNode parent = node("parent");
        QuestNode child = node("child");
        parent.addChild(child);
        parent.addChild(child);
        assertEquals(1, parent.getChildren().size());
    }

    @Test
    void removePrerequisiteAlsoClearsPerPrereqFlags() {
        QuestNode a = node("a");
        QuestNode b = node("b");
        a.addPrerequisite(b);
        a.setPrereqForbidden(b.getId(), true);
        assertTrue(a.isPrereqForbidden(b.getId()));

        a.removePrerequisite(b);

        assertFalse(a.getPrerequisites().contains(b));
        assertFalse(a.isPrereqForbidden(b.getId()));
    }

    @Test
    void effectiveRequireAllPrerequisitesDefaultsTrueWithNoOverrideOrChapterDefault() {
        QuestNode n = node("n");
        n.setChapter("SOME_CHAPTER");
        assertTrue(n.getEffectiveRequireAllPrerequisites());
    }

    @Test
    void effectiveRequireAllPrerequisitesPrefersExplicitOverChapterDefault() {
        QuestNode n = node("n");
        n.setChapter("SOME_CHAPTER");
        n.setRequireAllPrerequisites(false);
        assertFalse(n.getEffectiveRequireAllPrerequisites());
    }

    @Test
    void effectiveOptionalPrereqMinCountFallsBackToZeroWithNoOverrideOrChapterDefault() {
        QuestNode n = node("n");
        n.setChapter("NO_DEFAULT");
        assertEquals(0, n.getEffectiveOptionalPrereqMinCount());
    }

    @Test
    void effectiveOptionalPrereqMinCountPrefersExplicitOverride() {
        QuestNode n = node("n");
        n.setChapter("SOME_CHAPTER");
        n.setOptionalPrereqMinCount(3);
        assertEquals(3, n.getEffectiveOptionalPrereqMinCount());
    }

    @Test
    void nodePixelSizeFollowsSizeEnumUnlessOverridden() {
        QuestNode n = node("n");
        n.setNodeSize(QuestNode.NodeSize.TINY);
        assertEquals(14, n.getNodePixelSize());
        n.setNodeSize(QuestNode.NodeSize.HUGE);
        assertEquals(64, n.getNodePixelSize());
    }

    @Test
    void sizeOverrideClampsToValidRangeAndTakesPriorityOverEnum() {
        QuestNode n = node("n");
        n.setNodeSize(QuestNode.NodeSize.TINY);
        n.setSizeOverridePx(500);
        assertEquals(200, n.getNodePixelSize());
        n.setSizeOverridePx(-50);
        assertEquals(8, n.getNodePixelSize());
    }

    @Test
    void settingNodeSizeClearsAnyPriorSizeOverride() {
        QuestNode n = node("n");
        n.setSizeOverridePx(100);
        n.setNodeSize(QuestNode.NodeSize.LARGE);
        assertEquals(48, n.getNodePixelSize());
    }

    @Test
    void rewardChoiceCountClampsToAtLeastOne() {
        QuestNode n = node("n");
        n.setRewardChoiceCount(0);
        assertEquals(1, n.getRewardChoiceCount());
        n.setRewardChoiceCount(-5);
        assertEquals(1, n.getRewardChoiceCount());
        n.setRewardChoiceCount(4);
        assertEquals(4, n.getRewardChoiceCount());
    }

    @Test
    void isLinkStubReflectsWhetherALinkTargetIsSet() {
        QuestNode n = node("n");
        assertFalse(n.isLinkStub());
        n.setLinkTarget(new ResourceLocation("phoenix_chronicles", "target"));
        assertTrue(n.isLinkStub());
    }
}
