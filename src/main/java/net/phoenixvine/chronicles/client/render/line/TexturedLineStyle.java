package net.phoenixvine.chronicles.client.render.line;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class TexturedLineStyle implements IDependencyLineStyle {

    private final ResourceLocation texture;
    private final int textureWidth;
    private final int textureHeight;
    private final float lineWidth;

    public TexturedLineStyle(ResourceLocation texture) {
        this(texture, 16, 16, 6f);
    }

    public TexturedLineStyle(ResourceLocation texture, int textureWidth, int textureHeight, float lineWidth) {
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.lineWidth = lineWidth;
    }

    @Override
    public void render(GuiGraphics g, int px, int py, int cx, int cy, int color, long animTick) {
        float dx = cx - px;
        float dy = cy - py;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;

        double angle = Math.atan2(dy, dx);

        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) angle));

        int drawLen = Math.round(len);
        int drawWidth = Math.round(lineWidth);
        g.blit(texture, 0, -drawWidth / 2, drawLen, drawWidth, 0, 0, textureWidth, textureHeight, textureWidth,
                textureHeight);

        g.pose().popPose();
    }
}
