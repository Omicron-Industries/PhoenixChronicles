package net.phoenixvine.chronicles.client.screen;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

interface GraphLayoutState {

    Set<ResourceLocation> hiddenByCollapse();

    boolean hideCompleted();

    @Nullable
    PlayerQuestData playerData();

    boolean isGatedHidden(QuestNode node);

    boolean catMatches(QuestNode node);

    void resetViewOffset();
}
