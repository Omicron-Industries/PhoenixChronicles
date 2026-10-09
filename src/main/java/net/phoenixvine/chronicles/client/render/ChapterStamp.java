package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.mojang.math.Axis;

public final class ChapterStamp {

    public static final int W = 104;
    public static final int H = 30;
    private static final long ANIM_MS = 450;

    private ChapterStamp() {}

    public static void draw(GuiGraphics g, Font font, int cx, int cy, long sinceMs, int color) {
        float t = sinceMs <= 0 ? 1f : Math.min(1f, (System.currentTimeMillis() - sinceMs) / (float) ANIM_MS);
        float eased = 1f - (1f - t) * (1f - t);
        float scale = 1f + (1f - eased) * 0.8f;
        int alpha = Math.round(0xC8 * eased);
        if (alpha <= 4) return;

        int rgb = color & 0x00FFFFFF;
        int line = (alpha << 24) | rgb;
        int backing = (Math.round(0xF2 * eased) << 24) | 0x0A0A10;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 400f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-11f));
        g.pose().scale(scale, scale, 1f);
        int x0 = -W / 2, y0 = -H / 2;

        g.fill(x0, y0, x0 + W, y0 + H, backing);
        border(g, x0, y0, W, H, line);
        border(g, x0 + 3, y0 + 3, W - 6, H - 6, line);

        String top = "CHAPTER";
        String bottom = "COMPLETE";
        g.pose().pushPose();
        g.pose().translate(0f, y0 + 6f, 0f);
        g.pose().scale(0.7f, 0.7f, 1f);
        g.drawString(font, top, -font.width(top) / 2, 0, line, false);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0f, y0 + 14f, 0f);
        g.drawString(font, bottom, -font.width(bottom) / 2, 0, line, false);
        g.pose().popPose();

        g.pose().popPose();
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
