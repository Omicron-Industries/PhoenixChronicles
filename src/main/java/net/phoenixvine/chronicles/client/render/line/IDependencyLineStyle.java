package net.phoenixvine.chronicles.client.render.line;

import net.minecraft.client.gui.GuiGraphics;

public interface IDependencyLineStyle {

    void render(GuiGraphics g, int px, int py, int cx, int cy, int color, long animTick);
}
