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

/**
 * Fill/outline primitives for the quest node shape gallery (circle, diamond, hexagon, triangle,
 * star, pentagon, shield, cross) plus the Bresenham line/polygon scan-fill helpers they're built
 * from. Extracted out of ChronicleOverviewScreen - none of these read any node/canvas state,
 * they just rasterize a shape into the given box.
 */
public final class NodeShapeRenderer {

    private NodeShapeRenderer() {}

    // ── Batched fill queue ────────────────────────────────────────────────────
    //
    // Every scanline row in the fill* methods below used to be its own g.fill() call - its own
    // immediate GL draw. With ~126 nodes on screen and a dozen-plus rows per shape, that's
    // thousands of draw calls a frame just for shape fills, and profiling confirmed this
    // ("node:shape") as the dominant remaining render cost. queueFillRect() stashes each row
    // instead; flushFillQueue() uploads everything queued so far as ONE raw-Tesselator draw
    // call, mirroring DependencyLineRenderer's flushRibbonQueue()/flushArrowQueue() mechanism.
    //
    // Deliberately NOT used by drawLine()/plot() - those are shared with
    // CanvasBackgroundRenderer's grid pattern, which needs to render at its own point in the
    // frame (under the nodes), not get swept into a batch that flushes after the node pass.
    private record FillQuad(float x0, float y0, float x1, float y1,
                            float x2, float y2, float x3, float y3, int color) {}

    private static final List<FillQuad> fillQueue = new ArrayList<>();
    private static final Vector3f SCRATCH = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    /** Queues a screen rect (in the CURRENT pose, baked in immediately) for the batched flush. */
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

    /**
     * Uploads every queued shape-fill quad as one raw Tesselator draw call. Must be called once
     * per frame after every node's shape fill has had a chance to queue into this - today that's
     * right after the node-shape pass in ChronicleOverviewScreen.renderNodesAndDetails(), before
     * overlays/icons (which must paint on top of the now-flushed shapes) render.
     *
     * Called twice a frame (shapes, then badges - see renderNodesAndDetails()), so this reports
     * its own quad count via the return value rather than calling FrameProfiler.setCounter()
     * directly: that call overwrites rather than accumulates, so the second call in a frame was
     * silently clobbering the first's (much larger) shape count with the badge count. Caller sums
     * both calls' return values into one counter.
     */
    public static int flushFillQueue(GuiGraphics g) {
        int count = fillQueue.size();
        if (fillQueue.isEmpty()) return count;
        g.flush();
        RenderSystem.disableCull();

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
        fillQueue.clear();
        return count;
    }

    // ── Custom (pack-dev-supplied) shape texture ────────────────────────────────

