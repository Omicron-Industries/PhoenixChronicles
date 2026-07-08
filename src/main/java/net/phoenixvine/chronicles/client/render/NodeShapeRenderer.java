package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Fill/outline primitives for the quest node shape gallery (circle, diamond, hexagon, triangle,
 * star, pentagon, shield, cross) plus the Bresenham line/polygon scan-fill helpers they're built
 * from. Extracted out of ChronicleOverviewScreen - none of these read any node/canvas state,
 * they just rasterize a shape into the given box.
 */
public final class NodeShapeRenderer {

    private NodeShapeRenderer() {}

    // ── Shape fill primitives ─────────────────────────────────────────────────

    /** Fills every pixel inside a circle inscribed in the [x,y,sz] box. */
    public static void fillCircle(GuiGraphics g, int x, int y, int sz, int color) {
        float cx = x + sz / 2f, cy = y + sz / 2f, r = sz / 2f - 0.5f;
        for (int py = y; py < y + sz; py++) {
            float dy = py + 0.5f - cy;
            float dx = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            int x0 = (int) Math.ceil(cx - dx), x1 = (int) Math.floor(cx + dx);
            if (x1 >= x0) g.fill(x0, py, x1 + 1, py + 1, color);
        }
    }

    /** Outline of a circle at physical-pixel precision, stroke width scaled by thickness. */
    public static void outlineCircle(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        double gs = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        int pThick = Math.max(1, (int) Math.round(thickness * gs));
        float pcx = (float) ((x + sz / 2f) * gs);
        float pcy = (float) ((y + sz / 2f) * gs);
        float pr = (float) ((sz / 2f - 1f) * gs);
        int steps = Math.max(64, (int) (pr * 6.3f));
        for (int i = 0; i < steps; i++) {
            double a = 2 * Math.PI * i / steps;
            int px = (int) Math.round(pcx + Math.cos(a) * pr);
            int py = (int) Math.round(pcy + Math.sin(a) * pr);
            g.fill(px, py, px + pThick, py + pThick, color);
        }
        g.pose().popPose();
    }

    /** Diamond (rotated square). */
    public static void fillDiamond(GuiGraphics g, int x, int y, int sz, int color) {
        int cx = x + sz / 2, cy = y + sz / 2, h = sz / 2;
        for (int py = y; py < y + sz; py++) {
            int dist = Math.abs(py - cy);
            int half = h - dist;
            if (half > 0) g.fill(cx - half, py, cx + half, py + 1, color);
        }
    }

    public static void outlineDiamond(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int cx = x + sz / 2, cy = y + sz / 2, h = sz / 2 - 1;
        // Four edges: top-left, top-right, bottom-left, bottom-right
        for (int i = 0; i <= h; i++) {
            plot(g, cx - i, cy - h + i, thickness, color); // TL edge
            plot(g, cx + i, cy - h + i, thickness, color); // TR edge
            plot(g, cx - i, cy + h - i, thickness, color); // BL edge
            plot(g, cx + i, cy + h - i, thickness, color); // BR edge
        }
    }

    /** Flat-top hexagon. */
    public static void fillHexagon(GuiGraphics g, int x, int y, int sz, int color) {
        float cx = x + sz / 2f, cy = y + sz / 2f, r = sz / 2f - 1;
        float qr = r * 0.866f; // sqrt(3)/2
        for (int py = y; py < y + sz; py++) {
            float dy = Math.abs(py + 0.5f - cy);
            float hw;
            if (dy <= r / 2f) hw = qr;
            else hw = qr * (1f - (dy - r / 2f) / (r / 2f));
            if (hw > 0) g.fill((int) (cx - hw), py, (int) (cx + hw) + 1, py + 1, color);
        }
    }

