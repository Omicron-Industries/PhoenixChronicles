package net.phoenixvine.chronicles.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.client.screen.utils.GraphEditorState;
import net.phoenixvine.chronicles.client.screen.utils.NodeContextMenuBuilder;
import net.phoenixvine.chronicles.client.screen.utils.NodeContextMenuBuilderState;
import net.phoenixvine.chronicles.client.screen.utils.NodeCtxMenuState;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeContextMenuBuilderTest {

    private final GraphEditorState editorState = new GraphEditorState();

    private static class FakeCtxMenuState implements NodeCtxMenuState {

        boolean open = false;
        long openTimeMs = 0;
        int x = 100, y = 100;
        @Nullable
        QuestNode node = null;
        @Nullable
        QuestGroup group = null;
        boolean moveCatOpen = false;
        int moveCatScroll = 0;

        @Override
        public boolean ctxOpen() {
            return open;
        }

        @Override
        public long ctxOpenTimeMs() {
            return openTimeMs;
        }

        @Override
        public int ctxX() {
            return x;
        }

        @Override
        public int ctxY() {
            return y;
        }

        @Override
        public float ctxScale() {
            return 1f;
        }

        @Override
        public @Nullable QuestNode ctxNode() {
            return node;
        }

        @Override
        public @Nullable QuestGroup ctxGroup() {
            return group;
        }

        @Override
        public boolean ctxMoveCatOpen() {
            return moveCatOpen;
        }

        @Override
        public void setCtxMoveCatOpen(boolean open) {
            moveCatOpen = open;
        }

        @Override
        public int ctxMoveCatScroll() {
            return moveCatScroll;
        }

        @Override
        public void setCtxMoveCatScroll(int scroll) {
            moveCatScroll = scroll;
        }

        @Override
        public List<ChronicleOverviewScreen.CtxItem> buildCtxItems() {
            throw new UnsupportedOperationException("NodeContextMenuBuilder must not call back through ctxState");
        }

        @Override
        public int menuHeight(List<ChronicleOverviewScreen.CtxItem> items) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int ctxMoveCatX(int chapterCount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int ctxMoveCatYClamped(List<ChronicleOverviewScreen.CtxItem> items, int chapterCount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String shortName(QuestNode node, int maxWidth) {
            return node.getId().getPath();
        }
    }

    private static class FakeBuilderState implements NodeContextMenuBuilderState {

        boolean testMode = false;
        boolean gridSnapEnabled = true;
        ChronicleOverviewScreen.GridDisplayMode gridDisplayMode = ChronicleOverviewScreen.GridDisplayMode.ON_DRAG;
        boolean statsPanelOpen = false;

        @Override
        public void setCtxOpen(boolean open) {}

        @Override
        public void setCtxOpenTimeMs(long timeMs) {}

        @Override
        public void setCtxX(int x) {}

        @Override
        public void setCtxY(int y) {}

        @Override
        public void setCtxScale(float scale) {}

        @Override
        public int ctxRawX() {
            return 0;
        }

        @Override
        public int ctxRawY() {
            return 0;
        }

        @Override
        public void setCtxRawX(int x) {}

        @Override
        public void setCtxRawY(int y) {}

        @Override
        public void setCtxNode(@Nullable QuestNode node) {}

        @Override
        public void setCtxGroup(@Nullable QuestGroup group) {}

        @Override
        public boolean testMode() {
            return testMode;
        }

        @Override
        public void setTestMode(boolean testMode) {
            this.testMode = testMode;
        }

        @Override
        public void setTestModeData(PlayerQuestData data) {}

        @Override
        public boolean gridSnapEnabled() {
            return gridSnapEnabled;
        }

        @Override
        public void setGridSnapEnabled(boolean enabled) {
            gridSnapEnabled = enabled;
        }

        @Override
        public ChronicleOverviewScreen.GridDisplayMode gridDisplayMode() {
            return gridDisplayMode;
        }

        @Override
        public void setGridDisplayMode(ChronicleOverviewScreen.GridDisplayMode mode) {
            gridDisplayMode = mode;
        }

        @Override
        public void setNodeSizeEditMode(@Nullable QuestNode node) {}

        @Override
        public void setNodeSizeDragAccX(double v) {}

        @Override
        public void setNodeSizeDragAccY(double v) {}

        @Override
        public boolean statsPanelOpen() {
            return statsPanelOpen;
        }

        @Override
        public void toggleStatsPanel() {
            statsPanelOpen = !statsPanelOpen;
        }

        @Override
        public void rebuildSubgraph() {}

        @Override
        public void rebuild() {}

        @Override
        public Path groupsConfigPath() {
            return Path.of(".");
        }

        @Override
        public @Nullable QuestNode resolveLinkTarget(QuestNode node) {
            return null;
        }

        @Override
        public void navigateToNode(QuestNode node) {}

        @Override
        public void toggleSubtreeCollapse(QuestNode node) {}

        @Override
        public void autoArrangeChapter() {}

        @Override
        public void questPaste() {}

        @Override
        public void chainMultiSelection() {}

        @Override
        public void fanFromLeftmost() {}

        @Override
        public void questCopy(QuestNode node) {}

        @Override
        public void duplicateQuest(QuestNode source) {}

        @Override
        public void createLinkStubAt(int canvasX, int canvasY, QuestNode target) {}

        @Override
        public void deleteQuestFiles(QuestNode node) {}

        @Override
        public @Nullable PlayerQuestData playerData() {
            return null;
        }

        @Override
        public int viewOffX() {
            return 0;
        }

        @Override
        public int viewOffY() {
            return 0;
        }

        @Override
        public ChronicleOverviewScreen thisScreen() {
            return null;
        }
    }

    private static QuestNode node(String path) {
        return new QuestNode(new ResourceLocation("phoenix_chronicles", path),
                Component.literal(path), Component.literal(""));
    }

    private List<String> labels(List<ChronicleOverviewScreen.CtxItem> items) {
        return items.stream().map(ChronicleOverviewScreen.CtxItem::label).toList();
    }

    private boolean anyLabelContains(List<ChronicleOverviewScreen.CtxItem> items, String needle) {
        return items.stream().anyMatch(i -> i.label().contains(needle));
    }

    @AfterEach
    void resetCollapsedRoots() {
        ChronicleOverviewScreen.collapsedSubtreeRoots.clear();
    }

    @Test
    void emptyCanvasDevModeListsCreationAndDevToggleItemsButNoNodeSpecificOnes() {
        FakeScreenContext ctx = new FakeScreenContext();
        FakeCtxMenuState ctxState = new FakeCtxMenuState();
        FakeBuilderState state = new FakeBuilderState();
        var items = new NodeContextMenuBuilder(ctx, ctxState, state, editorState).buildCtxItems();

        assertTrue(anyLabelContains(items, "New quest"));
        assertTrue(anyLabelContains(items, "Link quest here"));
        assertTrue(anyLabelContains(items, "Dependency Lines"));
        assertTrue(anyLabelContains(items, "Paste quest"));
        assertTrue(anyLabelContains(items, "Enter Player Mode"));
        assertTrue(anyLabelContains(items, "Subgraph mode"));
        assertTrue(anyLabelContains(items, "Show stats"));
        assertTrue(anyLabelContains(items, "Grid Snap"));
        assertTrue(anyLabelContains(items, "New group here"));
        assertTrue(anyLabelContains(items, "Auto-arrange chapter"));
        assertFalse(anyLabelContains(items, "Edit Quest"), "no node is selected, so node-only items must be absent");
        assertFalse(anyLabelContains(items, "Chain selected"), "fewer than 2 selected, chain/fan must be absent");
    }

    @Test
    void multiSelectionOfTwoOrMoreAddsChainAndFanItems() {
        editorState.multiSelection.add(new ResourceLocation("phoenix_chronicles", "a"));
        editorState.multiSelection.add(new ResourceLocation("phoenix_chronicles", "b"));
        FakeScreenContext ctx = new FakeScreenContext();
        var items = new NodeContextMenuBuilder(ctx, new FakeCtxMenuState(), new FakeBuilderState(), editorState)
                .buildCtxItems();

        assertTrue(anyLabelContains(items, "Chain selected"));
        assertTrue(anyLabelContains(items, "Fan from leftmost"));
    }

    @Test
    void devModeOnlyTogglesAreHiddenWhenNotInDevMode() {
        FakeScreenContext ctx = new FakeScreenContext();
        ctx.devMode = false;
        var items = new NodeContextMenuBuilder(ctx, new FakeCtxMenuState(), new FakeBuilderState(), editorState)
                .buildCtxItems();

        assertFalse(anyLabelContains(items, "Player Mode"));
        assertFalse(anyLabelContains(items, "Subgraph mode"));
        assertFalse(anyLabelContains(items, "Show stats"));
        assertFalse(anyLabelContains(items, "Grid Snap"));

        assertTrue(anyLabelContains(items, "New quest"));
    }

    @Test
    void nodeSelectedListsNodeSpecificItemsAndHidesCreationItems() {
        FakeCtxMenuState ctxState = new FakeCtxMenuState();
        ctxState.node = node("some_quest");
        FakeScreenContext ctx = new FakeScreenContext();
        var items = new NodeContextMenuBuilder(ctx, ctxState, new FakeBuilderState(), editorState).buildCtxItems();

        assertTrue(anyLabelContains(items, "Edit Quest"));
        assertTrue(anyLabelContains(items, "Edit Texts"));
        assertTrue(anyLabelContains(items, "Design Pop-Up"));
        assertTrue(anyLabelContains(items, "Set Icon"));
        assertTrue(anyLabelContains(items, "Resize"));
        assertTrue(anyLabelContains(items, "Move to Chapter"));
        assertTrue(anyLabelContains(items, "Copy Quest"));
        assertTrue(anyLabelContains(items, "Duplicate Quest"));
        assertTrue(anyLabelContains(items, "Force Complete Quest"));
        assertTrue(anyLabelContains(items, "Reset Progress"));
        assertTrue(anyLabelContains(items, "Delete Quest"));
        assertFalse(anyLabelContains(items, "New quest"), "node is selected, so canvas-creation items must be absent");
        assertFalse(anyLabelContains(items, "New group here"));
    }

    @Test
    void nodeInTestModeHidesEditItemsButKeepsDependencyLines() {
        FakeCtxMenuState ctxState = new FakeCtxMenuState();
        ctxState.node = node("some_quest");
        FakeBuilderState state = new FakeBuilderState();
        state.testMode = true;
        FakeScreenContext ctx = new FakeScreenContext();
        var items = new NodeContextMenuBuilder(ctx, ctxState, state, editorState).buildCtxItems();

        assertFalse(anyLabelContains(items, "Edit Quest"));
        assertFalse(anyLabelContains(items, "Delete Quest"));
        assertTrue(anyLabelContains(items, "Dependency Lines"), "Dependency Lines isn't gated on canEdit");
    }

    @Test
    void nodeWithChildrenOffersCollapseUnlessAlreadyCollapsed() {
        QuestNode parent = node("parent");
        QuestNode child = node("child");
        parent.addChild(child);
        FakeCtxMenuState ctxState = new FakeCtxMenuState();
        ctxState.node = parent;
        FakeScreenContext ctx = new FakeScreenContext();

        var items = new NodeContextMenuBuilder(ctx, ctxState, new FakeBuilderState(), editorState).buildCtxItems();
        assertTrue(anyLabelContains(items, "Collapse Subtree"));

        ChronicleOverviewScreen.collapsedSubtreeRoots.add(parent.getId());
        var itemsAfterCollapse = new NodeContextMenuBuilder(ctx, ctxState, new FakeBuilderState(), editorState)
                .buildCtxItems();
        assertTrue(anyLabelContains(itemsAfterCollapse, "Expand Subtree"));
    }

    @Test
    void groupSelectedListsGroupItems() {
        FakeCtxMenuState ctxState = new FakeCtxMenuState();
        ctxState.group = new QuestGroup("grp1", "My Group", "chapterA");
        FakeScreenContext ctx = new FakeScreenContext();
        var items = new NodeContextMenuBuilder(ctx, ctxState, new FakeBuilderState(), editorState).buildCtxItems();

        assertTrue(anyLabelContains(items, "Edit group"));
        assertTrue(anyLabelContains(items, "Delete group"));
    }

    @Test
    void menuHeightAccountsForNodeHeaderRowAndSeparators() {
        FakeCtxMenuState ctxState = new FakeCtxMenuState();
        FakeScreenContext ctx = new FakeScreenContext();
        NodeContextMenuBuilder builder = new NodeContextMenuBuilder(ctx, ctxState, new FakeBuilderState(), editorState);
        List<ChronicleOverviewScreen.CtxItem> items = List.of(
                ChronicleOverviewScreen.CtxItem.sep(),
                new ChronicleOverviewScreen.CtxItem("Row 1", "", false, false, () -> {}),
                new ChronicleOverviewScreen.CtxItem("Row 2", "", false, false, () -> {}));

        int withoutNode = builder.menuHeight(items);
        assertEquals(4 + ChronicleOverviewScreen.CTX_SEP + 2 * ChronicleOverviewScreen.CTX_ROW, withoutNode);

        ctxState.node = node("n");
        int withNode = builder.menuHeight(items);
        assertEquals(withoutNode + ChronicleOverviewScreen.CTX_ROW, withNode,
                "a header row for the selected node's name adds one extra CTX_ROW");
    }

    @Test
    void ctxMoveCatXFlipsToTheLeftWhenTheSubmenuWouldOverflowTheScreen() {
        FakeCtxMenuState ctxState = new FakeCtxMenuState();
        FakeScreenContext ctx = new FakeScreenContext();
        ctx.width = 300;
        ctxState.x = 280;
        NodeContextMenuBuilder builder = new NodeContextMenuBuilder(ctx, ctxState, new FakeBuilderState(), editorState);

        int x = builder.ctxMoveCatX(3);
        assertTrue(x < ctxState.x, "submenu should flip to the left of the menu when it would overflow to the right");
    }

    @Test
    void ctxMoveCatXStaysToTheRightWhenThereIsRoom() {
        FakeCtxMenuState ctxState = new FakeCtxMenuState();
        FakeScreenContext ctx = new FakeScreenContext();
        ctx.width = 2000;
        ctxState.x = 100;
        NodeContextMenuBuilder builder = new NodeContextMenuBuilder(ctx, ctxState, new FakeBuilderState(), editorState);

        int x = builder.ctxMoveCatX(3);
        assertEquals(ctxState.x + ChronicleOverviewScreen.CTX_W + 2, x);
    }
}
