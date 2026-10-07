package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.profiler.FrameProfiler;
import net.phoenixvine.chronicles.client.util.BackgroundPictureConfig;
import net.phoenixvine.chronicles.client.util.CustomTextureCache;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

public final class BackgroundPictureRenderer {

    private BackgroundPictureRenderer() {}

    public static void render(GuiGraphics g, int cl, int top, int cr, int bottom,
                              String chapter, float zoom, int viewOffX, int viewOffY) {
        FrameProfiler.begin("background:pictures");
        int drawnCount = 0;

        g.enableScissor(cl, top, cr, bottom);
        for (BackgroundPictureConfig.Picture pic : BackgroundPictureConfig.get(chapter)) {
            int[] rect = screenRect(pic, cl, top, zoom, viewOffX, viewOffY);
            if (pic.texture == null || pic.texture.isBlank()) continue;
            int reach = pic.rotation == 0f ? 0 : (int) Math.ceil(
                    (Math.hypot(rect[2] - rect[0], rect[3] - rect[1]) -
                            Math.max(rect[2] - rect[0], rect[3] - rect[1])) /
                            2.0);
            if (rect[2] + reach < cl || rect[0] - reach > cr || rect[3] + reach < top || rect[1] - reach > bottom) {
                continue;
            }

            ResourceLocation loc;
            try {
                loc = CustomTextureCache.resolve(ResourceLocation.parse(pic.texture));
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
                if (pic.rotation == 0f) {
                    g.blit(loc, rect[0], rect[1], 0, 0, w, h, w, h);
                } else {
                    g.pose().pushPose();
                    g.pose().translate((rect[0] + rect[2]) / 2f, (rect[1] + rect[3]) / 2f, 0f);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(pic.rotation));
                    g.blit(loc, -w / 2, -h / 2, 0, 0, w, h, w, h);
                    g.pose().popPose();
                }
            } finally {

                if (tinted) {
                    g.flush();
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                }
            }
            drawnCount++;
        }
        g.disableScissor();
        FrameProfiler.setCounter("bgPicturesDrawn", drawnCount);
        FrameProfiler.end("background:pictures");
    }

    /** Whether a screen point is on the picture, allowing for its rotation. */
    public static boolean contains(BackgroundPictureConfig.Picture pic, double mx, double my, int cl, int top,
                                   float zoom, int viewOffX, int viewOffY) {
        int[] rect = screenRect(pic, cl, top, zoom, viewOffX, viewOffY);
        double dx = mx - (rect[0] + rect[2]) / 2.0, dy = my - (rect[1] + rect[3]) / 2.0;
        if (pic.rotation != 0f) {
            double theta = Math.toRadians(pic.rotation);
            double cos = Math.cos(theta), sin = Math.sin(theta);
            double lx = dx * cos + dy * sin;
            double ly = -dx * sin + dy * cos;
            dx = lx;
            dy = ly;
        }
        return Math.abs(dx) <= (rect[2] - rect[0]) / 2.0 && Math.abs(dy) <= (rect[3] - rect[1]) / 2.0;
    }

    /** The zoom a picture is drawn at: it follows the canvas zoom only as far as its parallax factor allows. */
    public static float parallaxZoom(BackgroundPictureConfig.Picture pic, float zoom) {
        return 1f + (zoom - 1f) * pic.parallax;
    }

    /** The pan a picture sees: the canvas offset scaled by its parallax factor. */
    public static int parallaxOffset(BackgroundPictureConfig.Picture pic, int viewOff) {
        return Math.round(viewOff * pic.parallax);
    }

    public static int[] screenRect(BackgroundPictureConfig.Picture pic, int cl, int top,
                                   float zoom, int viewOffX, int viewOffY) {
        float pz = parallaxZoom(pic, zoom);
        int cx = (int) (pic.x * pz) + parallaxOffset(pic, viewOffX) + cl;
        int cy = (int) (pic.y * pz) + parallaxOffset(pic, viewOffY) + top;
        int hw = (int) (pic.w * pz / 2f), hh = (int) (pic.h * pz / 2f);
        return new int[] { cx - hw, cy - hh, cx + hw, cy + hh };
    }
}
