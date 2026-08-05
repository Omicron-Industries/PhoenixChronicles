package net.phoenixvine.chronicles.client.render.line;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

final class LineRenderUtil {

    private LineRenderUtil() {}

    static void drawThickLine(GuiGraphics g, float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;

        float nx = -dy / len * (width / 2f);
        float ny = dx / len * (width / 2f);

        float a = ((argb >>> 24) & 0xFF) / 255f;
        if (a <= 0f) a = 1f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float gc = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        PoseStack.Pose pose = g.pose().last();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(pose.pose(), x1 - nx, y1 - ny, 0).color(r, gc, b, a).endVertex();
        buf.vertex(pose.pose(), x1 + nx, y1 + ny, 0).color(r, gc, b, a).endVertex();
        buf.vertex(pose.pose(), x2 + nx, y2 + ny, 0).color(r, gc, b, a).endVertex();
        buf.vertex(pose.pose(), x2 - nx, y2 - ny, 0).color(r, gc, b, a).endVertex();

        BufferUploader.drawWithShader(buf.end());

        RenderSystem.disableBlend();
    }

    static void drawDot(GuiGraphics g, float cx, float cy, float radius, int argb) {
        drawThickLine(g, cx - 0.01f, cy, cx + 0.01f, cy, radius * 2f, argb);
    }
}
