package net.phoenixvine.chronicles.client.screen.utils;

import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface NodeCtxMenuState {

    boolean ctxOpen();

    long ctxOpenTimeMs();

    int ctxX();

    int ctxY();

    float ctxScale();

    @Nullable
    QuestNode ctxNode();

    @Nullable
    QuestGroup ctxGroup();

    boolean ctxMoveCatOpen();

    void setCtxMoveCatOpen(boolean open);

    int ctxMoveCatScroll();

    void setCtxMoveCatScroll(int scroll);

    List<ChronicleOverviewScreen.CtxItem> buildCtxItems();

    int menuHeight(List<ChronicleOverviewScreen.CtxItem> items);

    int ctxMoveCatX(int chapterCount);

    int ctxMoveCatYClamped(List<ChronicleOverviewScreen.CtxItem> items, int chapterCount);

    String shortName(QuestNode node, int maxWidth);
}
