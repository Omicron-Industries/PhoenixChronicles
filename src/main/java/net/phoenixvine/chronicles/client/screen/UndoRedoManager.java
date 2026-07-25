package net.phoenixvine.chronicles.client.screen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

class UndoRedoManager {

    private static final int MAX_UNDO = 30;

    private final Deque<Runnable> undoStack = new ArrayDeque<>();
    private final Deque<Runnable> redoStack = new ArrayDeque<>();
    private final Consumer<String> feedback;

    UndoRedoManager(Consumer<String> feedback) {
        this.feedback = feedback;
    }

    void push(Runnable action) {
        undoStack.push(action);
        if (undoStack.size() > MAX_UNDO) undoStack.pollLast();
        redoStack.clear();
    }

    void undo() {
        if (undoStack.isEmpty()) {
            feedback.accept("Nothing to undo");
            return;
        }
        Runnable action = undoStack.pop();
        action.run();
    }

    void redo() {
        if (redoStack.isEmpty()) {
            feedback.accept("Nothing to redo");
            return;
        }
        Runnable action = redoStack.pop();
        action.run();
    }
}
