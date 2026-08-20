package net.phoenixvine.chronicles.client.screen;

import net.phoenixvine.chronicles.model.QuestNode;

interface QuestEditOpsState {

    void rebuild();

    void deleteQuestFiles(QuestNode node);

    void saveNodePrereqsToDisk(QuestNode node);

    void buildLineCache();
}
