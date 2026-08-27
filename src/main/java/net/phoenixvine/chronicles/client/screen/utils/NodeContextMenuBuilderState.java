package net.phoenixvine.chronicles.client.screen.utils;

import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface NodeContextMenuBuilderState {

    void setCtxOpen(boolean open);

    void setCtxOpenTimeMs(long timeMs);

    void setCtxX(int x);

    void setCtxY(int y);

    void setCtxScale(float scale);

    int ctxRawX();

    int ctxRawY();

    void setCtxRawX(int x);

    void setCtxRawY(int y);

    void setCtxNode(@Nullable QuestNode node);

    void setCtxGroup(@Nullable QuestGroup group);

    boolean testMode();

    void setTestMode(boolean testMode);

    void setTestModeData(PlayerQuestData data);

    boolean gridSnapEnabled();

    void setGridSnapEnabled(boolean enabled);

    ChronicleOverviewScreen.GridDisplayMode gridDisplayMode();

    void setGridDisplayMode(ChronicleOverviewScreen.GridDisplayMode mode);

    void setNodeSizeEditMode(@Nullable QuestNode node);

    void setNodeSizeDragAccX(double v);

    void setNodeSizeDragAccY(double v);

    boolean statsPanelOpen();

    void toggleStatsPanel();

    void rebuildSubgraph();

    void rebuild();

    Path groupsConfigPath();

    @Nullable
    QuestNode resolveLinkTarget(QuestNode node);

    void navigateToNode(QuestNode node);

    void toggleSubtreeCollapse(QuestNode node);

    void autoArrangeChapter();

    void questPaste();

    void chainMultiSelection();

    void fanFromLeftmost();

    void questCopy(QuestNode node);

    void duplicateQuest(QuestNode source);

    void createLinkStubAt(int canvasX, int canvasY, QuestNode target);

    void deleteQuestFiles(QuestNode node);

    @Nullable
    PlayerQuestData playerData();

    int viewOffX();

    int viewOffY();

    ChronicleOverviewScreen thisScreen();
}
