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

        // The badge flush (renderNodeDetails' second pass - see renderNodesAndDetails()) runs
        // strictly after every node's g.renderItem() icon for this frame in source order, but
        // renderItem()'s 3D icon geometry writes real depth-buffer values (the same "wins every
        // z-order trick" behavior documented all over ChronicleOverviewScreen). Without disabling
        // depth test here, a badge quad landing on a corner the icon's geometry also covers can
        // fail the depth comparison and render invisible/behind it despite being queued and
        // flushed later - this was the "state badge renders behind the item icon" bug. The shape
        // pass's own flush (queued/flushed before any icon exists yet this frame) doesn't need
        // this, but disabling it unconditionally here is harmless for that call too.
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
     * The original reason this was capped at 2x (see git history) doesn't fully apply anymore:
     * the outline_X methods for diamond/hexagon/triangle/star/pentagon/shield/cross were briefly
     * ALSO physical-pixel-supersampled here, which multiplied their per-pixel plot count by
     * guiScale on top of an already dominant per-node cost - that's what drove "node:shape" to
     * 50-70ms/frame. Those 7 outlines have since moved to queuedDrawLine's Wu's-algorithm /
     * Bresenham-core renderer, which works in fixed LOGICAL coordinates and doesn't call this
     * method AT ALL - so raising the cap here no longer multiplies 7 extra shapes' outline cost
     * the way it used to. Only the FILLS (all 7 shapes) and outlineCircle still scale with it,
     * and each row now costs up to 3 quads (interior + 2 AA edges) instead of 1, so the
     * fillAARow edge blending already does most of the smoothing analytically regardless of row
     * density - more rows mainly buys smoother top/bottom curve caps, not sharper edges.
     * Raised 2x → 3x → 4x on that basis (3x confirmed no measurable regression); if this ever
     * needs revisiting, this is the one line to change.
     *
     * IMPORTANT: this must NOT be Math.min()'d against the window's actual GUI Scale setting.
     * That was the bug behind "backgrounds still look bad, especially zoomed in, but icons
     * don't" - most players run GUI Scale 2 or 3, so min(4, realScale) silently clamped this
     * back down to whatever the player's display setting was, and none of the 2x/3x/4x cap
     * raises above that ever took effect for them. Node icons (g.renderItem()) are real 3D
     * geometry rendered through the pose transform, so they stay smooth at any canvas zoom
     * level; these shapes are pre-rasterized scanlines, so their sample count has to be a
     * fixed target of its own, independent of the display's GUI scale, or they visibly
     * blockify the more the canvas zoom stretches them across the screen.
     */
    private static double guiScale() {
        return 4.0;
    }

    // ── Shape fill primitives ─────────────────────────────────────────────────

    /**
     * Fills every pixel inside a circle inscribed in the [x,y,sz] box. The physical-pixel
     * supersampling above already halves the visible stair-step size, but every sample was still
     * either "fully in" or "fully out" - a REAL circle silhouette (or hexagon, see fillHexagon)
     * needs its boundary row to blend partial alpha for the fraction of the edge pixel it
     * actually covers, not just round to the nearest whole pixel at a finer grid. That's true
     * even at 2x supersampling: rounding still produces a visible stair-step, just a smaller one.
     */
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

    /**
     * Fills the horizontal span [edgeL, edgeR) on row py, blending partial alpha into the two
     * boundary pixels (based on how much of each is actually covered) instead of just rounding
     * the span to whole pixels - the shared anti-aliasing primitive for fillCircle/fillHexagon
     * and any other curved/angled shape edge.
     */
    private static void fillAARow(GuiGraphics g, float edgeL, float edgeR, int py, int color) {
        if (edgeR <= edgeL) return;
        int alpha = (color >>> 24) & 0xFF;

        int xL = (int) Math.floor(edgeL);  // pixel containing the left edge
        int xR = (int) Math.floor(edgeR);  // pixel containing the right edge

        // Bug fixed here: when the whole span [edgeL, edgeR) fits inside a single pixel (a
        // near-cap row of a curved/pointed shape is often narrower than 1px), this used to fall
        // through to the multi-pixel logic below, which treats xL as a "left fragment" whose
        // coverage runs all the way to the NEXT pixel boundary - overestimating how much of the
        // pixel the span actually covers, and (via the old "xR != xL" guard) silently dropping
        // the right-side truncation entirely. The thinner the row, the bigger that overestimate -
        // negligible on a large shape (only its very tip has rows this thin) but a large fraction
        // of a SMALL shape's total rows are this thin, which is why small shapes looked
        // noticeably blockier/wrong than large ones despite using the exact same AA math.
        if (xL == xR) {
            float cov = edgeR - edgeL;
            if (cov > 0.02f) queueFillRect(g, xL, py, xL + 1, py + 1, withAlpha(color, Math.round(alpha * cov)));
            return;
        }

        int x0 = (int) Math.ceil(edgeL);   // first fully-covered pixel
        int x1 = xR - 1;                   // last fully-covered pixel
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

    /**
     * Outline of a circle, rendered as an anti-aliased RING (outer circle minus inner circle)
     * via the same per-row fillAARow edges fillCircle uses, instead of stamping a
     * thickness×thickness square at points walked around the circumference. Point-plotting
     * left visible gaps or overlap depending on the ratio of radius to step count, and looked
     * like a dotted/jagged ring rather than a continuous stroke - most obvious exactly where it
     * was reported: small node sizes when zoomed out, where "steps" is clamped low but the
     * circle is still visually prominent.
     */
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
                // This row is entirely within the ring's thickness (near the top/bottom cap,
                // where there's no "hole" yet) - one solid AA span across the full width.
                fillAARow(g, pcx - outerDx, pcx + outerDx, py, color);
            } else {
                float innerDx = (float) Math.sqrt(innerSq);
                fillAARow(g, pcx - outerDx, pcx - innerDx, py, color);
                fillAARow(g, pcx + innerDx, pcx + outerDx, py, color);
            }
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
            if (half > 0) fillAARow(g, cx - half, cx + half, py, color);
        }
        g.pose().popPose();
    }

    public static void outlineDiamond(GuiGraphics g, int x, int y, int sz, int color, int thickness) {
        int cx = x + sz / 2, cy = y + sz / 2, h = sz / 2 - 1;
        // Four edges, drawn through the shared AA line routine (was a manual per-pixel
        // queuedPlot walk that never went through queuedDrawLine at all, so it stayed jagged
        // even after the other shapes' outlines got anti-aliased).
        queuedDrawLine(g, cx, cy - h, cx - h, cy, color, thickness); // top-left edge
        queuedDrawLine(g, cx, cy - h, cx + h, cy, color, thickness); // top-right edge
        queuedDrawLine(g, cx - h, cy, cx, cy + h, color, thickness); // bottom-left edge
        queuedDrawLine(g, cx + h, cy, cx, cy + h, color, thickness); // bottom-right edge
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
            // Math.round(), not a truncating (int) cast - truncation always rounds toward zero
            // rather than to the nearest pixel, so it introduces a systematic (not random) up-
            // to-1px bias in every vertex. Invisible on a large hexagon; on a small one that's a
            // big fraction of the whole radius, which is exactly why this read as visibly lopsided
            // specifically when zoomed out.
            int x0 = (int) Math.round(cx + Math.cos(a0) * r), y0 = (int) Math.round(cy + Math.sin(a0) * r);
            int x1 = (int) Math.round(cx + Math.cos(a1) * r), y1 = (int) Math.round(cy + Math.sin(a1) * r);
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
            fillAARow(g, cx - half, cx + half, py, color);
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
            // Rounded, not truncated - see outlineHexagon's matching comment.
            int nx = (int) Math.round(cx + Math.cos(a) * r2), ny = (int) Math.round(cy + Math.sin(a) * r2);
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
            // Rounded, not truncated - see outlineHexagon's matching comment.
            int nx = (int) Math.round(cx + Math.cos(a) * r), ny = (int) Math.round(cy + Math.sin(a) * r);
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
            if (half > 0) fillAARow(g, cx - half, cx + half, py, color);
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
                fillAARow(g, xs[i], xs[i + 1], scanY, color);
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
    /**
     * Every straight-edge outline (diamond/hexagon/triangle/star/pentagon/shield/cross) routes
     * through this one method. For a thin (thickness 1) line, Wu's algorithm alone is correct
     * and sufficient - see drawWuLine(). For thickness > 1, applying Wu's per-step alpha split
     * directly to a thickness×thickness block was the actual bug reported here: consecutive
     * steps' blocks overlap once thickness > 1 (each step only advances 1px along the line, but
     * the block is `thickness` px wide/tall), so the SAME semi-transparent color kept
     * re-blending on top of itself across most of the stroke's BODY, not just its boundary -
     * reading as an overall darker/muddier stroke instead of a clean line with softened edges.
     * Fixed by drawing the thick core as a solid, fully-opaque Bresenham walk (no blending, so
     * no compounding), then adding a thin 1px anti-aliased fringe offset to each side of that
     * core to soften just the two true edges.
     */
    private static void queuedDrawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int thickness) {
        if (thickness <= 1) {
            drawWuLine(g, x0, y0, x1, y1, color, 1);
            return;
        }
        bresenhamCore(g, x0, y0, x1, y1, color, thickness);

        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) return;
        double nx = -dy / len, ny = dx / len; // unit normal, perpendicular to the line
        double off = thickness / 2.0 + 0.5;

        int ax0 = (int) Math.round(x0 + nx * off), ay0 = (int) Math.round(y0 + ny * off);
        int ax1 = (int) Math.round(x1 + nx * off), ay1 = (int) Math.round(y1 + ny * off);
        drawWuLine(g, ax0, ay0, ax1, ay1, color, 1);

        int bx0 = (int) Math.round(x0 - nx * off), by0 = (int) Math.round(y0 - ny * off);
        int bx1 = (int) Math.round(x1 - nx * off), by1 = (int) Math.round(y1 - ny * off);
        drawWuLine(g, bx0, by0, bx1, by1, color, 1);
    }

    /**
     * Xiaolin Wu-style anti-aliased 1px-wide line - see queuedDrawLine's doc for why thickness > 1 needs different
     * handling.
     */
    private static void drawWuLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int thickness) {
        boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
        if (steep) {
            int t = x0;
            x0 = y0;
            y0 = t;
            t = x1;
            x1 = y1;
            y1 = t;
        }
        if (x0 > x1) {
            int t = x0;
            x0 = x1;
            x1 = t;
            t = y0;
            y0 = y1;
            y1 = t;
        }
        float dx = x1 - x0, dy = y1 - y0;
        float gradient = dx == 0 ? 1f : dy / dx;
        int alpha = (color >>> 24) & 0xFF;
        float intery = y0;
        for (int x = x0; x <= x1; x++) {
            int iy = (int) Math.floor(intery);
            float frac = intery - iy;
            int a1 = Math.round(alpha * (1f - frac));
            int a2 = Math.round(alpha * frac);
            if (steep) {
                if (a1 > 2) queuedPlot(g, iy, x, thickness, withAlpha(color, a1));
                if (a2 > 2) queuedPlot(g, iy + 1, x, thickness, withAlpha(color, a2));
            } else {
                if (a1 > 2) queuedPlot(g, x, iy, thickness, withAlpha(color, a1));
                if (a2 > 2) queuedPlot(g, x, iy + 1, thickness, withAlpha(color, a2));
            }
            intery += gradient;
        }
    }

    /** Opaque Bresenham walk - the solid core of a thick outline stroke (see queuedDrawLine). */
    private static void bresenhamCore(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int thickness) {
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