    /**
     * Blits an already-resolved shape texture (see CustomTextureCache.resolve) as a node's
     * CUSTOM outline shape, tinted by the given ARGB color - the same RenderSystem.setShaderColor
     * bracketing BackgroundPictureRenderer uses for picture opacity, just multiplying RGB too so
     * a white/gray silhouette PNG with alpha transparency tints cleanly to any state color
     * (locked/unlocked/active/done). A texture with its own baked-in colors still gets
     * color-multiplied on top, which is usually still readable but won't look "clean".
     *
     * Drawn as an immediate g.blit() rather than going through the batched fill queue - unlike
     * the vector shapes, this needs an actual texture bind per call, and custom shapes are
     * expected to be a small minority of nodes in any given pack (not the dominant shape the way
     * SQUARE/HEXAGON commonly are), so the per-call draw-call cost isn't worth the complexity of
     * a texture-keyed batch.
     */
    public static void blitCustomShape(GuiGraphics g, net.minecraft.resources.ResourceLocation tex,
                                       int x, int y, int w, int h, int color) {
        int a = (color >>> 24) & 0xFF, r = (color >>> 16) & 0xFF, gg = (color >>> 8) & 0xFF, b = color & 0xFF;
        g.flush();
        RenderSystem.setShaderColor(r / 255f, gg / 255f, b / 255f, a / 255f);
        g.blit(tex, x, y, 0, 0, w, h, w, h);
        g.flush();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * GUI scale factor, used to rasterize fills (and outlineCircle) at physical-pixel resolution
     * instead of one scanline/plot per logical GUI pixel.
     *
     * The other outline_X methods (diamond, hexagon, triangle, star, pentagon, shield, cross)
     * briefly did this too, to fix them looking blockier than their own fill underneath - but a
     * profiler run showed "node:shape" alone costing 50-70ms/frame with only 126 nodes on screen,
     * dominating total render time. Root cause: Bresenham/plot-based outlines emit roughly one
     * g.fill() draw call per PHYSICAL pixel of edge length, and supersampling multiplies that by
     * guiScale (2-3x at common scales) across 7 shapes that previously didn't pay this cost at
     * all. Reverted those 7 back to cheap logical-space plotting; only the circle (which already
     * had this before this session) keeps it. A real fix would batch each outline into a handful
     * of filled quads (like DependencyLineRenderer's rail rendering) instead of per-pixel plots -
     * that's a bigger rewrite, not attempted here given this needs to stop regressing frame time
     * *now*.
     *
     * Capped at 2x even when the window's real GUI scale is higher (3-4x is common on
     * 1440p/4K setups with GUI Scale left on Auto). Node shapes only ever occupy a handful of
     * physical pixels once zoomed, so 2x supersampling already looks smooth - going to 3-4x
     * buys no visible improvement while multiplying every fill's scanline (now batched quad)
     * count 1:1 with the real scale. This was still the single largest chunk of "node visuals"
     * even after batching the fills into one draw call, since the cap here controls how many
     * quads get built and queued in the first place, not just how they're uploaded.
     */
    private static double guiScale() {
        return Math.min(2.0, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
    }

    // ── Shape fill primitives ─────────────────────────────────────────────────

    /** Fills every pixel inside a circle inscribed in the [x,y,sz] box. */
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
            float dx = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            int x0 = (int) Math.ceil(cx - dx), x1 = (int) Math.floor(cx + dx);
            if (x1 >= x0) queueFillRect(g, x0, py, x1 + 1, py + 1, color);
        }
        g.pose().popPose();
    }

