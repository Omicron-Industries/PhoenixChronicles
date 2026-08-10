package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.BackgroundPictureConfig;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface ScreenContext {

    Font font();

    int width();

    int height();

    int sidebarW();

    boolean isDevMode();

    boolean isRenderingAsBackdrop();

    int scaledNodeSize(QuestNode node);

    int scaledNodeSize();

    Map<ResourceLocation, int[]> nodeScreenPos();

    QuestState getState(QuestNode node);

    String friendly(String chapterKey);

    List<String> buildChapterList();

    String selectedChapter();

    void setFeedback(String message, Object... args);

    UndoRedoManager undoRedo();

    List<String> validationIssues(QuestNode node);

    @Nullable
    BackgroundPictureConfig.Picture pictureEditMode();

    void setPictureEditMode(@Nullable BackgroundPictureConfig.Picture picture);

    int colorBorder();

    int colorSelectAccent();

    int colorText();

    int colorTextDim();

    int colorTextFaint();

    int colorNodeBorderDone();
}
