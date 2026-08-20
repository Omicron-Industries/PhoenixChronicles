package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.ChapterConfig;
import net.phoenixvine.chronicles.client.FrameProfiler;

public final class CanvasBackgroundRenderer {

    private static final int C_DOT = 0x14FFFFFF;

    private CanvasBackgroundRenderer() {}

    private static int getGridStep(int gridSnap, double zoom) {
        double exactSp = gridSnap * zoom;
        if (exactSp <= 0.001) return 1;

        int step = 1;
        while (exactSp * step < 4.0) {
            step *= 2;
        }
        return step;
    }

    public static void drawBackground(GuiGraphics g, int x1, int y1, int x2, int y2,
                                      String selectedChapter, float zoom, int viewOffX, int viewOffY, int gridSnap) {
        ChapterConfig cfg = selectedChapter.isEmpty() ? new ChapterConfig() :
                ChapterConfig.getEffective(selectedChapter);
        int tint = cfg.getColor();
        FrameProfiler.begin("background:tint");
        if (tint != 0) g.fill(x1, y1, x2, y2, 0xCC000000 | (tint & 0x00FFFFFF));
        FrameProfiler.end("background:tint");
        FrameProfiler.begin("background:pattern");

        switch (cfg.getStyle()) {
            case DOT_GRID -> drawDotGrid(g, x1, y1, x2, y2, zoom, viewOffX, viewOffY, gridSnap);
            case GRID_LINES -> drawGridLines(g, x1, y1, x2, y2, zoom, viewOffX, viewOffY, gridSnap);
            case HEX_GRID -> drawHexGrid(g, x1, y1, x2, y2, zoom, viewOffX, viewOffY, gridSnap);
            case DIAGONAL_LINES -> drawDiagonalLines(g, x1, y1, x2, y2, zoom, viewOffX, viewOffY, gridSnap);
            case SOLID -> {}
            case CUSTOM -> drawCustomBg(g, x1, y1, x2, y2, cfg.getTexture());
        }

        NodeShapeRenderer.flushFillQueue(g);
        FrameProfiler.end("background:pattern");
    }

    private static void drawDotGrid(GuiGraphics g, int x1, int y1, int x2, int y2,
                                    float zoom, int viewOffX, int viewOffY, int gridSnap) {
        int step = getGridStep(gridSnap, zoom);
        int worldSnap = gridSnap * step;

        long startWorldX = Math.floorDiv((long) Math.floor(-viewOffX / (double) zoom), worldSnap) * worldSnap;
        long endWorldX = (Math.floorDiv((long) Math.ceil((x2 - x1 - viewOffX) / (double) zoom), worldSnap) + 1) *
                worldSnap;

        long startWorldY = Math.floorDiv((long) Math.floor(-viewOffY / (double) zoom), worldSnap) * worldSnap;
        long endWorldY = (Math.floorDiv((long) Math.ceil((y2 - y1 - viewOffY) / (double) zoom), worldSnap) + 1) *
                worldSnap;

        int drawn = 0;

        for (long worldX = startWorldX; worldX <= endWorldX; worldX += worldSnap) {
            int x = (int) (x1 + viewOffX + worldX * zoom);
            if (x < x1 || x >= x2) continue;

            for (long worldY = startWorldY; worldY <= endWorldY; worldY += worldSnap) {
                int y = (int) (y1 + viewOffY + worldY * zoom);
                if (y < y1 || y >= y2) continue;

                NodeShapeRenderer.queueFillRect(g, x, y, x + 1, y + 1, C_DOT);
                drawn++;
            }
        }
        FrameProfiler.setCounter("bgDotsDrawn", drawn);
    }

