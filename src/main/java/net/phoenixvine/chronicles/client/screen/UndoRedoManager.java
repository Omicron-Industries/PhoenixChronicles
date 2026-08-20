package net.phoenixvine.chronicles.client.screen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

class UndoRedoManager {

    private static final int MAX_UNDO = 30;

    private record Entry(Runnable undo, Runnable redo) {}

    private final Deque<Entry> undoStack = new ArrayDeque<>();
    private final Deque<Entry> redoStack = new ArrayDeque<>();
    private final Consumer<String> feedback;

    UndoRedoManager(Consumer<String> feedback) {
        this.feedback = feedback;
    }

    void push(Runnable undoAction, Runnable redoAction) {
        undoStack.push(new Entry(undoAction, redoAction));
        if (undoStack.size() > MAX_UNDO) undoStack.pollLast();
        redoStack.clear();
    }

    void undo() {
        if (undoStack.isEmpty()) {
            feedback.accept("Nothing to undo");
            return;
        }
        Entry entry = undoStack.pop();
        entry.undo().run();
        redoStack.push(entry);
    }

    void redo() {
        if (redoStack.isEmpty()) {
            feedback.accept("Nothing to redo");
            return;
        }
        Entry entry = redoStack.pop();
        entry.redo().run();
        undoStack.push(entry);
    }
}
