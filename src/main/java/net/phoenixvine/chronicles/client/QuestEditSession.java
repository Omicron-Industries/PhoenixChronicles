package net.phoenixvine.chronicles.client;

import lombok.Getter;
import lombok.Setter;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import java.util.Stack;

/**
 * Global coordinator for the active quest configuration session.
 * Decouples edit state, tool selections, and historical data from transient UI screens.
 */
public class QuestEditSession {
    // History Tracking
    private static final Stack<Runnable> undoStack = new Stack<>();
    private static final Stack<Runnable> redoStack = new Stack<>();

    // Core Developer Tool States
    @Setter
    @Getter
    private static boolean devMode = false;
    @Getter
    private static boolean testMode = false;
    @Setter
    @Getter
    private static boolean subgraphMode = false;
    @Getter
    private static boolean statsOpen = false;
    @Getter
    private static boolean validationOpen = false;

    // Live Working Graph States
    @Getter
    private static PlayerQuestData testModeData = new PlayerQuestData();
    @Setter
    @Getter
    private static String questClipboard = null;

    // --- Undo / Redo Mechanism ---

    public static void pushUndo(Runnable undoAction) {
        undoStack.push(undoAction);
        redoStack.clear(); // Structural actions invalidate forward redo paths
    }

    public static boolean performUndo() {
        if (!undoStack.isEmpty()) {
            Runnable action = undoStack.pop();
            action.run();
            // Optional: If you track mirror actions, push to redo stack here
            return true;
        }
        return false;
    }

    public static void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    // --- Getters & Setters ---

    public static void setTestMode(boolean active) {
        testMode = active;
        if (!active) {
            testModeData = new PlayerQuestData(); // Clear transient profile state on exit
        }
    }

    public static void setStatsOpen(boolean open) {
        statsOpen = open;
        if (open) validationOpen = false; // Mutually exclusive panels
    }

    public static void setValidationOpen(boolean open) {
        validationOpen = open;
        if (open) statsOpen = false;
    }

    public static void resetTestData() { testModeData = new PlayerQuestData(); }

    public static boolean hasClipboardData() { return questClipboard != null && !questClipboard.isBlank(); }
}