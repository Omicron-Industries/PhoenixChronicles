package net.phoenixvine.chronicles.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.screen.utils.GraphEditorState;
import net.phoenixvine.chronicles.client.screen.utils.QuestEditOps;
import net.phoenixvine.chronicles.client.screen.utils.QuestEditOpsState;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestEditOpsTest {

    private final List<QuestNode> registered = new ArrayList<>();

    private QuestNode node(String path, int customX) {
        QuestNode n = new QuestNode(new ResourceLocation("phoenix_chronicles", path),
                Component.literal(path), Component.literal(""));
        n.setCustomPosition(customX, 0);
        QuestTreeRegistry.registerBareQuestNode(n);
        registered.add(n);
        return n;
    }

    @AfterEach
    void clearRegistry() {
        for (QuestNode n : registered) QuestTreeRegistry.removeQuest(n.getId());
        registered.clear();
    }

    private static class RecordingState implements QuestEditOpsState {

        int rebuildCalls = 0;
        int buildLineCacheCalls = 0;
        final List<QuestNode> savedPrereqs = new ArrayList<>();
        final List<QuestNode> deletedFiles = new ArrayList<>();

        @Override
        public void rebuild() {
            rebuildCalls++;
        }

        @Override
        public void deleteQuestFiles(QuestNode node) {
            deletedFiles.add(node);
        }

        @Override
        public void saveNodePrereqsToDisk(QuestNode node) {
            savedPrereqs.add(node);
        }

        @Override
        public void buildLineCache() {
            buildLineCacheCalls++;
        }
    }

    @Test
    void chainMultiSelectionWiresLeftToRightInCustomXOrder() {
        QuestNode c = node("c", 20);
        QuestNode a = node("a", 0);
        QuestNode b = node("b", 10);

        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        GraphEditorState editorState = new GraphEditorState();
        editorState.multiSelection.add(c.getId());
        editorState.multiSelection.add(a.getId());
        editorState.multiSelection.add(b.getId());

        new QuestEditOps(ctx, state, editorState).chainMultiSelection();

        assertTrue(b.getPrerequisites().contains(a), "b should gain a as a prerequisite");
        assertTrue(c.getPrerequisites().contains(b), "c should gain b as a prerequisite");
        assertFalse(c.getPrerequisites().contains(a), "c should not be directly linked to a");
        assertEquals(1, state.rebuildCalls);
        assertEquals(1, state.buildLineCacheCalls);
        assertEquals(2, state.savedPrereqs.size());
        assertTrue(ctx.feedback.get(0).contains("Chained 3 quests"));
        assertTrue(ctx.feedback.get(0).contains("2 new link"));
        assertEquals(1, ctx.pushedUndoActions.size());
    }

    @Test
    void chainMultiSelectionUndoRemovesOnlyTheLinksItAdded() {
        QuestNode a = node("a", 0);
        QuestNode b = node("b", 10);
        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        GraphEditorState editorState = new GraphEditorState();
        editorState.multiSelection.add(a.getId());
        editorState.multiSelection.add(b.getId());

        new QuestEditOps(ctx, state, editorState).chainMultiSelection();
        assertTrue(b.getPrerequisites().contains(a));

        ctx.pushedUndoActions.get(0).run();

        assertFalse(b.getPrerequisites().contains(a), "undo should remove the link chainMultiSelection added");
    }

    @Test
    void chainMultiSelectionSkipsAlreadyLinkedPairsAndOmitsUndoWhenNothingNew() {
        QuestNode a = node("a", 0);
        QuestNode b = node("b", 10);
        b.addPrerequisite(a);

        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        GraphEditorState editorState = new GraphEditorState();
        editorState.multiSelection.add(a.getId());
        editorState.multiSelection.add(b.getId());

        new QuestEditOps(ctx, state, editorState).chainMultiSelection();

        assertTrue(ctx.feedback.get(0).contains("0 new link"));
        assertTrue(ctx.pushedUndoActions.isEmpty(), "no undo should be pushed when nothing new was wired");
    }

    @Test
    void chainMultiSelectionWithFewerThanTwoSelectedDoesNothing() {
        QuestNode a = node("a", 0);
        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        GraphEditorState editorState = new GraphEditorState();
        editorState.multiSelection.add(a.getId());

        new QuestEditOps(ctx, state, editorState).chainMultiSelection();

        assertEquals(0, state.rebuildCalls);
        assertTrue(ctx.feedback.get(0).contains("Select 2+"));
    }

    @Test
    void fanFromLeftmostLinksEveryOtherNodeDirectlyToTheLeftmost() {
        QuestNode root = node("root", 0);
        QuestNode b = node("b", 10);
        QuestNode c = node("c", 20);

        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        GraphEditorState editorState = new GraphEditorState();
        editorState.multiSelection.add(c.getId());
        editorState.multiSelection.add(root.getId());
        editorState.multiSelection.add(b.getId());

        new QuestEditOps(ctx, state, editorState).fanFromLeftmost();

        assertTrue(b.getPrerequisites().contains(root));
        assertTrue(c.getPrerequisites().contains(root));
        assertFalse(c.getPrerequisites().contains(b));
        assertTrue(ctx.feedback.get(0).contains("Fanned from 'root' to 2 quest"));
        assertEquals(1, ctx.pushedUndoActions.size());
    }

    @Test
    void fanFromLeftmostUndoRemovesAllAddedLinks() {
        QuestNode root = node("root", 0);
        QuestNode b = node("b", 10);
        QuestNode c = node("c", 20);
        FakeScreenContext ctx = new FakeScreenContext();
        RecordingState state = new RecordingState();
        GraphEditorState editorState = new GraphEditorState();
        editorState.multiSelection.add(root.getId());
        editorState.multiSelection.add(b.getId());
        editorState.multiSelection.add(c.getId());

        new QuestEditOps(ctx, state, editorState).fanFromLeftmost();
        ctx.pushedUndoActions.get(0).run();

        assertFalse(b.getPrerequisites().contains(root));
        assertFalse(c.getPrerequisites().contains(root));
    }
}
