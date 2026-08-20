package net.phoenixvine.chronicles.client.render;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyLineRendererTest {

    private static final ResourceLocation PARENT_ID = new ResourceLocation("phoenix_chronicles", "parent");
    private static final ResourceLocation CHILD_ID = new ResourceLocation("phoenix_chronicles", "child");

    private static int[] edge(int px, int py, int cx, int cy, int lineStyleOrdinal, int horizontalBulge) {
        return new int[] { px, py, cx, cy, 0xFFFFFFFF, 0, lineStyleOrdinal, -1, -1, -1, 0, 0, 0, 0, 1,
                horizontalBulge };
    }

    private static float cubicBezier(float p0, float p1, float p2, float p3, float t) {
        float u = 1 - t;
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
    }

    private static void rebuildOneEdge(DependencyLineRenderer renderer, int[] ln) {
        List<int[]> edges = new ArrayList<>();
        edges.add(ln);
        List<ResourceLocation[]> edgeNodes = new ArrayList<>();
        edgeNodes.add(new ResourceLocation[] { PARENT_ID, CHILD_ID });
        renderer.rebuild(edges, edgeNodes, 1.0f, new QuestChroniclesSettings());
    }

    @Test
    void hitTestFollowsTheRealSplineBulgeNotAStraightLineApproximation() {
        DependencyLineRenderer renderer = new DependencyLineRenderer();

        int[] ln = edge(100, 100, 500, 400, -1, 1);
        rebuildOneEdge(renderer, ln);

        float onCurveX = cubicBezier(100, 300, 300, 500, 0.25f);
        float onCurveY = cubicBezier(100, 100, 400, 400, 0.25f);

        assertTrue(renderer.tryOpenContextMenuAt(Math.round(onCurveX), Math.round(onCurveY), 6),
                "clicking directly on the actual rendered bulge should open the context menu");
        assertTrue(renderer.isContextMenuOpen());
        renderer.closeContextMenu();

        assertFalse(renderer.tryOpenContextMenuAt(200, 175, 6),
                "the straight-line-between-centers point sits well off the actual bulge and should miss");
    }

    @Test
    void perEdgeStraightOverrideHitTestsAgainstTheLiteralStraightLine() {
        DependencyLineRenderer renderer = new DependencyLineRenderer();
        int straightOrdinal = QuestChroniclesSettings.LineStyle.STRAIGHT.ordinal();
        int[] ln = edge(100, 100, 500, 400, straightOrdinal, 1);
        rebuildOneEdge(renderer, ln);

        assertTrue(renderer.tryOpenContextMenuAt(200, 175, 6),
                "straight-style edges should hit-test against the literal straight line between centers");
    }

    @Test
    void missesEntirelyWhenFarFromAnyLine() {
        DependencyLineRenderer renderer = new DependencyLineRenderer();
        int[] ln = edge(100, 100, 500, 400, -1, 1);
        rebuildOneEdge(renderer, ln);

        assertFalse(renderer.tryOpenContextMenuAt(10, 900, 6));
        assertFalse(renderer.isContextMenuOpen());
    }

    @Test
    void openingOnOneEdgeRecordsTheCorrectParentAndChildIds() {
        DependencyLineRenderer renderer = new DependencyLineRenderer();
        int straightOrdinal = QuestChroniclesSettings.LineStyle.STRAIGHT.ordinal();
        int[] ln = edge(100, 100, 500, 400, straightOrdinal, 1);
        rebuildOneEdge(renderer, ln);

        assertTrue(renderer.tryOpenContextMenuAt(300, 250, 6), "midpoint of a straight edge should hit");
        assertTrue(renderer.isContextMenuOpen());
    }

    @AfterEach
    void clearRegistry() {
        QuestTreeRegistry.removeQuest(PARENT_ID);
        QuestTreeRegistry.removeQuest(CHILD_ID);
    }

    @Test
    void removeConnectionPushesUndoThatFullyRestoresTheRelationshipAndFlags() {
        DependencyLineRenderer renderer = new DependencyLineRenderer();
        QuestNode parent = new QuestNode(PARENT_ID, Component.literal("Parent"), Component.literal(""));
        QuestNode child = new QuestNode(CHILD_ID, Component.literal("Child"), Component.literal(""));
        QuestTreeRegistry.registerBareQuestNode(parent);
        QuestTreeRegistry.registerBareQuestNode(child);
        parent.addChild(child);
        child.addPrerequisite(parent);
        child.setPrereqCosmetic(PARENT_ID, true);
        child.setPrereqLink(PARENT_ID, true);

        int straightOrdinal = QuestChroniclesSettings.LineStyle.STRAIGHT.ordinal();
        int[] ln = edge(100, 100, 500, 400, straightOrdinal, 1);
        rebuildOneEdge(renderer, ln);

        assertTrue(renderer.tryOpenContextMenuAt(300, 250, 6));

        List<String> feedback = new ArrayList<>();
        List<String> undoMsgs = new ArrayList<>();
        List<Runnable> undoActions = new ArrayList<>();

        renderer.handleContextMenuClick(310, 256, 2000, 2000, () -> {}, feedback::add, n -> {},
                (msg, undoAction, redoAction) -> {
                    undoMsgs.add(msg);
                    undoActions.add(undoAction);
                });

        assertFalse(child.getPrerequisites().contains(parent), "connection should be removed");
        assertEquals(1, undoActions.size(), "removing a connection should push exactly one undo action");

        undoActions.get(0).run();

        assertTrue(child.getPrerequisites().contains(parent), "undo should restore the prerequisite link");
        assertTrue(parent.getChildren().contains(child), "undo should restore the parent/child relationship too");
        assertTrue(child.isPrereqCosmetic(PARENT_ID), "undo should restore the cosmetic flag");
        assertTrue(child.isPrereqLink(PARENT_ID), "undo should restore the link flag");
    }

    @Test
    void cyclePrereqTypePushesUndoThatRevertsToThePreviousType() {
        DependencyLineRenderer renderer = new DependencyLineRenderer();
        QuestNode parent = new QuestNode(PARENT_ID, Component.literal("Parent"), Component.literal(""));
        QuestNode child = new QuestNode(CHILD_ID, Component.literal("Child"), Component.literal(""));
        QuestTreeRegistry.registerBareQuestNode(parent);
        QuestTreeRegistry.registerBareQuestNode(child);
        parent.addChild(child);
        child.addPrerequisite(parent);

        int straightOrdinal = QuestChroniclesSettings.LineStyle.STRAIGHT.ordinal();
        int[] ln = edge(100, 100, 500, 400, straightOrdinal, 1);
        rebuildOneEdge(renderer, ln);

        assertTrue(renderer.tryOpenContextMenuAt(300, 250, 6));

        List<Runnable> undoActions = new ArrayList<>();

        renderer.handleContextMenuClick(310, 270, 2000, 2000, () -> {}, msg -> {}, n -> {},
                (msg, undoAction, redoAction) -> undoActions.add(undoAction));

        assertFalse(child.isPrereqRequired(PARENT_ID), "cycling from Required should land on Optional");
        assertEquals(1, undoActions.size());

        undoActions.get(0).run();

        assertTrue(child.isPrereqRequired(PARENT_ID), "undo should restore Required");
        assertFalse(child.isPrereqForbidden(PARENT_ID));
    }
}
