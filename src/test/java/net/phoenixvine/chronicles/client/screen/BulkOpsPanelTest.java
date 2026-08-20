package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.model.QuestNode;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkOpsPanelTest {

    private static final int HEADER_H = ChronicleOverviewScreen.HEADER_H;
    private static final int CL = 10;
    private static final int BX = CL + 4;
    private static final int BY = HEADER_H + 4;
    private static final int SLOT_W = 14;
    private static final int START_X = BX + 6;
    private static final int SLOT_Y = BY + 24;
    private static final int SHAPE_COUNT = 10;
    private static final int ACT_X = START_X + SHAPE_COUNT * (SLOT_W + 2) + 8;
    private static final int DEL_X = ACT_X + 62;
    private static final int ROW_H = 13;
    private static final int SIZE_SLOT_Y = SLOT_Y + ROW_H;
    private static final int SIZE_SLOT_W = 50;
    private static final int SIZE_START_X = BX + 6;

    private static class RecordingState implements BulkOpsPanelState {

        int rebuildCalls = 0;
        final List<QuestNode> shapeSaves = new ArrayList<>();
        final List<QuestNode> chapterSaves = new ArrayList<>();
        final List<QuestNode> deletedFiles = new ArrayList<>();

        @Override
        public void rebuild() {
            rebuildCalls++;
        }

        @Override
        public void saveNodeShapeToDisk(QuestNode node, String shape) {
            shapeSaves.add(node);
        }

        @Override
        public void saveNodeShapeTextureToDisk(QuestNode node) {}

        @Override
        public void saveNodeChapterToDisk(QuestNode node, String chapter) {
            chapterSaves.add(node);
        }

        @Override
        public void deleteQuestFiles(QuestNode node) {
            deletedFiles.add(node);
        }

        @Override
        public int colorBorderLit() {
            return 0;
        }

        @Override
        public Screen thisScreen() {
            return null;
        }
    }

    private GraphEditorState twoSelectedEditorState() {
        GraphEditorState editorState = new GraphEditorState();
        editorState.multiSelection.add(new ResourceLocation("phoenix_chronicles", "unregistered_a"));
        editorState.multiSelection.add(new ResourceLocation("phoenix_chronicles", "unregistered_b"));
        return editorState;
    }

    @Test
    void ignoresClicksWhenFewerThanTwoSelected() {
        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        GraphEditorState editorState = new GraphEditorState();
        editorState.multiSelection.add(new ResourceLocation("phoenix_chronicles", "only_one"));
        BulkOpsPanel panel = new BulkOpsPanel(ctx, state, editorState);

        assertFalse(panel.mouseClicked(START_X + 5, SLOT_Y + 5, 0, CL));
        assertEquals(0, state.rebuildCalls);
    }

    @Test
    void ignoresClicksWhenNotInDevMode() {
        FakeScreenContext ctx = new FakeScreenContext();
        ctx.devMode = false;
        RecordingState state = new RecordingState();
        BulkOpsPanel panel = new BulkOpsPanel(ctx, state, twoSelectedEditorState());

        assertFalse(panel.mouseClicked(START_X + 5, SLOT_Y + 5, 0, CL));
    }

    @Test
    void ignoresClicksOutsidePanelBounds() {
        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        BulkOpsPanel panel = new BulkOpsPanel(ctx, state, twoSelectedEditorState());

        assertFalse(panel.mouseClicked(5000, 5000, 0, CL));
        assertEquals(0, state.rebuildCalls);
    }

    @Test
    void shapeSlotClickReportsCorrectShapeAndPushesUndo() {
        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        BulkOpsPanel panel = new BulkOpsPanel(ctx, state, twoSelectedEditorState());

        int sx = START_X + 1 * (SLOT_W + 2) + 5;
        boolean handled = panel.mouseClicked(sx, SLOT_Y + 5, 0, CL);

        assertTrue(handled);
        assertTrue(ctx.feedback.get(0).contains("Shape → CIRCLE for 2 quests"));
        assertEquals(1, state.rebuildCalls);
        assertEquals(1, ctx.pushedUndoActions.size());
        assertTrue(ctx.pushedUndoMessages.get(0).contains("0 quest shapes reverted"),
                "unregistered ids mean no real node was mutated, so 0 shapes should be recorded as reverted");
    }

    @Test
    void sizeSlotClickReportsCorrectSizeWithoutTouchingRealDisk() {
        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        BulkOpsPanel panel = new BulkOpsPanel(ctx, state, twoSelectedEditorState());

        int sx = SIZE_START_X + 2 * (SIZE_SLOT_W + 2) + 5;
        boolean handled = panel.mouseClicked(sx, SIZE_SLOT_Y + 5, 0, CL);

        assertTrue(handled);
        assertTrue(ctx.feedback.get(0).contains("Size → Normal for 2 quests"));
        assertEquals(1, state.rebuildCalls);
    }

    @Test
    void moveCatToggleThenDropdownRowClickMovesSelectionAndClosesDropdown() {
        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        BulkOpsPanel panel = new BulkOpsPanel(ctx, state, twoSelectedEditorState());

        assertTrue(panel.mouseClicked(ACT_X + 5, SLOT_Y + 5, 0, CL));

        int subY = SIZE_SLOT_Y + ROW_H;
        assertTrue(panel.mouseClicked(ACT_X + 5, subY + 2, 0, CL), "first dropdown row should be clickable once open");
        assertTrue(ctx.feedback.get(0).contains("Moved 2 quests to"));
        assertEquals(1, state.rebuildCalls, "only the row click's handler calls rebuild(), not the open-toggle");
    }

    @Test
    void deleteAllClearsSelectionAndPushesRestoreUndoEvenWithNoRealNodes() {
        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        GraphEditorState editorState = twoSelectedEditorState();
        BulkOpsPanel panel = new BulkOpsPanel(ctx, state, editorState);

        boolean handled = panel.mouseClicked(DEL_X + 5, SLOT_Y + 5, 0, CL);

        assertTrue(handled);
        assertTrue(editorState.multiSelection.isEmpty(), "delete-all always clears the selection");
        assertTrue(ctx.feedback.get(0).contains("Deleted 2 quests"));
        assertEquals(1, state.rebuildCalls);
        assertEquals(1, ctx.pushedUndoActions.size());
        assertTrue(state.deletedFiles.isEmpty(),
                "no real nodes were registered, so deleteQuestFiles should never fire");
    }
}
