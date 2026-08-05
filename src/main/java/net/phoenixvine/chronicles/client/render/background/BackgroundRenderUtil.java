package net.phoenixvine.chronicles.client.render.background;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class BackgroundRenderUtil {

    private BackgroundRenderUtil() {}

    public static void drawShaderQuad(GuiGraphics g, ShaderInstance shader, ResourceLocation baseTexture,
                                      int x, int y, int size, float timeSeconds) {
        drawShaderQuad(g, shader, baseTexture, x, y, size, timeSeconds, 1f);
    }

    public static void drawShaderQuad(GuiGraphics g, ShaderInstance shader, ResourceLocation baseTexture,
                                      int x, int y, int size, float timeSeconds, float scale) {
        if (shader == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, baseTexture);
        RenderSystem.setShader(() -> shader);

        if (shader.safeGetUniform("Time") != null) {
            shader.safeGetUniform("Time").set(timeSeconds);
        }
        if (shader.safeGetUniform("Scale") != null) {
            shader.safeGetUniform("Scale").set(scale);
        }

        PoseStack.Pose pose = g.pose().last();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(pose.pose(), x, y + size, 0).uv(0, 1).endVertex();
        buf.vertex(pose.pose(), x + size, y + size, 0).uv(1, 1).endVertex();
        buf.vertex(pose.pose(), x + size, y, 0).uv(1, 0).endVertex();
        buf.vertex(pose.pose(), x, y, 0).uv(0, 0).endVertex();

        BufferUploader.drawWithShader(buf.end());

        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.disableBlend();
    }

    public static float wrappedSeconds(long animTick) {
        return (animTick % 3_600_000L) / 1000f;
    }

    public static ResourceLocation maskTextureFor(String shape) {
        String key = shape == null ? "square" : shape.toLowerCase(java.util.Locale.ROOT);
        String file = switch (key) {
            case "circle", "diamond", "hexagon", "triangle", "star", "pentagon", "shield", "cross" -> key;
            default -> "square";
        };
        return new ResourceLocation("phoenix_chronicles", "textures/gui/sprites/" + file + ".png");
    }
}
