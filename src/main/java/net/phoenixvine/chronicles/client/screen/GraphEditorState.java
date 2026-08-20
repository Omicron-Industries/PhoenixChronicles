package net.phoenixvine.chronicles.client.screen;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class GraphEditorState {

    @Nullable
    QuestNode selectedNode = null;

    final Set<ResourceLocation> multiSelection = new LinkedHashSet<>();

    @Nullable
    QuestNode draggedNode = null;

    @Nullable
    QuestGroup draggedGroup = null;

    @Nullable
    QuestNode lastMovedNode = null;

    @Nullable
    Map<ResourceLocation, int[]> bulkDragOrigPositions = null;

    @Nullable
    String questClipboard = null;

    boolean subgraphMode = false;
}
