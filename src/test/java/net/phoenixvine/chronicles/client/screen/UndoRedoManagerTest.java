package net.phoenixvine.chronicles.client.screen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoRedoManagerTest {

    @Test
    void undoRunsTheMostRecentlyPushedActionFirst() {
        List<String> log = new ArrayList<>();
        UndoRedoManager mgr = new UndoRedoManager(log::add);

        mgr.push(() -> log.add("undo-A"), () -> log.add("redo-A"));
        mgr.push(() -> log.add("undo-B"), () -> log.add("redo-B"));
        mgr.undo();
        mgr.undo();

        assertEquals(List.of("undo-B", "undo-A"), log);
    }

    @Test
    void undoOnEmptyStackReportsFeedbackAndDoesNotThrow() {
        List<String> feedback = new ArrayList<>();
        UndoRedoManager mgr = new UndoRedoManager(feedback::add);

        mgr.undo();

        assertEquals(List.of("Nothing to undo"), feedback);
    }

    @Test
    void redoOnEmptyStackReportsFeedback() {
        List<String> feedback = new ArrayList<>();
        UndoRedoManager mgr = new UndoRedoManager(feedback::add);

        mgr.redo();

        assertEquals(List.of("Nothing to redo"), feedback);
    }

    @Test
    void redoReappliesTheChangeAfterUndo() {
        List<String> feedback = new ArrayList<>();
        List<String> log = new ArrayList<>();
        UndoRedoManager mgr = new UndoRedoManager(feedback::add);

        mgr.push(() -> log.add("reverted"), () -> log.add("reapplied"));
        mgr.undo();
        feedback.clear();
        mgr.redo();

        assertEquals(List.of("reverted", "reapplied"), log);
        assertTrue(feedback.isEmpty(), "redo should not report 'Nothing to redo' once something was undone");
    }

    @Test
    void undoRedoCanCycleBackAndForthRepeatedly() {
        List<String> log = new ArrayList<>();
        UndoRedoManager mgr = new UndoRedoManager(msg -> {});

        mgr.push(() -> log.add("undo"), () -> log.add("redo"));
        mgr.undo();
        mgr.redo();
        mgr.undo();
        mgr.redo();

        assertEquals(List.of("undo", "redo", "undo", "redo"), log);
    }

    @Test
    void redoingMultipleUndosReappliesThemInOriginalOrder() {
        List<String> log = new ArrayList<>();
        UndoRedoManager mgr = new UndoRedoManager(msg -> {});

        mgr.push(() -> log.add("undo-A"), () -> log.add("redo-A"));
        mgr.push(() -> log.add("undo-B"), () -> log.add("redo-B"));
        mgr.undo();
        mgr.undo();
        log.clear();
        mgr.redo();
        mgr.redo();

        assertEquals(List.of("redo-A", "redo-B"), log);
    }

    @Test
    void pushingAfterAnUndoClearsTheRedoStack() {
        List<String> feedback = new ArrayList<>();
        List<String> log = new ArrayList<>();
        UndoRedoManager mgr = new UndoRedoManager(feedback::add);

        mgr.push(() -> log.add("undo-A"), () -> log.add("redo-A"));
        mgr.undo();
        mgr.push(() -> log.add("undo-B"), () -> log.add("redo-B"));

        feedback.clear();
        mgr.redo();

        assertEquals(List.of("Nothing to redo"), feedback,
                "a fresh push after undo should discard the old redo history, same as before real redo support existed");
    }

    @Test
    void pushBeyondCapacityEvictsTheOldestEntry() {
        List<Integer> log = new ArrayList<>();
        UndoRedoManager mgr = new UndoRedoManager(msg -> {});

        for (int i = 0; i < 32; i++) {
            int captured = i;
            mgr.push(() -> log.add(captured), () -> {});
        }
        for (int i = 0; i < 30; i++) mgr.undo();

        assertEquals(30, log.size());

        assertEquals(31, log.get(0));
        assertEquals(2, log.get(log.size() - 1));
    }

    @Test
    void undoStackDrainsCompletelyThenReportsEmpty() {
        List<String> feedback = new ArrayList<>();
        UndoRedoManager mgr = new UndoRedoManager(feedback::add);

        mgr.push(() -> {}, () -> {});
        mgr.undo();
        feedback.clear();
        mgr.undo();

        assertTrue(feedback.contains("Nothing to undo"));
    }
}