    private static void drawGridLines(GuiGraphics g, int x1, int y1, int x2, int y2,
                                      float zoom, int viewOffX, int viewOffY, int gridSnap) {
        int step = getGridStep(gridSnap, zoom);
        int worldSnap = gridSnap * step;

        long startWorldX = Math.floorDiv((long) Math.floor(-viewOffX / (double) zoom), worldSnap) * worldSnap;
        long endWorldX = (Math.floorDiv((long) Math.ceil((x2 - x1 - viewOffX) / (double) zoom), worldSnap) + 1) *
                worldSnap;

        long startWorldY = Math.floorDiv((long) Math.floor(-viewOffY / (double) zoom), worldSnap) * worldSnap;
        long endWorldY = (Math.floorDiv((long) Math.ceil((y2 - y1 - viewOffY) / (double) zoom), worldSnap) + 1) *
                worldSnap;

        for (long worldX = startWorldX; worldX <= endWorldX; worldX += worldSnap) {
            int x = (int) (x1 + viewOffX + worldX * zoom);
            if (x >= x1 && x < x2) g.fill(x, y1, x + 1, y2, 0x18FFFFFF);
        }
        for (long worldY = startWorldY; worldY <= endWorldY; worldY += worldSnap) {
            int y = (int) (y1 + viewOffY + worldY * zoom);
            if (y >= y1 && y < y2) g.fill(x1, y, x2, y + 1, 0x18FFFFFF);
        }
    }

    private static void drawHexGrid(GuiGraphics g, int x1, int y1, int x2, int y2,
                                    float zoom, int viewOffX, int viewOffY, int gridSnap) {
        int step = getGridStep(gridSnap, zoom);
        int worldSnap = gridSnap * step;

        float w = worldSnap * 1.7320508f;
        float h = worldSnap * 2f;
        float rowHeight = h * 0.75f;

        long startWorldY = (long) Math.floor(-viewOffY / (double) (rowHeight * zoom)) - 1;
        long endWorldY = (long) Math.ceil((y2 - y1 - viewOffY) / (double) (rowHeight * zoom)) + 1;

        for (long ny = startWorldY; ny <= endWorldY; ny++) {
            float worldY = ny * rowHeight;
            int y = Math.round(y1 + viewOffY + worldY * zoom);
            if (y < y1 - (h * zoom) || y > y2 + (h * zoom)) continue;

            boolean odd = Math.abs(ny % 2) == 1;
            float rowOffX = odd ? w / 2f : 0f;

            long startWorldX = (long) Math.floor((-viewOffX - rowOffX * zoom) / (double) (w * zoom)) - 1;
            long endWorldX = (long) Math.ceil((x2 - x1 - viewOffX - rowOffX * zoom) / (double) (w * zoom)) + 1;

            for (long nx = startWorldX; nx <= endWorldX; nx++) {
                float worldX = nx * w + rowOffX;
                int x = Math.round(x1 + viewOffX + worldX * zoom);
                int rScreen = Math.round(worldSnap * zoom);

                drawHexOutline(g, x, y, rScreen, 0x1AFFFFFF);
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

            if (i > 0) NodeShapeRenderer.queueThinLine(g, px, py, nx, ny, 0.5f, color);
            px = nx;
            py = ny;
        }
    }

    private static void drawDiagonalLines(GuiGraphics g, int x1, int y1, int x2, int y2,
                                          float zoom, int viewOffX, int viewOffY, int gridSnap) {
        int step = getGridStep(gridSnap, zoom);
        int worldSnap = gridSnap * step;
        double sp = worldSnap * zoom;

        int total = (x2 - x1) + (y2 - y1);

        float panShift = (float) ((viewOffX + viewOffY + x1 + y1) % sp);
        if (panShift < 0) panShift += sp;

        for (double d = -sp + panShift; d < total + sp; d += sp) {
            int ax = x1 + (int) Math.round(d), ay = y1;
            int bx = x1, by = y1 + (int) Math.round(d);

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
                NodeShapeRenderer.queueThinLine(g, cx0, cy0, cx1, cy1, 0.5f, 0x18FFFFFF);
        }
    }

    private static void drawCustomBg(GuiGraphics g, int x1, int y1, int x2, int y2, String textureLoc) {
        if (textureLoc == null || textureLoc.isBlank()) return;
        try {
            ResourceLocation loc = net.phoenixvine.chronicles.client.CustomTextureCache.resolve(
                    ResourceLocation.parse(textureLoc));
            int w = x2 - x1, h = y2 - y1;
            g.blit(loc, x1, y1, 0, 0, w, h, w, h);
        } catch (Exception ignored) {}
    }
}
