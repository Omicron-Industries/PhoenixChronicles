package net.phoenixvine.chronicles.client.render.line;

import net.minecraft.client.gui.GuiGraphics;

public class SolidLineStyle implements IDependencyLineStyle {

    private final float width;

    public SolidLineStyle() {
        this(2f);
    }

    public SolidLineStyle(float width) {
        this.width = width;
    }

    @Override
    public void render(GuiGraphics g, int px, int py, int cx, int cy, int color, long animTick) {
        LineRenderUtil.drawThickLine(g, px, py, cx, cy, width, color);
    }
}
