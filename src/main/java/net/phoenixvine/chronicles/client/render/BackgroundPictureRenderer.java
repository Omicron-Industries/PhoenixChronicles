package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.BackgroundPictureConfig;
import net.phoenixvine.chronicles.client.CustomTextureCache;
import net.phoenixvine.chronicles.client.FrameProfiler;

import com.mojang.blaze3d.systems.RenderSystem;

public final class BackgroundPictureRenderer {

    private BackgroundPictureRenderer() {}

    public static void render(GuiGraphics g, int cl, int top, int cr, int bottom,
                              String chapter, float zoom, int viewOffX, int viewOffY) {
        FrameProfiler.begin("background:pictures");
        int drawnCount = 0;
        for (BackgroundPictureConfig.Picture pic : BackgroundPictureConfig.get(chapter)) {
            int[] rect = screenRect(pic, cl, top, zoom, viewOffX, viewOffY);
            if (rect[2] < cl || rect[0] > cr || rect[3] < top || rect[1] > bottom) continue;
            if (pic.texture == null || pic.texture.isBlank()) continue;

            ResourceLocation loc;
            try {
                loc = CustomTextureCache.resolve(new ResourceLocation(pic.texture));
            } catch (Exception ignored) {
                continue;
            }

            int w = rect[2] - rect[0], h = rect[3] - rect[1];
            if (w <= 0 || h <= 0) continue;

            float tr = ((pic.color >> 16) & 0xFF) / 255f;
            float tg = ((pic.color >> 8) & 0xFF) / 255f;
            float tb = (pic.color & 0xFF) / 255f;
            boolean tinted = pic.opacity < 0.999f || tr < 0.999f || tg < 0.999f || tb < 0.999f;
            if (tinted) {
                g.flush();
                RenderSystem.setShaderColor(tr, tg, tb, Math.max(0f, Math.min(1f, pic.opacity)));
            }
            try {
                g.blit(loc, rect[0], rect[1], 0, 0, w, h, w, h);
            } finally {

                if (tinted) {
                    g.flush();
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                }
            }
            drawnCount++;
        }
        FrameProfiler.setCounter("bgPicturesDrawn", drawnCount);
        FrameProfiler.end("background:pictures");
    }

    public static int[] screenRect(BackgroundPictureConfig.Picture pic, int cl, int top,
                                   float zoom, int viewOffX, int viewOffY) {
        int cx = (int) (pic.x * zoom) + viewOffX + cl;
        int cy = (int) (pic.y * zoom) + viewOffY + top;
        int hw = (int) (pic.w * zoom / 2f), hh = (int) (pic.h * zoom / 2f);
        return new int[] { cx - hw, cy - hh, cx + hw, cy + hh };
    }
}