    /** Outline of a circle at physical-pixel precision, stroke width scaled by thickness. */
    public static void outlineCircle(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        double gs = guiScale();
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
            queueFillRect(g, px, py, px + pThick, py + pThick, color);
        }
        g.pose().popPose();
    }

    /** Diamond (rotated square). */
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
            if (half > 0) queueFillRect(g, (int) (cx - half), py, (int) (cx + half), py + 1, color);
        }
        g.pose().popPose();
    }

    public static void outlineDiamond(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int cx = x + sz / 2, cy = y + sz / 2, h = sz / 2 - 1;
        // Four edges: top-left, top-right, bottom-left, bottom-right
        for (int i = 0; i <= h; i++) {
            queuedPlot(g, cx - i, cy - h + i, thickness, color); // TL edge
            queuedPlot(g, cx + i, cy - h + i, thickness, color); // TR edge
            queuedPlot(g, cx - i, cy + h - i, thickness, color); // BL edge
            queuedPlot(g, cx + i, cy + h - i, thickness, color); // BR edge
        }
    }

    /**
     * A regular hexagon's width (flat side to flat side, or here vertex to vertex horizontally)
     * is only sqrt(3)/2 (~0.866) of its height at a given "radius" - so sizing it off the same
     * radius circle/diamond/etc. use made it read as visibly narrower/smaller than every other
     * shape at the same sz. Scaled the radius up by 1/0.866 so the hexagon's WIDTH matches what
     * the other shapes use as their full diameter; it now runs slightly taller than sz vertically
     * to compensate, which is fine - the drop shadow/glow effects already extend a few px past
     * the node's own footprint anyway, so a little vertical overflow isn't new.
     */
    private static final float HEX_SCALE = 1f / 0.866f;

    /** Flat-top hexagon. */
    public static void fillHexagon(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float cx = (float) ((x + sz / 2f) * gs), cy = (float) ((y + sz / 2f) * gs);
        float r = (float) ((sz / 2f - 1) * gs) * HEX_SCALE;
        float qr = r * 0.866f; // sqrt(3)/2
        int py0 = (int) Math.floor(cy - r), py1 = (int) Math.ceil(cy + r);
        for (int py = py0; py < py1; py++) {
            float dy = Math.abs(py + 0.5f - cy);
            float hw;
            if (dy <= r / 2f) hw = qr;
            else hw = qr * (1f - (dy - r / 2f) / (r / 2f));
            if (hw > 0) queueFillRect(g, (int) (cx - hw), py, (int) (cx + hw) + 1, py + 1, color);
        }
        g.pose().popPose();
    }

    public static void outlineHexagon(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        float cx = x + sz / 2f, cy = y + sz / 2f, r = (sz / 2f - 1) * HEX_SCALE;
        int sides = 6;
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI / 6 + i * Math.PI / 3;
            double a1 = Math.PI / 6 + (i + 1) * Math.PI / 3;
            int x0 = (int) (cx + Math.cos(a0) * r), y0 = (int) (cy + Math.sin(a0) * r);
            int x1 = (int) (cx + Math.cos(a1) * r), y1 = (int) (cy + Math.sin(a1) * r);
            queuedDrawLine(g, x0, y0, x1, y1, color, thickness);
        }
    }

    /** Upward-pointing triangle. */
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
            queueFillRect(g, (int) (cx - half), py, (int) (cx + half) + 1, py + 1, color);
        }
        g.pose().popPose();
    }

    public static void outlineTriangle(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int cx = x + sz / 2, top = y + 1, bot = y + sz - 1;
        int bl = x + 1, br = x + sz - 1;
        queuedDrawLine(g, cx, top, bl, bot, color, thickness); // left edge
        queuedDrawLine(g, cx, top, br, bot, color, thickness); // right edge
        queuedDrawLine(g, bl, bot, br, bot, color, thickness); // base
    }

    /** 5-pointed star. */
    public static void fillStar(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float cx = (float) ((x + sz / 2f) * gs), cy = (float) ((y + sz / 2f) * gs);
        float outerR = (float) ((sz / 2f - 1) * gs), innerR = outerR * 0.4f;
        int points = 5;
        // Scan-line fill of star polygon
        float[] px = new float[points * 2], py2 = new float[points * 2];
        for (int i = 0; i < points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points;
            float r2 = (i % 2 == 0) ? outerR : innerR;
            px[i] = cx + (float) (Math.cos(a) * r2);
            py2[i] = cy + (float) (Math.sin(a) * r2);
        }
        // A 10-vertex star has at most 10 edge crossings per scanline - a small fixed-size
        // array reused across scanlines avoids allocating and boxing-sorting a fresh
        // ArrayList<Float> for every single scanline row (which, with fills now supersampled to
        // physical-pixel resolution, means 2-3x as many scanlines - and therefore allocations -
        // per star than before at any GUI scale above 1x).
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
            // Insertion sort - cheaper than Arrays.sort's overhead at this tiny N
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
                queueFillRect(g, (int) xs[i], scanY, (int) xs[i + 1] + 1, scanY + 1, color);
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
            int nx = (int) (cx + Math.cos(a) * r2), ny = (int) (cy + Math.sin(a) * r2);
            if (i > 0) queuedDrawLine(g, prevX, prevY2, nx, ny, color, thickness);
            prevX = nx;
            prevY2 = ny;
        }
    }

    /** 5-sided pentagon. */
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
            int nx = (int) (cx + Math.cos(a) * r), ny = (int) (cy + Math.sin(a) * r);
            if (i > 0) queuedDrawLine(g, prevX, prevY2, nx, ny, color, thickness);
            prevX = nx;
            prevY2 = ny;
        }
    }

    /** Shield shape: square top half, pointed bottom half. */
    public static void fillShield(GuiGraphics g, int x, int y, int sz, int color) {
        double gs = guiScale();
        float s = (float) (1.0 / gs);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        float midY = (float) ((y + sz * 2 / 3f) * gs);
        float top = (float) (y * gs), bot = (float) ((y + sz) * gs);
        float left = (float) ((x + 1) * gs), right = (float) ((x + sz - 1) * gs);
        // Rectangular top
        queueFillRect(g, (int) left, (int) top, (int) right, (int) midY, color);
        // Pointed lower triangle
        float cx = (float) ((x + sz / 2f) * gs);
        int py0 = (int) Math.floor(midY), py1 = (int) Math.ceil(bot);
        for (int py = py0; py < py1; py++) {
            float t = (py - midY) / (bot - midY);
            float half = (1f - t) * (sz / 2f - 1) * (float) gs;
            if (half > 0) queueFillRect(g, (int) (cx - half), py, (int) (cx + half) + 1, py + 1, color);
        }
        g.pose().popPose();
    }

    public static void outlineShield(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int midY = y + sz * 2 / 3, cx = x + sz / 2;
        // Top edge
        queueFillRect(g, x + 1, y, x + sz - 1, y + thickness, color);
        // Left/right sides of rectangle part
        queueFillRect(g, x, y, x + thickness, midY, color);
        queueFillRect(g, x + sz - thickness, y, x + sz, midY, color);
        // Converging lines from rect corners to bottom point
        queuedDrawLine(g, x, midY, cx, y + sz - 1, color, thickness);
        queuedDrawLine(g, x + sz, midY, cx, y + sz - 1, color, thickness);
    }

    /** Cross / plus shape. */
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
        // 12-sided polygon outline traced directly
        int[] ox = { x0, x1, x1, ax1, ax1, x1, x1, x0, x0, ax0, ax0, x0, x0 };
        int[] oy = { ay0, ay0, y0, y0, ay0, ay0, ay1, ay1, ay0, ay0, y0, y0, ay0 };
        for (int i = 0; i < 12; i++) queuedDrawLine(g, ox[i], oy[i], ox[i + 1], oy[i + 1], color, thickness);
    }

    // ── Generic polygon fill (scan-line) ──────────────────────────────────────

    /**
     * Caller-space scanline fill - NOT physical-pixel-scaled internally, since callers that
     * want that (e.g. fillPentagon) already push their own physical-pixel pose and pass
     * already-scaled coordinates in. Called directly in logical space otherwise.
     */
    public static void fillPolygon(GuiGraphics g, float[] vx, float[] vy, int yMin, int yMax, int color) {
        int n = vx.length;
        // Same fixed-array + insertion-sort approach as fillStar - avoids a fresh ArrayList<Float>
        // allocation and boxed sort per scanline (now supersampled to 2-3x the scanline count).
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
                queueFillRect(g, (int) xs[i], scanY, (int) xs[i + 1] + 1, scanY + 1, color);
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

    // ── Batched outline strokes (node outlines only - NOT shared with drawLine/plot above,
    // which CanvasBackgroundRenderer's grid pattern also calls directly and needs to keep
    // drawing at its own point in the frame) ──────────────────────────────────────────────

    /**
     * Same Bresenham walk as drawLine(), but queues each step instead of drawing it immediately -
     * used only by the 7 outlineX shape methods below, which are exclusively called for node
     * outlines. With hexagon-heavy packs (each outline stroke is 6 edges) this was the largest
     * remaining unbatched cost in "node:shape" even after the fills were batched: unlike the
     * fills (already physical-pixel-scaled and few dozen quads apiece), these outlines walk in
     * LOGICAL space so their step count is small per edge, but every step was still its own
     * immediate g.fill() draw call.
     */
    private static void queuedDrawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            queuedPlot(g, x0, y0, thickness, color);
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

    private static void queuedPlot(GuiGraphics g, int x, int y, int thickness, int color) {
        if (thickness <= 1) {
            queueFillRect(g, x, y, x + 1, y + 1, color);
            return;
        }
        int half = thickness / 2;
        queueFillRect(g, x - half, y - half, x - half + thickness, y - half + thickness, color);
    }
}