    public static void outlineHexagon(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        float cx = x + sz / 2f, cy = y + sz / 2f, r = sz / 2f - 1;
        int sides = 6;
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI / 6 + i * Math.PI / 3;
            double a1 = Math.PI / 6 + (i + 1) * Math.PI / 3;
            int x0 = (int) (cx + Math.cos(a0) * r), y0 = (int) (cy + Math.sin(a0) * r);
            int x1 = (int) (cx + Math.cos(a1) * r), y1 = (int) (cy + Math.sin(a1) * r);
            drawLine(g, x0, y0, x1, y1, color, thickness);
        }
    }

    /** Upward-pointing triangle. */
    public static void fillTriangle(GuiGraphics g, int x, int y, int sz, int color) {
        int cx = x + sz / 2;
        int top = y + 1, bot = y + sz - 1;
        for (int py = top; py <= bot; py++) {
            float t = (float) (py - top) / (bot - top);
            int half = (int) (t * sz / 2);
            g.fill(cx - half, py, cx + half + 1, py + 1, color);
        }
    }

    public static void outlineTriangle(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int cx = x + sz / 2, top = y + 1, bot = y + sz - 1;
        int bl = x + 1, br = x + sz - 1;
        drawLine(g, cx, top, bl, bot, color, thickness); // left edge
        drawLine(g, cx, top, br, bot, color, thickness); // right edge
        drawLine(g, bl, bot, br, bot, color, thickness); // base
    }

    /** 5-pointed star. */
    public static void fillStar(GuiGraphics g, int x, int y, int sz, int color) {
        float cx = x + sz / 2f, cy = y + sz / 2f;
        float outerR = sz / 2f - 1, innerR = outerR * 0.4f;
        int points = 5;
        // Scan-line fill of star polygon
        float[] px = new float[points * 2], py2 = new float[points * 2];
        for (int i = 0; i < points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points;
            float r2 = (i % 2 == 0) ? outerR : innerR;
            px[i] = cx + (float) (Math.cos(a) * r2);
            py2[i] = cy + (float) (Math.sin(a) * r2);
        }
        for (int scanY = y; scanY < y + sz; scanY++) {
            List<Float> xs = new ArrayList<>();
            for (int i = 0; i < points * 2; i++) {
                int j = (i + 1) % (points * 2);
                float y0 = py2[i], y1 = py2[j];
                if ((y0 <= scanY && y1 > scanY) || (y1 <= scanY && y0 > scanY)) {
                    xs.add(px[i] + (scanY - y0) / (y1 - y0) * (px[j] - px[i]));
                }
            }
            xs.sort(null);
            for (int i = 0; i + 1 < xs.size(); i += 2)
                g.fill((int) xs.get(i).floatValue(), scanY,
                        (int) xs.get(i + 1).floatValue() + 1, scanY + 1, color);
        }
    }

    public static void outlineStar(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        float cx = x + sz / 2f, cy = y + sz / 2f;
        float outerR = sz / 2f - 1, innerR = outerR * 0.4f;
        int points = 5;
        int prevX = 0, prevY2 = 0;
        for (int i = 0; i <= points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points;
            float r2 = (i % 2 == 0) ? outerR : innerR;
            int nx = (int) (cx + Math.cos(a) * r2), ny = (int) (cy + Math.sin(a) * r2);
            if (i > 0) drawLine(g, prevX, prevY2, nx, ny, color, thickness);
            prevX = nx;
            prevY2 = ny;
        }
    }

    /** 5-sided pentagon. */
    public static void fillPentagon(GuiGraphics g, int x, int y, int sz, int color) {
        float cx = x + sz / 2f, cy = y + sz / 2f, r = sz / 2f - 1;
        int sides = 5;
        float[] px = new float[sides], py2 = new float[sides];
        for (int i = 0; i < sides; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / sides;
            px[i] = cx + (float) (Math.cos(a) * r);
            py2[i] = cy + (float) (Math.sin(a) * r);
        }
        fillPolygon(g, px, py2, y, y + sz, color);
    }

    public static void outlinePentagon(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        float cx = x + sz / 2f, cy = y + sz / 2f, r = sz / 2f - 1;
        int sides = 5;
        int prevX = 0, prevY2 = 0;
        for (int i = 0; i <= sides; i++) {
            double a = -Math.PI / 2 + (i % sides) * 2 * Math.PI / sides;
            int nx = (int) (cx + Math.cos(a) * r), ny = (int) (cy + Math.sin(a) * r);
            if (i > 0) drawLine(g, prevX, prevY2, nx, ny, color, thickness);
            prevX = nx;
            prevY2 = ny;
        }
    }

    /** Shield shape: square top half, pointed bottom half. */
    public static void fillShield(GuiGraphics g, int x, int y, int sz, int color) {
        int midY = y + sz * 2 / 3;
        // Rectangular top
        g.fill(x + 1, y, x + sz - 1, midY, color);
        // Pointed lower triangle
        int cx = x + sz / 2;
        for (int py = midY; py < y + sz; py++) {
            float t = (float) (py - midY) / (y + sz - midY);
            int half = (int) ((1f - t) * (sz / 2f - 1));
            if (half > 0) g.fill(cx - half, py, cx + half + 1, py + 1, color);
        }
    }

    public static void outlineShield(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int midY = y + sz * 2 / 3, cx = x + sz / 2;
        // Top edge
        g.fill(x + 1, y, x + sz - 1, y + thickness, color);
        // Left/right sides of rectangle part
        g.fill(x, y, x + thickness, midY, color);
        g.fill(x + sz - thickness, y, x + sz, midY, color);
        // Converging lines from rect corners to bottom point
        drawLine(g, x, midY, cx, y + sz - 1, color, thickness);
        drawLine(g, x + sz, midY, cx, y + sz - 1, color, thickness);
    }

    /** Cross / plus shape. */
    public static void fillCross(GuiGraphics g, int x, int y, int sz, int color) {
        int arm = sz / 3;
        int cx = x + sz / 2, cy = y + sz / 2;
        g.fill(cx - arm / 2, y + arm / 2, cx + arm / 2 + 1, y + sz - arm / 2, color); // vertical bar
        g.fill(x + arm / 2, cy - arm / 2, x + sz - arm / 2, cy + arm / 2 + 1, color); // horizontal bar
    }

    public static void outlineCross(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int arm = sz / 3;
        int cx = x + sz / 2, cy = y + sz / 2;
        int x0 = cx - arm / 2, x1 = cx + arm / 2, y0 = cy - arm / 2, y1 = cy + arm / 2;
        int ax0 = x + arm / 2, ax1 = x + sz - arm / 2;
        int ay0 = y + arm / 2, ay1 = y + sz - arm / 2;
        // 12-sided polygon outline traced directly
        int[] ox = { x0, x1, x1, ax1, ax1, x1, x1, x0, x0, ax0, ax0, x0, x0 };
        int[] oy = { ay0, ay0, y0, y0, ay0, ay0, ay1, ay1, ay0, ay0, y0, y0, ay0 };
        for (int i = 0; i < 12; i++) drawLine(g, ox[i], oy[i], ox[i + 1], oy[i + 1], color, thickness);
    }

    // ── Generic polygon fill (scan-line) ──────────────────────────────────────

    public static void fillPolygon(GuiGraphics g, float[] vx, float[] vy, int yMin, int yMax, int color) {
        int n = vx.length;
        for (int scanY = yMin; scanY < yMax; scanY++) {
            List<Float> xs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float y0 = vy[i], y1 = vy[j];
                if ((y0 <= scanY && y1 > scanY) || (y1 <= scanY && y0 > scanY))
                    xs.add(vx[i] + (scanY - y0) / (y1 - y0) * (vx[j] - vx[i]));
            }
            xs.sort(null);
            for (int i = 0; i + 1 < xs.size(); i += 2)
                g.fill((int) xs.get(i).floatValue(), scanY,
                        (int) xs.get(i + 1).floatValue() + 1, scanY + 1, color);
        }
    }

    // ── Bresenham line ────────────────────────────────────────────────────────

    public static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        drawLine(g, x0, y0, x1, y1, color, 1);
    }

    /** Bresenham line with a stroke thickness (used for node outlines that scale with zoom). */
    public static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            plot(g, x0, y0, thickness, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** Fills a thickness×thickness square centered on (x, y); thickness 1 is a single pixel. */
    public static void plot(GuiGraphics g, int x, int y, int thickness, int color) {
        if (thickness <= 1) {
            g.fill(x, y, x + 1, y + 1, color);
            return;
        }
        int half = thickness / 2;
        g.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
    }
}
