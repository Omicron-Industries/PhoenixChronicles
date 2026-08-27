package net.phoenixvine.chronicles.client.screen.utils;

import net.phoenixvine.chronicles.model.QuestNode;

public interface QuestEditOpsState {

    void rebuild();

    void deleteQuestFiles(QuestNode node);

    void saveNodePrereqsToDisk(QuestNode node);

    void buildLineCache();
}
