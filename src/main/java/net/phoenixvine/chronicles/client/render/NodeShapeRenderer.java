package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class NodeShapeRenderer {

    private NodeShapeRenderer() {}

    private record FillQuad(float x0, float y0, float x1, float y1,
                            float x2, float y2, float x3, float y3, int color) {}

    private static final List<FillQuad> fillQueue = new ArrayList<>();
    private static final Vector3f SCRATCH = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    public static void queueFillRect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        Matrix4f mat = g.pose().last().pose();
        SCRATCH.set(x0, y0, 0f);
        mat.transformPosition(SCRATCH);
        float ax0 = SCRATCH.x, ay0 = SCRATCH.y;
        SCRATCH.set(x1, y0, 0f);
        mat.transformPosition(SCRATCH);
        float ax1 = SCRATCH.x, ay1 = SCRATCH.y;
        SCRATCH.set(x1, y1, 0f);
        mat.transformPosition(SCRATCH);
        float ax2 = SCRATCH.x, ay2 = SCRATCH.y;
        SCRATCH.set(x0, y1, 0f);
        mat.transformPosition(SCRATCH);
        float ax3 = SCRATCH.x, ay3 = SCRATCH.y;
        fillQueue.add(new FillQuad(ax0, ay0, ax1, ay1, ax2, ay2, ax3, ay3, color));
    }

    public static void queueThinLine(GuiGraphics g, float x0, float y0, float x1, float y1, float halfWidth,
                                     int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001f) return;
        float nx = -dy / len * halfWidth, ny = dx / len * halfWidth;

        Matrix4f mat = g.pose().last().pose();
        SCRATCH.set(x0 + nx, y0 + ny, 0f);
        mat.transformPosition(SCRATCH);
        float ax0 = SCRATCH.x, ay0 = SCRATCH.y;
        SCRATCH.set(x1 + nx, y1 + ny, 0f);
        mat.transformPosition(SCRATCH);
        float ax1 = SCRATCH.x, ay1 = SCRATCH.y;
        SCRATCH.set(x1 - nx, y1 - ny, 0f);
        mat.transformPosition(SCRATCH);
        float ax2 = SCRATCH.x, ay2 = SCRATCH.y;
        SCRATCH.set(x0 - nx, y0 - ny, 0f);
        mat.transformPosition(SCRATCH);
        float ax3 = SCRATCH.x, ay3 = SCRATCH.y;
        fillQueue.add(new FillQuad(ax0, ay0, ax1, ay1, ax2, ay2, ax3, ay3, color));
    }

    public static void queueLineQuad(GuiGraphics g, float x0, float y0, float x1, float y1,
                                     float thickness, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float half = Math.max(0.5f, thickness / 2f);
        if (len < 0.0001f) {
            queueFillRect(g, (int) (x0 - half), (int) (y0 - half), (int) (x0 + half), (int) (y0 + half), color);
            return;
        }
        float nx = -dy / len * half, ny = dx / len * half;

        Matrix4f mat = g.pose().last().pose();
        SCRATCH.set(x0 + nx, y0 + ny, 0f);
        mat.transformPosition(SCRATCH);
        float ax0 = SCRATCH.x, ay0 = SCRATCH.y;
        SCRATCH.set(x1 + nx, y1 + ny, 0f);
        mat.transformPosition(SCRATCH);
        float ax1 = SCRATCH.x, ay1 = SCRATCH.y;
        SCRATCH.set(x1 - nx, y1 - ny, 0f);
        mat.transformPosition(SCRATCH);
        float ax2 = SCRATCH.x, ay2 = SCRATCH.y;
        SCRATCH.set(x0 - nx, y0 - ny, 0f);
        mat.transformPosition(SCRATCH);
        float ax3 = SCRATCH.x, ay3 = SCRATCH.y;
        fillQueue.add(new FillQuad(ax0, ay0, ax1, ay1, ax2, ay2, ax3, ay3, color));
    }

    public static int flushFillQueue(GuiGraphics g) {
        int count = fillQueue.size();
        if (fillQueue.isEmpty()) return count;
        g.flush();
        RenderSystem.disableCull();

        RenderSystem.disableDepthTest();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bb = tesselator.getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (FillQuad q : fillQueue) {
            int alpha = (q.color() >>> 24) & 0xFF;
            int r = (q.color() >>> 16) & 0xFF;
            int gg = (q.color() >>> 8) & 0xFF;
            int b = q.color() & 0xFF;
            bb.vertex(IDENTITY, q.x0(), q.y0(), 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(IDENTITY, q.x1(), q.y1(), 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(IDENTITY, q.x2(), q.y2(), 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(IDENTITY, q.x3(), q.y3(), 0f).color(r, gg, b, alpha).endVertex();
        }

        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableDepthTest();

        RenderSystem.enableCull();
        fillQueue.clear();
        return count;
    }

    public static void blitCustomShape(GuiGraphics g, net.minecraft.resources.ResourceLocation tex,
                                       int x, int y, int w, int h, int color) {
        int a = (color >>> 24) & 0xFF, r = (color >>> 16) & 0xFF, gg = (color >>> 8) & 0xFF, b = color & 0xFF;
        g.flush();
        RenderSystem.setShaderColor(r / 255f, gg / 255f, b / 255f, a / 255f);
        try {
            g.blit(tex, x, y, 0, 0, w, h, w, h);
        } finally {

            g.flush();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private static double guiScale() {
        return 4.0;
    }

    public static void fillCircle(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float cx = (float) ((x + sz / 2f) * gs), cy = (float) ((y + sz / 2f) * gs);
        float r = (float) ((sz / 2f - 0.5f) * gs);
        int py0 = (int) Math.floor(cy - r), py1 = (int) Math.ceil(cy + r);
        for (int py = py0; py < py1; py++) {
            float dy = py + 0.5f - cy;
            float dSq = r * r - dy * dy;
            if (dSq < 0) continue;
            float dx = (float) Math.sqrt(dSq);
            fillAARow(g, cx - dx, cx + dx, py, color);
        }
        g.pose().popPose();
    }

    private static void fillAARow(GuiGraphics g, float edgeL, float edgeR, int py, int color) {
        if (edgeR <= edgeL) return;
        int alpha = (color >>> 24) & 0xFF;

        int xL = (int) Math.floor(edgeL);
        int xR = (int) Math.floor(edgeR);

        if (xL == xR) {
            float cov = edgeR - edgeL;
            if (cov > 0.02f) queueFillRect(g, xL, py, xL + 1, py + 1, withAlpha(color, Math.round(alpha * cov)));
            return;
        }

        int x0 = (int) Math.ceil(edgeL);
        int x1 = xR - 1;
        if (xL < x0) {
            float cov = x0 - edgeL;
            if (cov > 0.02f) queueFillRect(g, xL, py, xL + 1, py + 1, withAlpha(color, Math.round(alpha * cov)));
        }
        if (x1 >= x0) queueFillRect(g, x0, py, x1 + 1, py + 1, color);
        float covR = edgeR - xR;
        if (covR > 0.02f) queueFillRect(g, xR, py, xR + 1, py + 1, withAlpha(color, Math.round(alpha * covR)));
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public static void outlineCircle(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float pcx = (float) ((x + sz / 2f) * gs), pcy = (float) ((y + sz / 2f) * gs);
        float outerR = (float) ((sz / 2f - 0.5f) * gs);
        float innerR = Math.max(0f, outerR - (float) (thickness * gs));
        int py0 = (int) Math.floor(pcy - outerR), py1 = (int) Math.ceil(pcy + outerR);
        for (int py = py0; py < py1; py++) {
            float dy = py + 0.5f - pcy;
            float outerSq = outerR * outerR - dy * dy;
            if (outerSq < 0) continue;
            float outerDx = (float) Math.sqrt(outerSq);
            float innerSq = innerR * innerR - dy * dy;
            if (innerSq <= 0) {

                fillAARow(g, pcx - outerDx, pcx + outerDx, py, color);
            } else {
                float innerDx = (float) Math.sqrt(innerSq);
                fillAARow(g, pcx - outerDx, pcx - innerDx, py, color);
                fillAARow(g, pcx + innerDx, pcx + outerDx, py, color);
            }
        }
        g.pose().popPose();
    }

    public static void fillDiamond(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float cx = (float) ((x + sz / 2f) * gs), cy = (float) ((y + sz / 2f) * gs);
        float h = (float) (sz / 2f * gs);
        int py0 = (int) Math.floor(cy - h), py1 = (int) Math.ceil(cy + h);
        for (int py = py0; py < py1; py++) {
            float dist = Math.abs(py + 0.5f - cy);
            float half = h - dist;
            if (half > 0) fillAARow(g, cx - half, cx + half, py, color);
        }
        g.pose().popPose();
    }

    public static void outlineDiamond(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int cx = x + sz / 2, cy = y + sz / 2, h = sz / 2 - 1;

        queueLineQuad(g, cx, cy - h, cx - h, cy, thickness, color);
        queueLineQuad(g, cx, cy - h, cx + h, cy, thickness, color);
        queueLineQuad(g, cx - h, cy, cx, cy + h, thickness, color);
        queueLineQuad(g, cx + h, cy, cx, cy + h, thickness, color);
    }

    private static final float HEX_SCALE = 1f / 0.866f;

    private static float hexHalfWidth(float dy, float r) {
        float qr = r * 0.866f;
        if (dy <= r / 2f) return qr;
        float t = 1f - (dy - r / 2f) / (r / 2f);
        return t > 0f ? qr * t : 0f;
    }

    public static void fillHexagon(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float cx = (float) ((x + sz / 2f) * gs), cy = (float) ((y + sz / 2f) * gs);
        float r = (float) ((sz / 2f - 1) * gs) * HEX_SCALE;
        int py0 = (int) Math.floor(cy - r), py1 = (int) Math.ceil(cy + r);
        for (int py = py0; py < py1; py++) {
            float dy = Math.abs(py + 0.5f - cy);
            float hw = hexHalfWidth(dy, r);
            if (hw > 0) fillAARow(g, cx - hw, cx + hw, py, color);
        }
        g.pose().popPose();
    }

    public static void outlineHexagon(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        float cx = x + sz / 2f, cy = y + sz / 2f, r = (sz / 2f - 1) * HEX_SCALE;
        int sides = 6;
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI / 6 + i * Math.PI / 3;
            double a1 = Math.PI / 6 + (i + 1) * Math.PI / 3;

            int x0 = (int) Math.round(cx + Math.cos(a0) * r), y0 = (int) Math.round(cy + Math.sin(a0) * r);
            int x1 = (int) Math.round(cx + Math.cos(a1) * r), y1 = (int) Math.round(cy + Math.sin(a1) * r);
            queueLineQuad(g, x0, y0, x1, y1, thickness, color);
        }
    }

    public static void fillTriangle(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float cx = (float) ((x + sz / 2f) * gs);
        float top = (float) ((y + 1) * gs), bot = (float) ((y + sz - 1) * gs);
        int py0 = (int) Math.floor(top), py1 = (int) Math.ceil(bot);
        for (int py = py0; py <= py1; py++) {
            float t = (py - top) / (bot - top);
            float half = t * sz / 2f * (float) gs;
            fillAARow(g, cx - half, cx + half, py, color);
        }
        g.pose().popPose();
    }

    public static void outlineTriangle(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int cx = x + sz / 2, top = y + 1, bot = y + sz - 1;
        int bl = x + 1, br = x + sz - 1;
        queueLineQuad(g, cx, top, bl, bot, thickness, color);
        queueLineQuad(g, cx, top, br, bot, thickness, color);
        queueLineQuad(g, bl, bot, br, bot, thickness, color);
    }

    public static void fillStar(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float cx = (float) ((x + sz / 2f) * gs), cy = (float) ((y + sz / 2f) * gs);
        float outerR = (float) ((sz / 2f - 1) * gs), innerR = outerR * 0.4f;
        int points = 5;

        float[] px = new float[points * 2], py2 = new float[points * 2];
        for (int i = 0; i < points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points;
            float r2 = (i % 2 == 0) ? outerR : innerR;
            px[i] = cx + (float) (Math.cos(a) * r2);
            py2[i] = cy + (float) (Math.sin(a) * r2);
        }

        float[] xs = new float[points * 2];
        int py0 = (int) Math.floor(cy - outerR), py1 = (int) Math.ceil(cy + outerR);
        for (int scanY = py0; scanY < py1; scanY++) {
            int count = 0;
            for (int i = 0; i < points * 2; i++) {
                int j = (i + 1) % (points * 2);
                float y0 = py2[i], y1 = py2[j];
                if ((y0 <= scanY && y1 > scanY) || (y1 <= scanY && y0 > scanY)) {
                    xs[count++] = px[i] + (scanY - y0) / (y1 - y0) * (px[j] - px[i]);
                }
            }

            for (int i = 1; i < count; i++) {
                float v = xs[i];
                int k = i - 1;
                while (k >= 0 && xs[k] > v) {
                    xs[k + 1] = xs[k];
                    k--;
                }
                xs[k + 1] = v;
            }
            for (int i = 0; i + 1 < count; i += 2)
                fillAARow(g, xs[i], xs[i + 1], scanY, color);
        }
        g.pose().popPose();
    }

    public static void outlineStar(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        float cx = x + sz / 2f, cy = y + sz / 2f;
        float outerR = sz / 2f - 1, innerR = outerR * 0.4f;
        int points = 5;
        int prevX = 0, prevY2 = 0;
        for (int i = 0; i <= points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points;
            float r2 = (i % 2 == 0) ? outerR : innerR;

            int nx = (int) Math.round(cx + Math.cos(a) * r2), ny = (int) Math.round(cy + Math.sin(a) * r2);
            if (i > 0) queueLineQuad(g, prevX, prevY2, nx, ny, thickness, color);
            prevX = nx;
            prevY2 = ny;
        }
    }

    public static void fillPentagon(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float cx = (float) ((x + sz / 2f) * gs), cy = (float) ((y + sz / 2f) * gs);
        float r = (float) ((sz / 2f - 1) * gs);
        int sides = 5;
        float[] px = new float[sides], py2 = new float[sides];
        for (int i = 0; i < sides; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / sides;
            px[i] = cx + (float) (Math.cos(a) * r);
            py2[i] = cy + (float) (Math.sin(a) * r);
        }

        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        fillPolygon(g, px, py2, (int) Math.floor(cy - r), (int) Math.ceil(cy + r), color);
        g.pose().popPose();
    }

    public static void outlinePentagon(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        float cx = x + sz / 2f, cy = y + sz / 2f, r = sz / 2f - 1;
        int sides = 5;
        int prevX = 0, prevY2 = 0;
        for (int i = 0; i <= sides; i++) {
            double a = -Math.PI / 2 + (i % sides) * 2 * Math.PI / sides;

            int nx = (int) Math.round(cx + Math.cos(a) * r), ny = (int) Math.round(cy + Math.sin(a) * r);
            if (i > 0) queueLineQuad(g, prevX, prevY2, nx, ny, thickness, color);
            prevX = nx;
            prevY2 = ny;
        }
    }

    public static void fillShield(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float midY = (float) ((y + sz * 2 / 3f) * gs);
        float top = (float) (y * gs), bot = (float) ((y + sz) * gs);
        float left = (float) ((x + 1) * gs), right = (float) ((x + sz - 1) * gs);

        queueFillRect(g, (int) left, (int) top, (int) right, (int) midY, color);

        float cx = (float) ((x + sz / 2f) * gs);
        int py0 = (int) Math.floor(midY), py1 = (int) Math.ceil(bot);
        for (int py = py0; py < py1; py++) {
            float t = (py - midY) / (bot - midY);
            float half = (1f - t) * (sz / 2f - 1) * (float) gs;
            if (half > 0) fillAARow(g, cx - half, cx + half, py, color);
        }
        g.pose().popPose();
    }

    public static void outlineShield(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int midY = y + sz * 2 / 3, cx = x + sz / 2;

        queueFillRect(g, x + 1, y, x + sz - 1, y + thickness, color);

        queueFillRect(g, x, y, x + thickness, midY, color);
        queueFillRect(g, x + sz - thickness, y, x + sz, midY, color);

        queueLineQuad(g, x, midY, cx, y + sz - 1, thickness, color);
        queueLineQuad(g, x + sz, midY, cx, y + sz - 1, thickness, color);
    }

    public static void fillCross(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        int arm = sz / 3;
        float cx = (float) ((x + sz / 2f) * gs), cy = (float) ((y + sz / 2f) * gs);
        float armP = (float) (arm * gs);
        float szP = (float) (sz * gs);
        float xP = (float) (x * gs), yP = (float) (y * gs);
        queueFillRect(g, (int) (cx - armP / 2), (int) (yP + armP / 2), (int) (cx + armP / 2 + 1),
                (int) (yP + szP - armP / 2),
                color);
        queueFillRect(g, (int) (xP + armP / 2), (int) (cy - armP / 2), (int) (xP + szP - armP / 2),
                (int) (cy + armP / 2 + 1),
                color);
        g.pose().popPose();
    }

    public static void outlineCross(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int arm = sz / 3;
        int cx = x + sz / 2, cy = y + sz / 2;
        int x0 = cx - arm / 2, x1 = cx + arm / 2, y0 = cy - arm / 2, y1 = cy + arm / 2;
        int ax0 = x + arm / 2, ax1 = x + sz - arm / 2;
        int ay0 = y + arm / 2, ay1 = y + sz - arm / 2;

        int[] ox = { x0, x1, x1, ax1, ax1, x1, x1, x0, x0, ax0, ax0, x0, x0 };
        int[] oy = { ay0, ay0, y0, y0, ay0, ay0, ay1, ay1, ay0, ay0, y0, y0, ay0 };
        for (int i = 0; i < 12; i++) queueLineQuad(g, ox[i], oy[i], ox[i + 1], oy[i + 1], thickness, color);
    }

    public static void fillPolygon(GuiGraphics g, float[] vx, float[] vy, int yMin, int yMax, int color) {
        int n = vx.length;

        float[] xs = new float[n];
        for (int scanY = yMin; scanY < yMax; scanY++) {
            int count = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float y0 = vy[i], y1 = vy[j];
                if ((y0 <= scanY && y1 > scanY) || (y1 <= scanY && y0 > scanY))
                    xs[count++] = vx[i] + (scanY - y0) / (y1 - y0) * (vx[j] - vx[i]);
            }
            for (int i = 1; i < count; i++) {
                float v = xs[i];
                int k = i - 1;
                while (k >= 0 && xs[k] > v) {
                    xs[k + 1] = xs[k];
                    k--;
                }
                xs[k + 1] = v;
            }
            for (int i = 0; i + 1 < count; i += 2)
                fillAARow(g, xs[i], xs[i + 1], scanY, color);
        }
    }

    public static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        drawLine(g, x0, y0, x1, y1, color, 1);
    }

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

    public static void plot(GuiGraphics g, int x, int y, int thickness, int color) {
        if (thickness <= 1) {
            g.fill(x, y, x + 1, y + 1, color);
            return;
        }
        int half = thickness / 2;
        g.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
    }
}
