package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.FrameProfiler;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Turns resolved quest-dependency edges into pixels: cached bezier ribbon geometry, flowing
 * arrow chevrons, the "active quest" spark, the live link-drag preview line, and the per-edge
 * right-click context menu (style overrides, remove/link/cosmetic/hide toggles).
 *
 * Extracted out of ChronicleOverviewScreen. The split line: ChronicleOverviewScreen still walks
 * the quest graph to decide WHICH edges exist and what color/style they should have (that's
 * quest-domain logic - QuestState, isPrereqForbidden/Link/Cosmetic, category filters); this class
 * only knows how to turn already-resolved edge tuples into rendered lines.
 */
public class DependencyLineRenderer {

    private static final float[] EMPTY_FLOATS = new float[0];
    private static final int TRIM_SAMPLE_STEPS = 48;

    /**
     * How far a trimmed line end runs UNDER the node icon's edge (rather than stopping exactly
     * at it), so the icon's own paint guarantees the connector reads as touching the quest with
     * no stray subpixel gap.
     */
    private static final float TRIM_OVERLAP_PX = 2f;

    /**
     * Target apparent speed for flowing dependency-line arrows, in screen px/ms - each edge's
     * actual traversal period is derived from this against its own trimmed length instead of
     * sharing one fixed period, so short and long edges read as moving at the same rate.
     */
    private static final float ARROW_SPEED_PX_PER_MS = 0.15f;

    /**
     * Dependency-line arrowhead sprite. Transparent-background variant, since this is drawn
     * as a rotated overlay on top of the line ribbon rather than a standalone icon.
     */
    private static final ResourceLocation ARROW_SPRITE = new ResourceLocation("phoenix_chronicles",
            "textures/gui/sprites/arrow_no_background.png");

    private final List<int[]> lineCache = new ArrayList<>();
    private final List<ResourceLocation[]> lineCacheNodes = new ArrayList<>();
    private final List<LineGeometry> lineGeometryCache = new ArrayList<>();
    private final List<ArrowInstance> arrowQueue = new ArrayList<>();
    private final List<RibbonQuad> ribbonQueue = new ArrayList<>();

    private boolean lineCtxOpen = false;
    private int lineCtxX, lineCtxY;
    private ResourceLocation lineCtxParentId, lineCtxChildId;

    // ── Cache rebuild ────────────────────────────────────────────────────────

    /**
     * Replaces the current edge set and re-resolves every edge's render geometry. Called by
     * ChronicleOverviewScreen's buildLineCache() once it's finished walking the quest graph.
     */
    public void rebuild(List<int[]> edges, List<ResourceLocation[]> edgeNodes,
                        float zoom, float nodeSizePx, QuestChroniclesSettings settings) {
        lineCache.clear();
        lineCache.addAll(edges);
        lineCacheNodes.clear();
        lineCacheNodes.addAll(edgeNodes);
        rebuildLineGeometryCache(zoom, nodeSizePx, settings);
    }

    /**
     * Shifts every cached coordinate by a pure translation - panning doesn't change any line's
     * shape, thickness, or color, only where it sits on screen, so this skips a full rebuild.
     */
    public void panShift(int dx, int dy) {
        for (int i = 0; i < lineCache.size(); i++) {
            int[] line = lineCache.get(i);
            line[0] += dx;
            line[1] += dy;
            line[2] += dx;
            line[3] += dy;
            if (i < lineGeometryCache.size()) lineGeometryCache.get(i).shiftBy(dx, dy);
        }
    }

    private void rebuildLineGeometryCache(float zoom, float nodeSizePx, QuestChroniclesSettings settings) {
        lineGeometryCache.clear();
        for (int[] ln : lineCache) {
            lineGeometryCache.add(buildLineGeometry(ln, settings, zoom, nodeSizePx));
        }
    }

    // ── Cached line geometry (perf) ─────────────────────────────────────────

    /**
     * One edge's fully-resolved screen-space render data. rebuild() only runs when something
     * about the graph actually changed (node moved, zoom/pan changed, edge style edited) - but
     * render() used to re-walk the cubic bezier (sqrt/normalize per segment, up to 64 segments,
     * twice per line for the outline+core ribbon) from raw ints every single frame regardless,
     * which is why dependency-line rendering dominated the profiler. This holds the resolved
     * quad corners once; render() just replays them, so the only per-frame cost left for a line
     * is a cheap integer modulo to pick the dash phase.
     */
    private static final class LineGeometry {

        /**
         * 16 floats per segment: two thin rail quads (top rail x0..x3 at [0..7], bottom rail at
         * [8..15]), already screen-space. A hollow channel rather than one filled bar - a solid
         * fill drawn under the arrow chain showed through the transparent notch at the back of
         * each chevron sprite as a stray rectangle cutting through it. The arrow chain (tiled the
         * full length of the edge, see arrowCount below) travels inside this channel and is what
         * actually reads as "the connection"; the rails are just the FTBQ-style encapsulation.
         */
        final float[] quads;
        final int segmentCount;
        final int color;
        final boolean isSolid;
        final int dashPeriod;
        final int dashOn;
        final long effectiveSpeedDiv;
        /**
         * Arrows tile the entire path edge-to-edge (see buildLineGeometry's arrowCount calc)
         * rather than a handful of widely-spaced chevrons. Since their position moves every
         * frame when animated, the quad corners can't be pre-baked like the ribbon - instead the
         * control points are cached so emitLineGeometry() can cheaply re-sample a handful of
         * points on the curve (not the full 16-64 segment walk) each frame.
         */
        final boolean showArrow;
        float xa, ya, cp1x, cp1y, cp2x, cp2y, xb, yb;
        final float arrowSize;
        final int arrowColor;
        final int arrowCount;
        /**
         * Ribbon half-width, reused as the rounded-end-cap radius so the cap seamlessly caps
         * off the ribbon's own width instead of an arbitrary separate size.
         */
        final float capRadius;
        /**
         * Time for one arrow to travel this edge's own (now-trimmed) visible span, scaled to
         * the edge's length instead of a single shared constant - a fixed period made short
         * connectors between nearby nodes crawl at a much lower apparent px/sec than long ones.
         */
        final long arrowPeriodMs;
        /**
         * arcT[k] = the bezier t-parameter whose cumulative arc length is k/(arcT.length-1) of
         * the curve's total length - lets arrow placement be arc-length-uniform (evenly spaced
         * in actual screen pixels) instead of t-uniform (which bunches near the ends of a
         * non-uniform-speed bezier and spreads out mid-curve). See emitLineGeometry.
         */
        final float[] arcT;

        LineGeometry(float[] quads, int segmentCount, int color,
                     boolean isSolid, int dashPeriod, int dashOn, long effectiveSpeedDiv,
                     boolean showArrow, float xa, float ya, float cp1x, float cp1y, float cp2x, float cp2y,
                     float xb, float yb, float arrowSize, int arrowColor, int arrowCount, float capRadius,
                     long arrowPeriodMs, float[] arcT) {
            this.quads = quads;
            this.segmentCount = segmentCount;
            this.color = color;
            this.isSolid = isSolid;
            this.dashPeriod = dashPeriod;
            this.dashOn = dashOn;
            this.effectiveSpeedDiv = effectiveSpeedDiv;
            this.showArrow = showArrow;
            this.xa = xa;
            this.ya = ya;
            this.cp1x = cp1x;
            this.cp1y = cp1y;
            this.cp2x = cp2x;
            this.cp2y = cp2y;
            this.xb = xb;
            this.yb = yb;
            this.arrowSize = arrowSize;
            this.arrowColor = arrowColor;
            this.arrowCount = arrowCount;
            this.capRadius = capRadius;
            this.arrowPeriodMs = arrowPeriodMs;
            this.arcT = arcT;
        }

