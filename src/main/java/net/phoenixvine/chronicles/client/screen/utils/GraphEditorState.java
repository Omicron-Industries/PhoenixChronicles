package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class GraphEditorState {

    @Nullable
   public QuestNode selectedNode = null;

  public   final Set<ResourceLocation> multiSelection = new LinkedHashSet<>();

    @Nullable
   public QuestNode draggedNode = null;

    @Nullable
   public QuestGroup draggedGroup = null;

    @Nullable
  public   QuestNode lastMovedNode = null;

    @Nullable
   public Map<ResourceLocation, int[]> bulkDragOrigPositions = null;

    @Nullable
   public String questClipboard = null;

   public boolean subgraphMode = false;
}
