package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.gui.screens.Screen;
import net.phoenixvine.chronicles.model.QuestNode;

public interface BulkOpsPanelState {

    void rebuild();

    void saveNodeShapeToDisk(QuestNode node, String shape);

    void saveNodeShapeTextureToDisk(QuestNode node);

    void saveNodeChapterToDisk(QuestNode node, String chapter);

    void deleteQuestFiles(QuestNode node);

    int colorBorderLit();

    Screen thisScreen();
}