        /**
         * Shifts every cached coordinate by a pure translation - no trig needed, since panning
         * doesn't change any line's shape, thickness, or color, only where it sits on screen.
         * Lets panShift() keep this cache in sync without falling back to a full rebuild.
         */
        void shiftBy(float dx, float dy) {
            for (int i = 0; i < quads.length; i += 2) {
                quads[i] += dx;
                quads[i + 1] += dy;
            }
            xa += dx;
            ya += dy;
            cp1x += dx;
            cp1y += dy;
            cp2x += dx;
            cp2y += dy;
            xb += dx;
            yb += dy;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Samples the full parent-to-child bezier to find the parameter range [t0,t1] that lies
     * outside both endpoint nodes' square icon footprints, so the trimmed line touches exactly
     * at the icon's edge instead of stopping early. Uses Chebyshev (box) distance rather than a
     * circular radius - a circular keep-out left a visible gap along diagonal approaches (up to
     * ~20% of the icon's size short of its actual corner), which read as the line not connecting
     * to the quest at all. Returns null when the icons overlap/sit too close for any gap to exist.
     */
    private static float[] findTrimRange(float xa, float ya, float cp1x, float cp1y,
                                         float cp2x, float cp2y, float xb, float yb,
                                         float startHalfSize, float endHalfSize) {
        float t0 = -1f, t1 = -1f;
        for (int i = 0; i <= TRIM_SAMPLE_STEPS; i++) {
            float t = (float) i / TRIM_SAMPLE_STEPS;
            float mt = 1f - t;
            float px = mt * mt * mt * xa + 3 * mt * mt * t * cp1x + 3 * mt * t * t * cp2x + t * t * t * xb;
            float py = mt * mt * mt * ya + 3 * mt * mt * t * cp1y + 3 * mt * t * t * cp2y + t * t * t * yb;
            if (t0 < 0f) {
                float dxs = Math.abs(px - xa), dys = Math.abs(py - ya);
                if (Math.max(dxs, dys) >= startHalfSize) t0 = t;
            }
            float dxe = Math.abs(px - xb), dye = Math.abs(py - yb);
            if (Math.max(dxe, dye) >= endHalfSize) t1 = t;
        }
        if (t0 < 0f || t1 < 0f || t0 >= t1) return null;
        return new float[] { t0, t1 };
    }

    /** de Casteljau split of a cubic bezier at parameter t - the [0,t] half, re-expressed as its own cubic. */
    private static float[] bezierLeftHalf(float p0x, float p0y, float p1x, float p1y,
                                          float p2x, float p2y, float p3x, float p3y, float t) {
        float ax = lerp(p0x, p1x, t), ay = lerp(p0y, p1y, t);
        float bx = lerp(p1x, p2x, t), by = lerp(p1y, p2y, t);
        float cx = lerp(p2x, p3x, t), cy = lerp(p2y, p3y, t);
        float dx = lerp(ax, bx, t), dy = lerp(ay, by, t);
        float ex = lerp(bx, cx, t), ey = lerp(by, cy, t);
        float fx = lerp(dx, ex, t), fy = lerp(dy, ey, t);
        return new float[] { p0x, p0y, ax, ay, dx, dy, fx, fy };
    }

    /** de Casteljau split of a cubic bezier at parameter t - the [t,1] half, re-expressed as its own cubic. */
    private static float[] bezierRightHalf(float p0x, float p0y, float p1x, float p1y,
                                           float p2x, float p2y, float p3x, float p3y, float t) {
        float ax = lerp(p0x, p1x, t), ay = lerp(p0y, p1y, t);
        float bx = lerp(p1x, p2x, t), by = lerp(p1y, p2y, t);
        float cx = lerp(p2x, p3x, t), cy = lerp(p2y, p3y, t);
        float dx = lerp(ax, bx, t), dy = lerp(ay, by, t);
        float ex = lerp(bx, cx, t), ey = lerp(by, cy, t);
        float fx = lerp(dx, ex, t), fy = lerp(dy, ey, t);
        return new float[] { fx, fy, ex, ey, cx, cy, p3x, p3y };
    }

    /**
     * Extracts the exact cubic-bezier sub-arc spanning [t0,t1] of the original curve via two
     * chained de Casteljau splits, rather than just plugging new endpoints into the old control
     * points (which would silently change the curve's shape instead of trimming it).
     */
    private static float[] bezierSubArc(float xa, float ya, float cp1x, float cp1y,
                                        float cp2x, float cp2y, float xb, float yb,
                                        float t0, float t1) {
        float[] left = bezierLeftHalf(xa, ya, cp1x, cp1y, cp2x, cp2y, xb, yb, t1);
        float t0n = t1 > 1e-6f ? t0 / t1 : 0f;
        return bezierRightHalf(left[0], left[1], left[2], left[3], left[4], left[5], left[6], left[7], t0n);
    }

    /**
     * Resolves one edge's style overrides and walks its cubic bezier ONCE, producing the
     * screen-space quad corners for both ribbon passes plus the arrowhead's tip/direction.
     */
    private LineGeometry buildLineGeometry(int[] ln, QuestChroniclesSettings settings, float zoom, float nodeSizePx) {
        int x1 = ln[0], y1 = ln[1], x2 = ln[2], y2 = ln[3];
        int color = ln[4], style = ln[5];
        QuestChroniclesSettings.LineStyle shapeOverride = ln[6] >= 0 ?
                QuestChroniclesSettings.LineStyle.values()[ln[6]] : null;
        QuestChroniclesSettings.LineVisualStyle visOverride = ln[7] >= 0 ?
                QuestChroniclesSettings.LineVisualStyle.values()[ln[7]] : null;
        QuestChroniclesSettings.LineAnimSpeed speedOverride = ln[8] >= 0 ?
                QuestChroniclesSettings.LineAnimSpeed.values()[ln[8]] : null;
        Boolean arrowOverride = ln[9] < 0 ? null : ln[9] == 1;

        boolean spline = (shapeOverride != null ? shapeOverride : settings.getLineStyle()) ==
                QuestChroniclesSettings.LineStyle.SPLINE;
        QuestChroniclesSettings.LineVisualStyle vis = visOverride != null ? visOverride : settings.getLineVisualStyle();
        long speedDiv = (speedOverride != null ? speedOverride : settings.getLineAnimSpeed()).divisor;
        boolean showArrow = arrowOverride != null ? arrowOverride : settings.isShowLineArrows();

        float xa = x1, ya = y1, xb = x2, yb = y2;
        float adx = Math.abs(xb - xa), ady = Math.abs(yb - ya);
        float cp1x, cp1y, cp2x, cp2y;
        if (!spline) {
            cp1x = xa;
            cp1y = ya;
            cp2x = xb;
            cp2y = yb;
        } else if (adx >= ady) {
            float mx = (xa + xb) / 2f;
            cp1x = mx;
            cp1y = ya;
            cp2x = mx;
            cp2y = yb;
        } else {
            float my = (ya + yb) / 2f;
            // Collapsing the second control point onto the endpoint made vertical-dominant
            // edges an asymmetric curve instead of a symmetric S like the horizontal-dominant
            // branch above (which mirrors mx at both ends).
            cp1x = xa;
            cp1y = my;
            cp2x = xb;
            cp2y = my;
        }

        float dist = (float) Math.sqrt(adx * adx + ady * ady);
        if (dist < 0.5f) {
            return new LineGeometry(EMPTY_FLOATS, 0, 0, true, 8, 5, 1L,
                    false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1L, EMPTY_FLOATS);
        }

        // Clip the rendered span to the visible gap between the two node icons (FTBQ-style)
        // instead of running the ribbon from dead-center to dead-center.
        //
        // Trimmed slightly SHORT of the icon's edge (negative gap = overlap) rather than exactly
        // at it - stopping precisely at the boundary looked like the line wasn't actually
        // touching the quest (a stray subpixel/AA gap reads as disconnected), so this deliberately
        // runs the trimmed end a couple px under where the icon will paint over it instead.
        float nodeRadius = nodeSizePx / 2f;
        float trimHalfSize = Math.max(0.5f, nodeRadius - TRIM_OVERLAP_PX);
        float[] trimRange = findTrimRange(xa, ya, cp1x, cp1y, cp2x, cp2y, xb, yb, trimHalfSize, trimHalfSize);
        if (trimRange == null) {
            // Nodes overlap or sit too close for any visible gap - nothing to draw between
            // icons that are already touching/overlapping each other.
            return new LineGeometry(EMPTY_FLOATS, 0, 0, true, 8, 5, 1L,
                    false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1L, EMPTY_FLOATS);
        }
        float[] sub = bezierSubArc(xa, ya, cp1x, cp1y, cp2x, cp2y, xb, yb, trimRange[0], trimRange[1]);
        xa = sub[0];
        ya = sub[1];
        cp1x = sub[2];
        cp1y = sub[3];
        cp2x = sub[4];
        cp2y = sub[5];
        xb = sub[6];
        yb = sub[7];
        adx = Math.abs(xb - xa);
        ady = Math.abs(yb - ya);
        dist = (float) Math.sqrt(adx * adx + ady * ady);

        int steps = Math.min(Math.max(16, (int) (dist / 4.0f)), 64);

        int alpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        float baseCoreW = switch (vis) {
            case THIN -> 0.4f;
            case BOLD -> 1.2f;
            case THICK -> 1.8f;
            case WIDE -> 2.6f;
            case GLOW -> 0.9f;
            default -> 0.75f; // NORMAL
        };

        // Scales WITH zoom (proportional, like FTBQ) instead of inversely - the FACTOR is what
        // gets floored at 0.5, not the resulting width, so it never vanishes at extreme zoom-out
        // without also washing different styles into the same clamped value.
        //
        // This clamp used to apply directly to the final widthScale (a flat "never more than
        // 1.6" cap) - THICK (1.8 base) and WIDE (2.6 base) both got capped to the exact same
        // 1.6 at normal zoom, so they rendered identically and the setting looked like it did
        // nothing between those two options. Left uncapped here; channelHalfW below has its own
        // (generous) upper clamp as the actual safety net against extreme zoom-in.
        float currentZoom = Math.max(0.05f, zoom);
        float zoomFactor = Math.max(0.5f, currentZoom);
        float widthScale = baseCoreW * zoomFactor;

        boolean isSolid = (style == 1 || style == 6 || style == 8);
        boolean isMarching = (style == 2 || style == 9);
        int dashPeriod = 8, dashOn = 5;
        if (style == 0) {
            dashPeriod = 10;
            dashOn = 3;
        } else if (style == 3 || style == 4) {
            dashPeriod = 14;
            dashOn = 6;
        } else if (style == 5) {
            dashPeriod = 8;
            dashOn = 3;
        } else if (style == 7 || style == 9) {
            dashPeriod = 20;
            dashOn = 5;
        } else if (style == 10) {
            dashPeriod = 16;
            dashOn = 2;
        }

        long effectiveSpeedDiv = isMarching ? speedDiv : 1L;

        int lineAlpha = Math.min(235, Math.max(120, alpha));
        int lineColor = (lineAlpha << 24) | rgb;

        // Channel sized first, from widthScale alone - arrows are sized to fit INSIDE it below,
        // not the other way around. Sizing the channel off the arrow (as before) meant the arrow
        // sprite's own half-width could come out wider than the channel it was supposed to travel
        // through, so its edges clipped past the rails - the area where both the rail's color and
        // the arrow's color showed at once, which read as visually broken rather than one clean
        // chevron sliding through a tube.
        //
        // railHalfThickness is computed FIRST and is the dominant term now - it's the actual
        // stroke width of each rail, i.e. the part your eye reads as "how thick is this line".
        // The old formula derived it as a small fraction (0.4x, capped at 1.2) of a channel that
        // was itself sized off widthScale - so between THIN and WIDE the visible STROKE only grew
        // from ~0.25px to ~1.04px (barely perceptible) while the GAP between the two rails grew
        // much more (0.31 to 2.6), which reads as "the rails drifting apart" rather than "the line
        // getting thicker". Rail thickness now gets the large multiplier and channelHalfW is built
        // outward from it (rail + a smaller gap term), so growing the size setting visibly thickens
        // the stroke itself instead of mostly just widening empty space between two thin strokes.
        float railHalfThickness = Math.min(2.0f, Math.max(0.35f, widthScale * 0.9f));

        // Arrow size scales off widthScale directly (same term driving rail thickness), and the
        // gap is now derived FROM the arrow instead of the other way around - it's sized with a
        // guaranteed margin around whatever the arrow's own half-width works out to. That used to
        // be backwards (gap sized off widthScale independently, arrow capped against it as a loose
        // safety net) - since railHalfThickness above claims a bigger share of widthScale than the
        // arrow's own growth needed, thicker styles ended up with an arrow that outgrew its gap
        // and visually smushed into the rails on both sides. Deriving the gap from the arrow makes
        // that impossible by construction: growing the size setting only pushes railHalfThickness
        // (and therefore the rails) further OUTWARD from a fixed, always-sufficient inner
        // clearance, rather than the clearance shrinking around a growing arrow.
        float arrowSize = Math.min(7.0f, Math.max(0.9f, widthScale * 1.6f));
        float arrowHalfWidth = arrowSize * (45f / 75f);
        float gapHalf = arrowHalfWidth * 1.2f;
        float channelHalfW = railHalfThickness + gapHalf;
        int arrowAlpha = Math.max(alpha, 200);
        int arrowColor = (arrowAlpha << 24) | rgb;
        // FTBQ tiles chevrons the full length of the connection rather than a sparse handful -
        // the arrow chain itself is what reads as "the line". Spaced so consecutive arrows sit
        // almost edge-to-edge (arrowSize*2 is one sprite's full length), clamped so it can't
        // explode into hundreds of quads on an extreme zoomed-out long edge.
        int arrowCount = showArrow ? Math.min(80, Math.max(1, Math.round(dist / (arrowSize * 2.1f)))) : 0;

        float[] pathX = new float[steps + 1];
        float[] pathY = new float[steps + 1];
        // Per-VERTEX normals, not one normal per segment - each is the analytic tangent at that
        // exact t, so segment i's end normal and segment i+1's start normal are computed from the
        // literal same t and come out bit-for-bit identical. Segments used to each pick a single
        // normal at their own midpoint, so neighboring segments' shared corner was built from two
        // slightly different angles - the tiny wedge-shaped gap/overlap that showed up as a seam
        // at every one of the ~64 joints along a long or sharply curved edge. This also reads as
        // generally smoother/less faceted overall, not just seam-free, since the offset now varies
        // continuously along the curve instead of being constant across each whole segment.
        float[] normX = new float[steps + 1];
        float[] normY = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float mt = 1f - t;
            pathX[i] = mt * mt * mt * xa + 3 * mt * mt * t * cp1x + 3 * mt * t * t * cp2x + t * t * t * xb;
            pathY[i] = mt * mt * mt * ya + 3 * mt * mt * t * cp1y + 3 * mt * t * t * cp2y + t * t * t * yb;

            float tdx = 3 * mt * mt * (cp1x - xa) + 6 * mt * t * (cp2x - cp1x) + 3 * t * t * (xb - cp2x);
            float tdy = 3 * mt * mt * (cp1y - ya) + 6 * mt * t * (cp2y - cp1y) + 3 * t * t * (yb - cp2y);
            float len = (float) Math.sqrt(tdx * tdx + tdy * tdy);
            if (len < 0.0001f) {
                // True zero-tangent only happens right at t=0/1 of a non-spline (straight-style)
                // curve - nudge inward and re-evaluate rather than leaving a degenerate normal.
                float tt = t < 0.5f ? t + 0.001f : t - 0.001f;
                float mtt = 1f - tt;
                tdx = 3 * mtt * mtt * (cp1x - xa) + 6 * mtt * tt * (cp2x - cp1x) + 3 * tt * tt * (xb - cp2x);
                tdy = 3 * mtt * mtt * (cp1y - ya) + 6 * mtt * tt * (cp2y - cp1y) + 3 * tt * tt * (yb - cp2y);
                len = (float) Math.sqrt(tdx * tdx + tdy * tdy);
            }
            if (len < 0.0001f) {
                normX[i] = 0;
                normY[i] = 0;
            } else {
                normX[i] = -tdy / len;
                normY[i] = tdx / len;
            }
        }

        // 16 floats per segment: two rail quads (top rail at [0..7], bottom rail at [8..15]).
        float[] quads = new float[steps * 16];
        float outerOff = channelHalfW + railHalfThickness;
        float innerOff = channelHalfW - railHalfThickness;

        for (int i = 0; i < steps; i++) {
            float sx = pathX[i], sy = pathY[i];
            float ex = pathX[i + 1], ey = pathY[i + 1];
            float nsx = normX[i], nsy = normY[i];
            float nex = normX[i + 1], ney = normY[i + 1];

            float sOxOuter = nsx * outerOff, sOyOuter = nsy * outerOff;
            float sOxInner = nsx * innerOff, sOyInner = nsy * innerOff;
            float eOxOuter = nex * outerOff, eOyOuter = ney * outerOff;
            float eOxInner = nex * innerOff, eOyInner = ney * innerOff;

            int b = i * 16;
            // Top rail
            quads[b] = sx + sOxOuter;
            quads[b + 1] = sy + sOyOuter;
            quads[b + 2] = sx + sOxInner;
            quads[b + 3] = sy + sOyInner;
            quads[b + 4] = ex + eOxInner;
            quads[b + 5] = ey + eOyInner;
            quads[b + 6] = ex + eOxOuter;
            quads[b + 7] = ey + eOyOuter;
            // Bottom rail (mirrored)
            quads[b + 8] = sx - sOxInner;
            quads[b + 9] = sy - sOyInner;
            quads[b + 10] = sx - sOxOuter;
            quads[b + 11] = sy - sOyOuter;
            quads[b + 12] = ex - eOxOuter;
            quads[b + 13] = ey - eOyOuter;
            quads[b + 14] = ex - eOxInner;
            quads[b + 15] = ey - eOyInner;
        }

        // Traversal time scaled to THIS edge's (trimmed) length instead of one shared constant -
        // a fixed period made short connectors between nearby nodes crawl at a much lower
        // apparent px/sec than long ones once trimming shrank their visible span, which is what
        // read as the arrow "moving like a turtle" on closely-spaced quests.
        long baseArrowPeriodMs = (long) Math.min(2200f, Math.max(350f, dist / ARROW_SPEED_PX_PER_MS));
        // speedDiv (the LineAnimSpeed setting/override) used to only affect the old marching-ants
        // dash animation, which the hollow-rail redesign dropped - so "Very Slow" etc had quietly
        // stopped doing anything visible. NORMAL's divisor (35) is the baseline the constant above
        // was tuned against, so everything else scales relative to it: SLOWEST (120) stretches the
        // period ~3.4x, VERY_FAST (7) compresses it to ~0.2x.
        long arrowPeriodMs = Math.max(80L, Math.min(8000L,
                Math.round(baseArrowPeriodMs * (speedDiv / 35.0))));

        // Arc-length remap table for arrow placement (see emitLineGeometry) - a cubic bezier
        // isn't uniform-speed, so arrows spaced evenly in t landed unevenly spaced in actual
        // screen pixels (bunched near the ends, spread out mid-curve, worse the longer/straighter
        // the edge). arcT[k] is the t-parameter whose cumulative arc length equals k/steps of the
        // curve's total length, letting arrows be placed evenly by distance instead of by t.
        float[] cumLen = new float[steps + 1];
        for (int i = 1; i <= steps; i++) {
            float ddx = pathX[i] - pathX[i - 1], ddy = pathY[i] - pathY[i - 1];
            cumLen[i] = cumLen[i - 1] + (float) Math.sqrt(ddx * ddx + ddy * ddy);
        }
        float totalLen = cumLen[steps];
        float[] arcT = new float[steps + 1];
        if (totalLen < 0.001f) {
            for (int k = 0; k <= steps; k++) arcT[k] = (float) k / steps;
        } else {
            int j = 0;
            for (int k = 0; k <= steps; k++) {
                float target = (float) k / steps * totalLen;
                while (j < steps - 1 && cumLen[j + 1] < target) j++;
                float segLen = cumLen[j + 1] - cumLen[j];
                float frac = segLen > 0.0001f ? (target - cumLen[j]) / segLen : 0f;
                arcT[k] = (j + frac) / steps;
            }
        }

        return new LineGeometry(quads, steps, lineColor,
                isSolid, dashPeriod, dashOn, effectiveSpeedDiv,
                showArrow, xa, ya, cp1x, cp1y, cp2x, cp2y, xb, yb, arrowSize, arrowColor, arrowCount, railHalfThickness,
                arrowPeriodMs, arcT);
    }

    /**
     * Queues the cached quad strip for the batched raw-Tesselator flush (flushRibbonQueue())
     * instead of writing into GuiGraphics's own deferred bufferSource()/RenderType.gui() batch.
     *
     * That vc-based path was ruled out the hard way: profiler diagnostics confirmed real,
     * substantial vertex counts were being submitted, with raw and pose-transformed coordinates
     * landing exactly where expected, well inside both the canvas and the active scissor rect,
     * with zero GL error after the flush - by every measure available from inside this mod, the
     * draw call was correct, yet nothing appeared on screen. That combination points at something
     * outside this mod's control interfering with GuiGraphics/MultiBufferSource specifically
     * (this pack runs a large number of other mods, several of which patch rendering internals).
     * The one line-adjacent rendering path that has been reliable through all of this is the
     * arrow sprite, which never went through GuiGraphics's bufferSource at all - it queues raw
     * corner data and uploads it directly via Tesselator + BufferUploader. This mirrors that
     * exact mechanism for the ribbon instead.
     */
    /**
     * animated=false freezes dash phase and arrow phase at 0 instead of deriving them from
     * animTick - every non-solid edge (locked/optional/forbidden) was re-deriving its dash
     * offset from animTick with effectiveSpeedDiv=1 (only genuinely "marching" styles slow that
     * down), which cycles the whole dashPeriod within single-digit milliseconds - faster than
     * frame time, so it read as flicker rather than motion across the ENTIRE tree simultaneously.
     * That's what prompted this: static by default, motion reserved for the hovered quest's own
     * edges (see render()).
     */
    /** Alpha-only dim for the static (non-hover) arrow pass - see emitLineGeometry's javadoc. */
    private static int dimArrowColor(int color) {
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int dimmed = Math.max(45, Math.round(a * 0.4f));
        return (dimmed << 24) | rgb;
    }

    /** Linear interpolation into an arc-length remap table (see LineGeometry.arcT's javadoc). */
    private static float lookupArcT(float[] arcT, float f) {
        float idx = Math.max(0f, Math.min(1f, f)) * (arcT.length - 1);
        int lo = (int) idx;
        int hi = Math.min(arcT.length - 1, lo + 1);
        float frac = idx - lo;
        return arcT[lo] + (arcT[hi] - arcT[lo]) * frac;
    }

    private void emitLineGeometry(GuiGraphics g, LineGeometry geo, long animTick, Integer colorOverride,
                                  boolean animated) {
        if (geo.segmentCount == 0) return;

        // Rails are always drawn continuously now, not dash-gated - the dash pattern used to cut
        // the whole ~2px-wide channel into ~30px on/off chunks, which read as broken/missing
        // rendering rather than a deliberate dashed style once the line was this thin. State is
        // now conveyed entirely by color; the arrow chain is what carries motion/direction.
        int color = colorOverride != null ? colorOverride : geo.color;
        for (int i = 0; i < geo.segmentCount; i++) {
            int b = i * 16;
            // Top rail
            queueRibbonQuad(geo.quads[b], geo.quads[b + 1],
                    geo.quads[b + 2], geo.quads[b + 3],
                    geo.quads[b + 4], geo.quads[b + 5],
                    geo.quads[b + 6], geo.quads[b + 7], color);
            // Bottom rail
            queueRibbonQuad(geo.quads[b + 8], geo.quads[b + 9],
                    geo.quads[b + 10], geo.quads[b + 11],
                    geo.quads[b + 12], geo.quads[b + 13],
                    geo.quads[b + 14], geo.quads[b + 15], color);
        }
        // Rounded end caps - rounds off the flat perpendicular cut at each node connection point
        // instead of leaving a hard square edge.
        queueRoundCap(geo.xa, geo.ya, geo.capRadius, color);
        queueRoundCap(geo.xb, geo.yb, geo.capRadius, color);

        if (geo.showArrow && geo.arrowCount > 0) {
            // FTBQ-style: a fixed fan of static chevrons when not animated (phase pinned to 0
            // reuses the exact same spacing formula, just frozen), flowing only once hovered.
            float phase = animated ? (animTick % geo.arrowPeriodMs) / (float) geo.arrowPeriodMs : 0f;
            // Static (non-hover) arrows were rendered at the same near-full alpha as the animated
            // hover ones (arrowAlpha floor of 200/255 in buildLineGeometry) - with every edge in
            // the tree showing its own full-brightness arrow chain at once, that read as constant
            // visual noise rather than a UI you could scan. Dimmed here instead of at build time
            // so the hovered pass (animated=true) still gets full brightness for contrast.
            int arrowColor = animated ? geo.arrowColor : dimArrowColor(geo.arrowColor);
            for (int i = 0; i < geo.arrowCount; i++) {
                // Nudged off the exact 0/1 endpoints (a straight, non-spline edge has zero
                // tangent right at t=0/1 since cp1==p0 and cp2==p3 in that mode) but only by
                // enough to dodge that one degenerate point, not a whole 12% of the edge's
                // length on each end - that used to leave a visibly empty stretch of rail right
                // next to the node before the arrow chain actually started, reading as a gap/
                // missing-arrows bug rather than a deliberate margin.
                float f = 0.02f + ((phase + (float) i / geo.arrowCount) % 1f) * 0.96f;
                // f is a fraction of ARC LENGTH, not of t - remapped through arcT since a cubic
                // bezier isn't uniform-speed (slow near the ends, fast mid-curve). Placing arrows
                // at evenly-spaced t values instead used to bunch them up near both nodes and
                // spread them out in the middle - "unevenly spaced" despite the formula above
                // evenly dividing [0,1].
                float t = lookupArcT(geo.arcT, f);
                float mt = 1f - t;
                float px = mt * mt * mt * geo.xa + 3 * mt * mt * t * geo.cp1x + 3 * mt * t * t * geo.cp2x +
                        t * t * t * geo.xb;
                float py = mt * mt * mt * geo.ya + 3 * mt * mt * t * geo.cp1y + 3 * mt * t * t * geo.cp2y +
                        t * t * t * geo.yb;
                // Analytic bezier derivative - exact tangent, no extra curve sampling needed.
                float dx = 3 * mt * mt * (geo.cp1x - geo.xa) + 6 * mt * t * (geo.cp2x - geo.cp1x) +
                        3 * t * t * (geo.xb - geo.cp2x);
                float dy = 3 * mt * mt * (geo.cp1y - geo.ya) + 6 * mt * t * (geo.cp2y - geo.cp1y) +
                        3 * t * t * (geo.yb - geo.cp2y);
                float dLen = (float) Math.sqrt(dx * dx + dy * dy);
                if (dLen < 0.0001f) continue;
                dx /= dLen;
                dy /= dLen;
                drawArrowSprite(g, px, py, dx, dy, geo.arrowSize, arrowColor);
            }
        }
    }

    // ── Main per-frame render ───────────────────────────────────────────────

    /**
     * Renders every cached edge as a static ribbon (no dash/arrow motion - see emitLineGeometry's
     * javadoc for why constant motion across the whole tree read as noise), then re-emits only
     * the hovered quest's own edges animated and colored by direction: edges INTO the hovered
     * quest are its dependencies/prerequisites, edges OUT of it are its dependents (what it
     * unlocks) - distinct hues so "what do I need" vs "what does this unlock" is legible at a
     * glance the moment you hover, matching the FTBQ request. hoveredNodeId/stateResolver are
     * injected since node-hover detection and quest state are screen/domain concerns.
     */
    public void render(GuiGraphics g, long animTick, ResourceLocation hoveredNodeId,
                       Function<QuestNode, QuestState> stateResolver) {
        FrameProfiler.begin("dep lines");
        FrameProfiler.setCounter("edges", lineCache.size());

        int lineCountExpected = 0, arrowCountExpected = 0, lineWidthPxTenths = -1;
        for (int i = 0; i < lineGeometryCache.size(); i++) {
            LineGeometry geo = lineGeometryCache.get(i);
            if (geo.segmentCount > 0) {
                lineCountExpected++;
                // Ground-truth width of the actual cached quad (not the formula that produced
                // it) - grabs the first real sample so we can tell "computed as invisible-thin"
                // apart from "computed fine but not drawing/showing" without guessing.
                if (lineWidthPxTenths < 0 && geo.quads.length >= 4) {
                    float w = (float) Math.hypot(geo.quads[2] - geo.quads[0],
                            geo.quads[3] - geo.quads[1]);
                    lineWidthPxTenths = Math.round(w * 10);
                }
            }
            if (geo.showArrow) arrowCountExpected += geo.arrowCount;

            // Edges touching the hovered quest are drawn ONCE, animated and direction-colored,
            // below - drawing the static pass here too as well as the animated pass down there
            // used to double up the arrow chain (a static set and a moving set overlapping and
            // sliding through each other), which was pure visual noise.
            boolean isHoverEdge = hoveredNodeId != null && i < lineCacheNodes.size() &&
                    (lineCacheNodes.get(i)[0].equals(hoveredNodeId) || lineCacheNodes.get(i)[1].equals(hoveredNodeId));
            if (!isHoverEdge) emitLineGeometry(g, geo, animTick, null, false);
        }

        // Hover line overlay processing - only the hovered quest's own edges render here, once,
        // animated and colored by direction relative to it.
        if (hoveredNodeId != null) {
            for (int i = 0; i < lineCache.size() && i < lineCacheNodes.size() && i < lineGeometryCache.size(); i++) {
                ResourceLocation[] pair = lineCacheNodes.get(i);
                int[] ln = lineCache.get(i);
                if (pair[1].equals(hoveredNodeId)) {
                    // parent -> hovered: a dependency/prerequisite of the hovered quest.
                    emitLineGeometry(g, lineGeometryCache.get(i), animTick, boostedDependencyColor(ln[4]), true);
                } else if (pair[0].equals(hoveredNodeId)) {
                    // hovered -> child: a dependent - what the hovered quest unlocks.
                    emitLineGeometry(g, lineGeometryCache.get(i), animTick, boostedDependentColor(ln[4]), true);
                }
            }
        }

        FrameProfiler.setCounter("linesExpected", lineCountExpected);
        FrameProfiler.setCounter("lineWidthPxTenths", lineWidthPxTenths);
        FrameProfiler.setCounter("arrowsExpected", arrowCountExpected);
        FrameProfiler.end("dep lines");

        FrameProfiler.begin("sparks/hover/flush");
        // Active-quest spark - also hover-gated now (used to run continuously on every edge
        // touching an active quest, which added to the constant-motion clutter).
        if (hoveredNodeId != null) {
            for (int i = 0; i < lineCache.size() && i < lineCacheNodes.size(); i++) {
                ResourceLocation[] pair = lineCacheNodes.get(i);
                if (!pair[0].equals(hoveredNodeId) && !pair[1].equals(hoveredNodeId)) continue;
                QuestNode pn = QuestTreeRegistry.getQuest(pair[0]);
                QuestNode cn = QuestTreeRegistry.getQuest(pair[1]);
                if (pn == null || cn == null) continue;
                QuestState pst = stateResolver.apply(pn), cst = stateResolver.apply(cn);
                if (pst != QuestState.ACTIVE && cst != QuestState.ACTIVE) continue;
                int[] ln = lineCache.get(i);
                drawBezierSpark(g, ln[0], ln[1], ln[2], ln[3], animTick, i);
            }
        }

        // Ribbon quads queued above upload as one raw-Tesselator draw call, same mechanism as
        // the arrow sprites (which have been reliable throughout) - bypasses GuiGraphics's own
        // deferred bufferSource()/RenderType.gui() batching entirely.
        flushRibbonQueue(g);

        // Every line above queued its arrowhead instead of drawing it immediately - upload
        // them all now as a single textured draw call.
        flushArrowQueue(g);
        FrameProfiler.end("sparks/hover/flush");
    }

    /** Live preview line drawn while dragging from a node to create a new prerequisite link. */
    public void renderLinkDragPreview(GuiGraphics g, int sx, int sy, int dragX, int dragY, long animTick, float zoom) {
        drawBezierLine(g, sx, sy, dragX, dragY, 0xFFCC44FF, 3, animTick, zoom);
        flushArrowQueue(g);
    }

    // ── Hit-testing + context menu ──────────────────────────────────────────

    public boolean isContextMenuOpen() {
        return lineCtxOpen;
    }

    public void closeContextMenu() {
        lineCtxOpen = false;
    }

    /** True when (mx,my) is within tol pixels of the cubic S-bezier from (x1,y1) to (x2,y2). */
    private boolean pointNearBezier(int mx, int my, int x1, int y1, int x2, int y2, int tol) {
        int dx = x2 - x1, dy = y2 - y1;
        int cx1 = x1 + dx / 3, cy1 = y1;
        int cx2 = x2 - dx / 3, cy2 = y2;
        int steps = Math.max(16, Math.abs(dx) / 4 + Math.abs(dy) / 4);
        int tolSq = tol * tol;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float u = 1 - t;
            float bx = u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2;
            float by = u * u * u * y1 + 3 * u * u * t * cy1 + 3 * u * t * t * cy2 + t * t * t * y2;
            int ddx = mx - (int) bx, ddy = my - (int) by;
            if (ddx * ddx + ddy * ddy <= tolSq) return true;
        }
        return false;
    }

