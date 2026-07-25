package net.phoenixvine.chronicles.client;

import net.phoenixvine.chronicles.capability.PlayerQuestData;

import lombok.Getter;
import lombok.Setter;

import java.util.Stack;

public class QuestEditSession {

    private static final Stack<Runnable> undoStack = new Stack<>();
    private static final Stack<Runnable> redoStack = new Stack<>();

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

    @Getter
    private static PlayerQuestData testModeData = new PlayerQuestData();
    @Setter
    @Getter
    private static String questClipboard = null;

    public static void pushUndo(Runnable undoAction) {
        undoStack.push(undoAction);
        redoStack.clear();
    }

    public static boolean performUndo() {
        if (!undoStack.isEmpty()) {
            Runnable action = undoStack.pop();
            action.run();

            return true;
        }
        return false;
    }

    public static void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    public static void setTestMode(boolean active) {
        testMode = active;
        if (!active) {
            testModeData = new PlayerQuestData();
        }
    }

    public static void setStatsOpen(boolean open) {
        statsOpen = open;
        if (open) validationOpen = false;
    }

    public static void setValidationOpen(boolean open) {
        validationOpen = open;
        if (open) statsOpen = false;
    }

    public static void resetTestData() {
        testModeData = new PlayerQuestData();
    }

    public static boolean hasClipboardData() {
        return questClipboard != null && !questClipboard.isBlank();
    }
}
