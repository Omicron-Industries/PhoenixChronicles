package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.CategoryConfig;
import net.phoenixvine.chronicles.client.FrameProfiler;

/**
 * Draws the canvas background tint + pattern (dot grid / grid lines / hex grid / diagonal lines
 * / custom texture) for the currently selected category. Extracted out of ChronicleOverviewScreen
 * - stateless, only needs the current viewport (zoom/pan) and category threaded through.
 */
public final class CanvasBackgroundRenderer {

    private static final int C_DOT = 0x14FFFFFF;

    private CanvasBackgroundRenderer() {}

    public static void drawBackground(GuiGraphics g, int x1, int y1, int x2, int y2,
                                      String selectedCategory, float zoom, int viewOffX, int viewOffY) {
        CategoryConfig cfg = selectedCategory.isEmpty() ? new CategoryConfig() :
                CategoryConfig.getEffective(selectedCategory);
        int tint = cfg.getColor();
        FrameProfiler.begin("background:tint");
        if (tint != 0) g.fill(x1, y1, x2, y2, 0xCC000000 | (tint & 0x00FFFFFF));
        FrameProfiler.end("background:tint");
        FrameProfiler.begin("background:pattern");
        switch (cfg.getStyle()) {
            case DOT_GRID -> drawDotGrid(g, x1, y1, x2, y2, zoom, viewOffX, viewOffY);
            case GRID_LINES -> drawGridLines(g, x1, y1, x2, y2, zoom, viewOffX, viewOffY);
            case HEX_GRID -> drawHexGrid(g, x1, y1, x2, y2, zoom, viewOffX, viewOffY);
            case DIAGONAL_LINES -> drawDiagonalLines(g, x1, y1, x2, y2, zoom, viewOffX, viewOffY);
            case SOLID -> {} // tint fill above is sufficient
            case CUSTOM -> drawCustomBg(g, x1, y1, x2, y2, cfg.getTexture());
        }
        // drawDotGrid queues into NodeShapeRenderer's shared batch instead of drawing each dot
        // immediately (same fix as the node shapes) - flush right here, before anything else
        // queues into it this frame, so the background stays correctly behind everything drawn
        // after it (a no-op if this category isn't using DOT_GRID).
        NodeShapeRenderer.flushFillQueue(g);
        FrameProfiler.end("background:pattern");
    }

    private static void drawDotGrid(GuiGraphics g, int x1, int y1, int x2, int y2,
                                    float zoom, int viewOffX, int viewOffY) {
        // Floor raised from 12 to 22 - each dot is its own g.fill() call, and the floor caps how
        // dense the pattern gets at low zoom (spacing can't shrink below it), not how sparse. At
        // the old floor a full-screen canvas at low zoom was ~11,700 individual fill calls every
        // frame just for the background texture; this cuts that by roughly (22/12)^2 ≈ 3.4x with
        // a barely-perceptible difference in the dot pattern itself.
        int sp = Math.max(22, (int) (18 * zoom));
        int sx = x1 + ((viewOffX % sp + sp) % sp);
        int sy = y1 + ((viewOffY % sp + sp) % sp);
        int drawn = 0;
        for (int x = sx; x < x2; x += sp) {
            for (int y = sy; y < y2; y += sp) {
                NodeShapeRenderer.queueFillRect(g, x, y, x + 1, y + 1, C_DOT);
                drawn++;
            }
        }
        FrameProfiler.setCounter("bgDotsDrawn", drawn);
    }

    private static void drawGridLines(GuiGraphics g, int x1, int y1, int x2, int y2,
                                      float zoom, int viewOffX, int viewOffY) {
        int sp = Math.max(10, (int) (32 * zoom));
        int sx = x1 + ((viewOffX % sp + sp) % sp);
        int sy = y1 + ((viewOffY % sp + sp) % sp);
        for (int x = sx; x < x2; x += sp) g.fill(x, y1, x + 1, y2, 0x18FFFFFF);
        for (int y = sy; y < y2; y += sp) g.fill(x1, y, x2, y + 1, 0x18FFFFFF);
    }

    private static void drawHexGrid(GuiGraphics g, int x1, int y1, int x2, int y2,
                                    float zoom, int viewOffX, int viewOffY) {
        float r = Math.max(10f, 28f * zoom);
        float w = r * 1.732f; // sqrt(3) * r
        float h = r * 2f;
        float offX = viewOffX % (int) w;
        float offY = viewOffY % (int) (h * 0.75f);
        for (float gy = y1 + offY - h; gy < y2 + h; gy += h * 0.75f) {
            boolean odd = ((int) ((gy - y1 - offY + h) / (h * 0.75f)) % 2) == 1;
            float rowOffX = odd ? w / 2 : 0;
            for (float gx = x1 + offX + rowOffX - w; gx < x2 + w; gx += w) {
                drawHexOutline(g, (int) gx, (int) gy, (int) r, 0x1AFFFFFF);
            }
        }
    }

    private static void drawHexOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        int sides = 6;
        int px = 0, py = 0;
        for (int i = 0; i <= sides; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3;
            int nx = cx + (int) (Math.cos(a) * r);
            int ny = cy + (int) (Math.sin(a) * r);
            if (i > 0) NodeShapeRenderer.drawLine(g, px, py, nx, ny, color);
            px = nx;
            py = ny;
        }
    }

    private static void drawDiagonalLines(GuiGraphics g, int x1, int y1, int x2, int y2,
                                          float zoom, int viewOffX, int viewOffY) {
        int sp = Math.max(10, (int) (24 * zoom));
        int total = (x2 - x1) + (y2 - y1);
        int startOff = ((viewOffX + viewOffY) % sp + sp) % sp;
        for (int d = -sp + startOff; d < total + sp; d += sp) {
            int ax = x1 + d, ay = y1;
            int bx = x1, by = y1 + d;
            // Clamp to canvas rect
            int cx0 = Math.max(x1, Math.min(x2, ax));
            int cy0 = ay + (cx0 - ax);
            int cx1 = Math.max(x1, Math.min(x2, bx));
            int cy1 = by + (cx1 - bx);
            if (cy0 < y1) {
                cx0 += y1 - cy0;
                cy0 = y1;
            }
            if (cy1 < y1) {
                cx1 += y1 - cy1;
                cy1 = y1;
            }
            if (cy0 > y2) {
                cx0 -= cy0 - y2;
                cy0 = y2;
            }
            if (cy1 > y2) {
                cx1 -= cy1 - y2;
                cy1 = y2;
            }
            if (cx0 >= x1 && cx0 <= x2 && cx1 >= x1 && cx1 <= x2)
                NodeShapeRenderer.drawLine(g, cx0, cy0, cx1, cy1, 0x18FFFFFF);
        }
    }

    private static void drawCustomBg(GuiGraphics g, int x1, int y1, int x2, int y2, String textureLoc) {
        if (textureLoc == null || textureLoc.isBlank()) return;
        try {
            ResourceLocation loc = net.phoenixvine.chronicles.client.CustomTextureCache.resolve(
                    new ResourceLocation(textureLoc));
            int w = x2 - x1, h = y2 - y1;
            g.blit(loc, x1, y1, 0, 0, w, h, w, h);
        } catch (Exception ignored) {} // malformed RL or missing texture
    }
}