    /**
     * If (mx,my) lands within tol px of a cached edge's curve, opens the context menu for that
     * edge at (mx,my) and returns true. Wraps the pointNearBezier hit-test loop that used to be
     * inlined in ChronicleOverviewScreen.mouseClicked().
     */
    public boolean tryOpenContextMenuAt(int mx, int my, int tol) {
        for (int i = 0; i < lineCache.size() && i < lineCacheNodes.size(); i++) {
            int[] ln = lineCache.get(i);
            if (pointNearBezier(mx, my, ln[0], ln[1], ln[2], ln[3], tol)) {
                lineCtxOpen = true;
                lineCtxX = mx;
                lineCtxY = my;
                lineCtxParentId = lineCacheNodes.get(i)[0];
                lineCtxChildId = lineCacheNodes.get(i)[1];
                return true;
            }
        }
        return false;
    }

    // ── Line context menu ────────────────────────────────────────────────────

    public void renderContextMenu(GuiGraphics g, Font font, double mx, double my, int screenW, int screenH) {
        QuestNode parentNode = lineCtxParentId == null ? null : QuestTreeRegistry.getQuest(lineCtxParentId);
        QuestNode childNode = lineCtxChildId == null ? null : QuestTreeRegistry.getQuest(lineCtxChildId);
        if (parentNode == null || childNode == null) {
            lineCtxOpen = false;
            return;
        }

        boolean isForbidden = childNode.isPrereqForbidden(lineCtxParentId);
        boolean isRequired = !isForbidden && childNode.isPrereqRequired(lineCtxParentId);
        boolean isLink = childNode.isPrereqLink(lineCtxParentId);
        boolean isCosmetic = childNode.isPrereqCosmetic(lineCtxParentId);

        // 3-way cycle label: required → optional → forbidden → required
        String cycleLabel;
        if (isForbidden) cycleLabel = "§aType: Forbidden  →  Required";
        else if (!isRequired) cycleLabel = "§cType: Optional  →  Forbidden";
        else cycleLabel = "§eType: Required  →  Optional";

        String linkLabel = isLink ? "§7Unmark as link" : "§dMark as link";
        String cosmeticLabel = isCosmetic ? "§7Unmark as cosmetic-only" : "§6Mark as cosmetic-only (no gate)";
        boolean childHidesLines = childNode.isHideDepLine();
        String hideLabel = childHidesLines ? "§aShow dep lines: " + childNode.getTitle().getString() :
                "§8Hide dep lines: " + childNode.getTitle().getString();

        String shapeLabel = "§7Line shape: §f" + lineOverrideLabel(childNode.getPrereqLineShape(lineCtxParentId));
        String visualLabel = "§7Line style: §f" + lineOverrideLabel(childNode.getPrereqLineVisual(lineCtxParentId));
        String speedLabel = "§7Dot speed: §f" + lineOverrideLabel(childNode.getPrereqLineSpeed(lineCtxParentId));
        String arrowLabel = "§7Arrow: §f" + arrowOverrideLabel(childNode.getPrereqLineArrow(lineCtxParentId));

        String[] labels = {
                "§cRemove connection",
                cycleLabel,
                linkLabel,
                cosmeticLabel,
                hideLabel,
                shapeLabel,
                visualLabel,
                speedLabel,
                arrowLabel,
                "§bDependency line settings…"
        };
        int menuW = 210, itemH = 14, pad = 4;
        int menuH = pad + labels.length * itemH + pad;
        int lx = lineCtxX, ly = lineCtxY;
        if (lx + menuW > screenW) lx = screenW - menuW - 2;
        if (ly + menuH > screenH) ly = screenH - menuH - 2;

        g.fill(lx, ly, lx + menuW, ly + menuH, 0xEE0D0D12);
        g.fill(lx, ly, lx + menuW, ly + 1, 0xFF9900FF);
        g.fill(lx, ly, lx + 1, ly + menuH, 0xFF9900FF);
        g.fill(lx + menuW - 1, ly, lx + menuW, ly + menuH, 0xFF9900FF);
        g.fill(lx, ly + menuH - 1, lx + menuW, ly + menuH, 0xFF9900FF);

        for (int i = 0; i < labels.length; i++) {
            int iy = ly + pad + i * itemH;
            boolean hov = mx >= lx && mx < lx + menuW && my >= iy && my < iy + itemH;
            if (hov) g.fill(lx + 1, iy, lx + menuW - 1, iy + itemH, 0x44FFFFFF);
            g.drawString(font, labels[i], lx + 6, iy + 2, 0xFFDDDDDD, false);
        }
    }

