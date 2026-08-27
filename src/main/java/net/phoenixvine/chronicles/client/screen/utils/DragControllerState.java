package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.model.QuestNode;

import java.util.Map;

public interface DragControllerState {

    int viewOffX();

    int viewOffY();

    int gridSnap();

    boolean gridSnapEnabled();

    boolean dragForceSnap();

    Map<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> nodeButtons();

    int dragGrabX();

    int dragGrabY();

    int dragOrigX();

    int dragOrigY();

    void saveNodeToDisk(QuestNode node);

    void rebuild();

    void refreshEdgeEndpointsFor(QuestNode node);
}
