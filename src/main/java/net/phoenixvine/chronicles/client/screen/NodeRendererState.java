package net.phoenixvine.chronicles.client.screen;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestTask;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

interface NodeRendererState {

    Map<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> nodeButtons();

    int viewOffX();

    int viewOffY();

    Set<ResourceLocation> subgraphNodes();

    boolean testMode();

    @Nullable
    PlayerQuestData testModeData();

    @Nullable
    PlayerQuestData playerData();

    String shortLabel(QuestNode node);

    @Nullable
    QuestNode resolveLinkTarget(QuestNode node);

    boolean isTaskDone(QuestTask task);

    QuestTask fallbackTaskIconTask(QuestNode node);

    QuestTask matchingIconTask(QuestNode node, net.minecraft.world.item.Item icon);

    net.minecraft.world.item.ItemStack nbtAwareIconStack(QuestTask task, net.minecraft.world.item.Item icon);

    net.minecraft.world.item.ItemStack cachedIconStack(net.minecraft.world.item.Item icon);

    int colorNodeLocked();

    int colorNodeUnlocked();

    int colorNodeActive();

    int colorNodeDone();

    int colorNodeBorderLocked();

    int colorNodeBorderUnlocked();

    int colorNodeBorderActive();

    int colorNodeBorderDev();

    int colorTextDone();

    int colorTextActive();

    int dbgFull3DIconCount();

    void setDbgFull3DIconCount(int count);

    int dbgCustomIconCount();

    void setDbgCustomIconCount(int count);

    int dbgPickedTextureIconCount();

    void setDbgPickedTextureIconCount(int count);

    int dbgFluidIconCount();

    void setDbgFluidIconCount(int count);

    int dbgGlyphIconCount();

    void setDbgGlyphIconCount(int count);

    Map<String, Integer> dbgShapeCounts();

    boolean validationPanelOpen();

    boolean statsPanelOpen();

    boolean minimapOpen();

    int[] minimapBounds(int cr);

    int colorBorderLit();
}