    private static String lineOverrideLabel(Enum<?> override) {
        return override == null ? "§8Inherit" : override.name();
    }

    private static String arrowOverrideLabel(Boolean override) {
        return override == null ? "§8Inherit" : (override ? "ON" : "OFF");
    }

    /** Cycles null → true → false → null. */
    private static Boolean cycleArrowOverride(Boolean current) {
        if (current == null) return Boolean.TRUE;
        return current ? Boolean.FALSE : null;
    }

    /** Cycles null → values[0] → values[1] → ... → values[last] → null. */
    private static <T extends Enum<T>> T cycleLineOverride(T current, T[] values) {
        if (current == null) return values[0];
        int next = current.ordinal() + 1;
        return next < values.length ? values[next] : null;
    }

    /**
     * requestRebuild/feedback/openLineSettings replace the direct rebuild()/setFeedback()/
     * minecraft.setScreen(new DepLineSettingsScreen(...)) calls this used to make directly on
     * ChronicleOverviewScreen, so this class doesn't need a back-reference to the screen.
     */
    public void handleContextMenuClick(int mx, int my, int screenW, int screenH,
                                       Runnable requestRebuild, Consumer<String> feedback,
                                       Consumer<QuestNode> openLineSettings) {
        if (!lineCtxOpen) return;
        QuestNode parentNode = lineCtxParentId == null ? null : QuestTreeRegistry.getQuest(lineCtxParentId);
        QuestNode childNode = lineCtxChildId == null ? null : QuestTreeRegistry.getQuest(lineCtxChildId);
        if (parentNode == null || childNode == null) {
            lineCtxOpen = false;
            return;
        }

        boolean isForbidden = childNode.isPrereqForbidden(lineCtxParentId);
        boolean isRequired = !isForbidden && childNode.isPrereqRequired(lineCtxParentId);
        boolean isLink = childNode.isPrereqLink(lineCtxParentId);
        boolean isCosmetic = childNode.isPrereqCosmetic(lineCtxParentId);
        int menuW = 210, itemH = 14, pad = 4;
        int menuH = pad + 10 * itemH + pad;
        int lx = lineCtxX, ly = lineCtxY;
        if (lx + menuW > screenW) lx = screenW - menuW - 2;
        if (ly + menuH > screenH) ly = screenH - menuH - 2;

        if (mx < lx || mx >= lx + menuW || my < ly || my >= ly + menuH) {
            lineCtxOpen = false;
            return;
        }

        int idx = (my - ly - pad) / itemH;
        lineCtxOpen = false;

        if (idx == 0) {
            // Remove connection: child removes parentNode as a prereq; also remove parent→child edge
            childNode.removePrerequisite(parentNode);
            parentNode.removeChild(childNode);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Removed: " + parentNode.getTitle().getString() + " → " + childNode.getTitle().getString());
        } else if (idx == 1) {
            // 3-way cycle: required → optional → forbidden → required
            if (isForbidden) {
                childNode.setPrereqForbidden(lineCtxParentId, false);
                childNode.setPrereqRequired(lineCtxParentId, true);
                feedback.accept("Prereq type: Required");
            } else if (!isRequired) {
                childNode.setPrereqForbidden(lineCtxParentId, true);
                feedback.accept("Prereq type: Forbidden (must NOT be completed)");
            } else {
                childNode.setPrereqRequired(lineCtxParentId, false);
                feedback.accept("Prereq type: Optional");
            }
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
        } else if (idx == 2) {
            // Toggle link marker
            childNode.setPrereqLink(lineCtxParentId, !isLink);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept(isLink ? "Unmarked as link" : "Marked as link");
        } else if (idx == 3) {
            // Toggle cosmetic-only marker — when set, this edge is drawn but never gates unlock
            childNode.setPrereqCosmetic(lineCtxParentId, !isCosmetic);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept(isCosmetic ? "Unmarked as cosmetic-only (now gates unlock)" :
                    "Marked as cosmetic-only (no longer gates unlock)");
        } else if (idx == 4) {
            // Toggle dep line visibility for the child quest
            childNode.setHideDepLine(!childNode.isHideDepLine());
            QuestFileSaver.updateHideDepLine(childNode);
            requestRebuild.run();
            feedback.accept(childNode.isHideDepLine() ? "Dep lines hidden: " + childNode.getTitle().getString() :
                    "Dep lines shown: " + childNode.getTitle().getString());
        } else if (idx == 5) {
            // Cycle this edge's line-shape override: Inherit → Spline → Straight → Inherit
            var next = cycleLineOverride(childNode.getPrereqLineShape(lineCtxParentId),
                    QuestChroniclesSettings.LineStyle.values());
            childNode.setPrereqLineShape(lineCtxParentId, next);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Line shape: " + lineOverrideLabel(next));
        } else if (idx == 6) {
            // Cycle this edge's line-style override: Inherit → Thin → Normal → ... → Glow → Inherit
            var next = cycleLineOverride(childNode.getPrereqLineVisual(lineCtxParentId),
                    QuestChroniclesSettings.LineVisualStyle.values());
            childNode.setPrereqLineVisual(lineCtxParentId, next);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Line style: " + lineOverrideLabel(next));
        } else if (idx == 7) {
            // Cycle this edge's dot-speed override: Inherit → Slowest → ... → Very Fast → Inherit
            var next = cycleLineOverride(childNode.getPrereqLineSpeed(lineCtxParentId),
                    QuestChroniclesSettings.LineAnimSpeed.values());
            childNode.setPrereqLineSpeed(lineCtxParentId, next);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Dot speed: " + lineOverrideLabel(next));
        } else if (idx == 8) {
            // Cycle this edge's directional-arrow override: Inherit → On → Off → Inherit
            Boolean next = cycleArrowOverride(childNode.getPrereqLineArrow(lineCtxParentId));
            childNode.setPrereqLineArrow(lineCtxParentId, next);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Arrow: " + arrowOverrideLabel(next));
        } else if (idx == 9) {
            // Open dep line settings screen, scoped to the parent quest of this edge + its dependents
            openLineSettings.accept(parentNode);
        }
    }

