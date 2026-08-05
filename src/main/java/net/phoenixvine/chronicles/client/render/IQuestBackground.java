package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.model.QuestNode;

public interface IQuestBackground {

    void render(GuiGraphics g, QuestNode node, int x, int y, int size, long animTick);
}
