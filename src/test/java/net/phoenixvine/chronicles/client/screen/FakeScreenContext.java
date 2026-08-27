package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.screen.utils.ScreenContext;
import net.phoenixvine.chronicles.client.screen.utils.UndoRedoManager;
import net.phoenixvine.chronicles.client.util.BackgroundPictureConfig;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FakeScreenContext implements ScreenContext {

    boolean devMode = true;
    int width = 800;
    int height = 600;
    int sidebarW = 120;
    float posZoom = 1.0f;
    String selectedChapter = "chapterA";
    List<String> chapterList = new ArrayList<>(List.of("ALL", "chapterA", "chapterB"));
    final List<String> feedback = new ArrayList<>();
    final List<Runnable> pushedUndoActions = new ArrayList<>();
    final List<Runnable> pushedRedoActions = new ArrayList<>();
    final List<String> pushedUndoMessages = new ArrayList<>();

    @Override
    public Font font() {
        return null;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public int sidebarW() {
        return sidebarW;
    }

    @Override
    public float posZoom() {
        return posZoom;
    }

    @Override
    public boolean isDevMode() {
        return devMode;
    }

    @Override
    public boolean isRenderingAsBackdrop() {
        return false;
    }

    @Override
    public int scaledNodeSize(QuestNode node) {
        return 32;
    }

    @Override
    public int scaledNodeSize() {
        return 32;
    }

    @Override
    public Map<ResourceLocation, int[]> nodeScreenPos() {
        return new HashMap<>();
    }

    @Override
    public QuestState getState(QuestNode node) {
        return QuestState.UNLOCKED;
    }

    @Override
    public String friendly(String chapterKey) {
        return chapterKey;
    }

    @Override
    public List<String> buildChapterList() {
        return new ArrayList<>(chapterList);
    }

    @Override
    public String selectedChapter() {
        return selectedChapter;
    }

    @Override
    public void setFeedback(String message, Object... args) {
        feedback.add(message.formatted(args));
    }

    @Override
    public void pushUndo(String undoMsg, Runnable revertAction, Runnable redoAction) {
        pushedUndoMessages.add(undoMsg);
        pushedUndoActions.add(revertAction);
        pushedRedoActions.add(redoAction);
    }

    @Override
    public void setFeedbackDone(String doneMsg, Object... args) {
        setFeedback(doneMsg + "  (Ctrl+Z to undo)", args);
    }

    @Override
    public UndoRedoManager undoRedo() {
        return new UndoRedoManager(feedback::add);
    }

    @Override
    public List<String> validationIssues(QuestNode node) {
        return List.of();
    }

    @Override
    public @Nullable BackgroundPictureConfig.Picture pictureEditMode() {
        return null;
    }

    @Override
    public void setPictureEditMode(@Nullable BackgroundPictureConfig.Picture picture) {}

    @Override
    public int colorBorder() {
        return 0;
    }

    @Override
    public int colorSelectAccent() {
        return 0;
    }

    @Override
    public int colorText() {
        return 0;
    }

    @Override
    public int colorTextDim() {
        return 0;
    }

    @Override
    public int colorTextFaint() {
        return 0;
    }

    @Override
    public int colorNodeBorderDone() {
        return 0;
    }
}