    /** Cool cyan boost for edges INTO the hovered quest - its dependencies/prerequisites. */
    private static int boostedDependencyColor(int col) {
        return blendTowardHue(col, 0x33CFFF);
    }

    /** Warm amber boost for edges OUT of the hovered quest - its dependents (what it unlocks). */
    private static int boostedDependentColor(int col) {
        return blendTowardHue(col, 0xFFAA33);
    }

    /**
     * Blends a line's existing state color partway toward a target hue rather than just
     * brightening it uniformly - a flat "+90 per channel" boost barely changes hue (locked vs
     * done vs active already look very different from each other), so dependency/dependent
     * edges wouldn't read as consistently different colors from one another. Blending toward a
     * fixed hue does, regardless of the edge's underlying state color.
     */
    private static int blendTowardHue(int col, int targetRgb) {
        int alpha = Math.max(200, (col >>> 24) & 0xFF);
        int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
        int tr = (targetRgb >> 16) & 0xFF, tg = (targetRgb >> 8) & 0xFF, tb = targetRgb & 0xFF;
        float a = 0.6f;
        int nr = Math.min(255, Math.round(r + (tr - r) * a));
        int ng = Math.min(255, Math.round(g + (tg - g) * a));
        int nb = Math.min(255, Math.round(b + (tb - b) * a));
        return (alpha << 24) | (nr << 16) | (ng << 8) | nb;
    }

    // ── Uncached bezier draw (link-drag preview only) ───────────────────────

    private void drawBezierLine(GuiGraphics g, int x1, int y1, int x2, int y2,
                                int color, int style, long animTick, float zoom) {
        drawBezierLine(g, x1, y1, x2, y2, color, style, animTick, zoom, null, null, null, null);
    }

    private void drawBezierLine(GuiGraphics g, int x1, int y1, int x2, int y2,
                                int color, int style, long animTick, float zoom,
                                QuestChroniclesSettings.LineStyle shapeOverride,
                                QuestChroniclesSettings.LineVisualStyle visualOverride,
                                QuestChroniclesSettings.LineAnimSpeed speedOverride,
                                Boolean arrowOverride) {
        QuestChroniclesSettings settings = QuestChroniclesSettings.get();
        boolean spline = (shapeOverride != null ? shapeOverride : settings.getLineStyle()) ==
                QuestChroniclesSettings.LineStyle.SPLINE;
        QuestChroniclesSettings.LineVisualStyle vis = visualOverride != null ? visualOverride :
                settings.getLineVisualStyle();
        long speedDiv = (speedOverride != null ? speedOverride : settings.getLineAnimSpeed()).divisor;
        boolean showArrow = arrowOverride != null ? arrowOverride : settings.isShowLineArrows();
        drawBezierLine(g, x1, y1, x2, y2, color, style, animTick, spline, vis, speedDiv, showArrow, zoom);
    }

    private void drawBezierLine(GuiGraphics g, int x1, int y1, int x2, int y2,
                                int color, int style, long animTick, boolean spline,
                                QuestChroniclesSettings.LineVisualStyle vis, long speedDiv, boolean showArrow,
                                float zoom) {
        // Cleanly cast inputs to local GUI floats to prevent rounding gaps
        float xa = (float) x1;
        float ya = (float) y1;
        float xb = (float) x2;
        float yb = (float) y2;

        // Calculate elegant control points for the S-curve directly in GUI space
        float adx = Math.abs(xb - xa), ady = Math.abs(yb - ya);
        float cp1x, cp1y, cp2x, cp2y;
        if (!spline) {
            cp1x = xa;
            cp1y = ya;
            cp2x = xb;
            cp2y = yb;
        } else if (adx >= ady) {
            float mx = (xa + xb) / 2f;
            cp1x = mx;
            cp1y = ya;
            cp2x = mx;
            cp2y = yb;
        } else {
            float my = (ya + yb) / 2f;
            cp1x = xa;
            cp1y = my;
            cp2x = xb;
            cp2y = yb;
        }

        float dist = (float) Math.sqrt(adx * adx + ady * ady);
        if (dist < 0.5f) return;

        // Fixed step allocation: Ensures steps map to physical length so dashes never stretch
        int steps = Math.min(Math.max(16, (int) (dist / 4.0f)), 64);

        // Unpack colors efficiently
        int alpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        // Base thickness definitions in GUI space
        float baseCoreW = switch (vis) {
            case THIN -> 0.4f;
            case BOLD -> 1.2f;
            case THICK -> 1.8f;
            case WIDE -> 2.6f;
            case GLOW -> 0.9f;
            default -> 0.75f; // NORMAL
        };

        // FTB QUESTS VISIBILITY MECHANIC:
        // As you zoom out, lines naturally look thinner. We dynamically increase their width
        // in GUI space here so they never drop below a readable, high-contrast physical thickness.
        float currentZoom = Math.max(0.05f, zoom);
        float coreHalfW = Math.max(baseCoreW, Math.min(1.4f, 0.4f / currentZoom));
        float outlineHalfW = coreHalfW + Math.min(0.9f, 0.45f / currentZoom);

        // Setup standard dash pacing rules
        boolean isSolid = (style == 1 || style == 6 || style == 8);
        boolean isMarching = (style == 2 || style == 9);
        int dashPeriod = 8, dashOn = 5;
        if (style == 0) {
            dashPeriod = 10;
            dashOn = 3;
        } else if (style == 3 || style == 4) {
            dashPeriod = 14;
            dashOn = 6;
        } else if (style == 5) {
            dashPeriod = 8;
            dashOn = 3;
        } else if (style == 7 || style == 9) {
            dashPeriod = 20;
            dashOn = 5;
        } else if (style == 10) {
            dashPeriod = 16;
            dashOn = 2;
        }

        long effectiveSpeedDiv = isMarching ? speedDiv : 1L;
        int dashOffset = (int) ((animTick / effectiveSpeedDiv) % dashPeriod);

        // Near-black at ~15-20% opacity (the old floor of 30/255) reads as basically invisible
        // against this screen's near-black canvas background - bump the floor hard so the
        // casing actually shows as a distinct dark border regardless of what's behind it.
        int outlineAlpha = Math.min(255, Math.max(170, alpha));
        int outlineRgb = QuestChroniclesSettings.get().getTheme() == QuestChroniclesSettings.Theme.LIGHT ? 0x1A1A1A :
                0xEDEDED;
        int outlineColor = (outlineAlpha << 24) | outlineRgb;

        // Fetch the running batch stream consumer and local screen matrix context
        com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource()
                .getBuffer(net.minecraft.client.renderer.RenderType.gui());
        org.joml.Matrix4f mat = g.pose().last().pose();

        // Pre-calculate path segments to ensure lightning fast execution loops
        float[] pathX = new float[steps + 1];
        float[] pathY = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float mt = 1f - t;
            pathX[i] = mt * mt * mt * xa + 3 * mt * mt * t * cp1x + 3 * mt * t * t * cp2x + t * t * t * xb;
            pathY[i] = mt * mt * mt * ya + 3 * mt * mt * t * cp1y + 3 * mt * t * t * cp2y + t * t * t * yb;
        }

        // PASS 1: High-Contrast Background Alignment Ribbon
        if (outlineAlpha > 0) {
            for (int i = 0; i < steps; i++) {
                float sx = pathX[i], sy = pathY[i];
                float ex = pathX[i + 1], ey = pathY[i + 1];

                float dx = ex - sx, dy = ey - sy;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 0.01f) continue;

                float nx = -dy / len;
                float ny = dx / len;

                float ox = nx * outlineHalfW;
                float oy = ny * outlineHalfW;

                writeVert(vc, mat, sx + ox, sy + oy, outlineColor);
                writeVert(vc, mat, sx - ox, sy - oy, outlineColor);
                writeVert(vc, mat, ex - ox, ey - oy, outlineColor);
                writeVert(vc, mat, ex + ox, ey + oy, outlineColor);
            }
        }

        // PASS 2: Core Ribbon Pathing (With clean directional orientation vectors)
        for (int i = 0; i < steps; i++) {
            if (!isSolid && ((i + dashOffset) % dashPeriod) >= dashOn) continue;

            float sx = pathX[i], sy = pathY[i];
            float ex = pathX[i + 1], ey = pathY[i + 1];

            float dx = ex - sx, dy = ey - sy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.01f) continue;

            float nx = -dy / len;
            float ny = dx / len;

            float cx = nx * coreHalfW;
            float cy = ny * coreHalfW;

            int segmentColor = (alpha << 24) | rgb;

            writeVert(vc, mat, sx + cx, sy + cy, segmentColor);
            writeVert(vc, mat, sx - cx, sy - cy, segmentColor);
            writeVert(vc, mat, ex - cx, ey - cy, segmentColor);
            writeVert(vc, mat, ex + cx, ey + cy, segmentColor);
        }

        // Pass 3: Arrowhead sprite (batched — see drawArrowSprite)
        if (showArrow) {
            // Grows at low zoom so the arrow stays legible, but capped hard - otherwise at very
            // low zoom (e.g. 12%) this blows up past 40px and swallows the nodes it points at.
            float arrowSize = Math.min(9f, 5.0f / currentZoom);
            float tipT = dist > 0.1f ? Math.max(0.5f, 1f - (arrowSize * 1.5f) / dist) : 0.5f;
            float mt2 = 1f - tipT;
            float tipX = mt2 * mt2 * mt2 * xa + 3 * mt2 * mt2 * tipT * cp1x + 3 * mt2 * tipT * tipT * cp2x +
                    tipT * tipT * tipT * xb;
            float tipY = mt2 * mt2 * mt2 * ya + 3 * mt2 * mt2 * tipT * cp1y + 3 * mt2 * tipT * tipT * cp2y +
                    tipT * tipT * tipT * yb;

            float dirX = xb - cp2x, dirY = yb - cp2y;
            float dLen = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (dLen > 0.01f) {
                dirX /= dLen;
                dirY /= dLen;
                int arrowAlpha = Math.max(alpha, 200);
                int arrowColor = (arrowAlpha << 24) | rgb;
                drawArrowSprite(g, tipX, tipY, dirX, dirY, arrowSize, arrowColor);
            }
        }
    }

    private static void writeVert(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f mat, float x, float y,
                                  int color) {
        vc.vertex(mat, x, y, 0.0f)
                .color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
    }

    /** Draws a single bright spark dot traveling from (x1,y1) to (x2,y2) along the S-bezier. */
    private void drawBezierSpark(GuiGraphics g, int x1, int y1, int x2, int y2, long animMs, int lineIdx) {
        float adx = Math.abs(x2 - x1), ady = Math.abs(y2 - y1);
        float cp1x, cp1y, cp2x, cp2y;
        if (adx >= ady) {
            float mx = (x1 + x2) / 2f;
            cp1x = mx;
            cp1y = y1;
            cp2x = mx;
            cp2y = y2;
        } else {
            float my = (y1 + y2) / 2f;
            cp1x = x1;
            cp1y = my;
            cp2x = x2;
            cp2y = my;
        }
        // Each line gets its own offset so sparks don't all sync
        float t = ((animMs / 1800f) + lineIdx * 0.37f) % 1f;
        float mt = 1f - t;
        int bx = Math.round(mt * mt * mt * x1 + 3 * mt * mt * t * cp1x + 3 * mt * t * t * cp2x + t * t * t * x2);
        int by = Math.round(mt * mt * mt * y1 + 3 * mt * mt * t * cp1y + 3 * mt * t * t * cp2y + t * t * t * y2);
        // Bright core + soft halo
        g.fill(bx - 1, by - 1, bx + 2, by + 2, 0xFFFFEE88);
        g.fill(bx - 2, by - 2, bx + 3, by + 3, 0x44FFCC44);
    }

    // ── Batched ribbon quads ────────────────────────────────────────────────

    /**
     * One frame's queued arrowhead instances. RenderType.guiTextured(...) doesn't exist on
     * 1.20.1 (it's part of the later GuiGraphics rework) and GuiGraphics.blit() on this
     * version issues its own immediate Tesselator draw per call rather than batching through
     * bufferSource() - so textured quads here can't just get "another getBuffer() call for the
     * same RenderType" the way the untextured line ribbon does. Instead every arrow queues its
     * corner data here, and flushArrowQueue() builds ONE BufferBuilder covering every arrow on
     * screen and uploads it in a single draw call, which is what's actually being asked for.
     */
    private static final class ArrowInstance {

        final float tipX, tipY, dirX, dirY, halfSize;
        final int color;

        ArrowInstance(float tipX, float tipY, float dirX, float dirY, float halfSize, int color) {
            this.tipX = tipX;
            this.tipY = tipY;
            this.dirX = dirX;
            this.dirY = dirY;
            this.halfSize = halfSize;
            this.color = color;
        }
    }

    /** Queues one arrowhead instance for the batched flush at the end of the canvas pass. */
    private void drawArrowSprite(GuiGraphics g, float tipX, float tipY, float dirX, float dirY,
                                 float halfSize, int color) {
        arrowQueue.add(new ArrowInstance(tipX, tipY, dirX, dirY, halfSize, color));
    }

    /** One queued ribbon (outline or core) quad - four corners, already screen-space, one color. */
    private static final class RibbonQuad {

        final float x0, y0, x1, y1, x2, y2, x3, y3;
        final int color;

        RibbonQuad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.x3 = x3;
            this.y3 = y3;
            this.color = color;
        }
    }

    private void queueRibbonQuad(float x0, float y0, float x1, float y1,
                                 float x2, float y2, float x3, float y3, int color) {
        ribbonQueue.add(new RibbonQuad(x0, y0, x1, y1, x2, y2, x3, y3, color));
    }

    /**
     * Approximates a filled circle as a fan of thin triangular wedges (degenerate quads - two
     * corners collapsed to the center point), queued through the same ribbon batch. Good enough
     * at the small radii these end caps use to read as genuinely rounded rather than square.
     */
    private void queueRoundCap(float cx, float cy, float radius, int color) {
        if (radius < 0.6f) return;
        int segs = 8;
        for (int i = 0; i < segs; i++) {
            double a0 = i * 2 * Math.PI / segs;
            double a1 = (i + 1) * 2 * Math.PI / segs;
            float x1 = cx + (float) (radius * Math.cos(a0)), y1 = cy + (float) (radius * Math.sin(a0));
            float x2 = cx + (float) (radius * Math.cos(a1)), y2 = cy + (float) (radius * Math.sin(a1));
            queueRibbonQuad(cx, cy, x1, y1, x2, y2, cx, cy, color);
        }
    }

    /**
     * Uploads every queued ribbon quad (outline + core, every edge) as one raw Tesselator draw
     * call, mirroring flushArrowQueue()'s mechanism exactly - untextured, so this uses
     * POSITION_COLOR instead of POSITION_TEX_COLOR, but otherwise the identical approach.
     */
    private void flushRibbonQueue(GuiGraphics g) {
        FrameProfiler.setCounter("ribbonQuadsQueued", ribbonQueue.size());
        if (ribbonQueue.isEmpty()) return;
        org.joml.Matrix4f mat = g.pose().last().pose();

        g.flush();

        // renderNodesAndDetails() does full 3D item-model rendering for every node with an item
        // icon - and cube models legitimately rely on backface culling to hide interior faces.
        // If that gets left enabled afterward, these quads (whose winding, unlike the arrow
        // sprite's, comes out consistently back-facing along a smooth path) would be silently
        // culled in their entirety while the arrow sprite - built from a different, front-facing
        // winding - still shows fine. 2D UI never needs culling, so just make sure it's off here
        // regardless of what 3D item rendering left behind.
        com.mojang.blaze3d.systems.RenderSystem.disableCull();

        com.mojang.blaze3d.systems.RenderSystem
                .setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder bb = tesselator.getBuilder();
        bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);

        for (RibbonQuad q : ribbonQueue) {
            int alpha = (q.color >>> 24) & 0xFF;
            int r = (q.color >>> 16) & 0xFF;
            int gg = (q.color >>> 8) & 0xFF;
            int b = q.color & 0xFF;
            bb.vertex(mat, q.x0, q.y0, 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, q.x1, q.y1, 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, q.x2, q.y2, 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, q.x3, q.y3, 0f).color(r, gg, b, alpha).endVertex();
        }

        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
        ribbonQueue.clear();
    }

    /**
     * Uploads every queued arrowhead as a single textured draw call: one shader bind, one
     * texture bind, one BufferBuilder, one BufferUploader.drawWithShader(...). Must be called
     * once per frame after every line has had a chance to queue its arrow.
     */
    private void flushArrowQueue(GuiGraphics g) {
        FrameProfiler.setCounter("arrowsQueued", arrowQueue.size());
        if (arrowQueue.isEmpty()) return;
        org.joml.Matrix4f mat = g.pose().last().pose();

        // RenderType.gui() (used by the line ribbon above) isn't one of GuiGraphics's
        // pre-allocated "fixed" buffers, so it falls back to sharing the exact same
        // Tesselator.getInstance().getBuilder() instance we're about to grab directly below.
        // If the "gui" batch is still pending (unflushed) when we call .begin() on that same
        // builder ourselves, it either throws ("already building") or silently discards the
        // pending line-ribbon vertices - which is exactly what was making the dep lines vanish.
        // Flushing here drains that pending batch first so the shared builder is safe to reuse.
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();

        com.mojang.blaze3d.systems.RenderSystem
                .setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, ARROW_SPRITE);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder bb = tesselator.getBuilder();
        bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);

        for (ArrowInstance a : arrowQueue) {
            int alpha = (a.color >>> 24) & 0xFF;
            int r = (a.color >>> 16) & 0xFF;
            int gg = (a.color >>> 8) & 0xFF;
            int b = a.color & 0xFF;

            // Forward = direction the arrow points; right = perpendicular, for the sprite's local axes
            float fx = a.dirX, fy = a.dirY;
            float rx = -a.dirY, ry = a.dirX;
            float half = a.halfSize;
            // arrow_no_background.png is a 45x75 portrait sprite - stretching it into a square
            // quad (equal length/width) is what made it read as a shapeless blob instead of a
            // crisp chevron. Keep the source aspect so it stays a recognizable arrow shape.
            float halfLen = half;
            float halfWid = half * (45f / 75f);

            // Quad centre sits half a sprite-length behind the tip, so the sprite's forward edge
            // (v=0 in the texture) lands exactly on the tip rather than the quad's centre.
            float cx = a.tipX - fx * halfLen;
            float cy = a.tipY - fy * halfLen;

            float tlX = cx - rx * halfWid + fx * halfLen, tlY = cy - ry * halfWid + fy * halfLen;
            float trX = cx + rx * halfWid + fx * halfLen, trY = cy + ry * halfWid + fy * halfLen;
            float brX = cx + rx * halfWid - fx * halfLen, brY = cy + ry * halfWid - fy * halfLen;
            float blX = cx - rx * halfWid - fx * halfLen, blY = cy - ry * halfWid - fy * halfLen;

            bb.vertex(mat, tlX, tlY, 0f).uv(0f, 0f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, blX, blY, 0f).uv(0f, 1f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, brX, brY, 0f).uv(1f, 1f).color(r, gg, b, alpha).endVertex();
            bb.vertex(mat, trX, trY, 0f).uv(1f, 0f).color(r, gg, b, alpha).endVertex();
        }

        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
        arrowQueue.clear();
    }
}
