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

public class DependencyLineRenderer {

    private static final float[] EMPTY_FLOATS = new float[0];
    private static final int TRIM_SAMPLE_STEPS = 48;

    private static final float TRIM_OVERLAP_PX = 2f;

    private static final float ARROW_SPEED_PX_PER_MS = 0.15f;

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

    public void rebuild(List<int[]> edges, List<ResourceLocation[]> edgeNodes,
                        float zoom, float nodeSizePx, QuestChroniclesSettings settings) {
        lineCache.clear();
        lineCache.addAll(edges);
        lineCacheNodes.clear();
        lineCacheNodes.addAll(edgeNodes);
        rebuildLineGeometryCache(zoom, nodeSizePx, settings);
    }

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

    private static final class LineGeometry {

        final float[] quads;
        final int segmentCount;
        final int color;
        final boolean isSolid;
        final int dashPeriod;
        final int dashOn;
        final long effectiveSpeedDiv;
        
        final boolean showArrow;
        float xa, ya, cp1x, cp1y, cp2x, cp2y, xb, yb;
        final float arrowSize;
        final int arrowColor;
        final int arrowCount;
        
        final float capRadius;
        
        final long arrowPeriodMs;
        
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

    private static float[] bezierSubArc(float xa, float ya, float cp1x, float cp1y,
                                        float cp2x, float cp2y, float xb, float yb,
                                        float t0, float t1) {
        float[] left = bezierLeftHalf(xa, ya, cp1x, cp1y, cp2x, cp2y, xb, yb, t1);
        float t0n = t1 > 1e-6f ? t0 / t1 : 0f;
        return bezierRightHalf(left[0], left[1], left[2], left[3], left[4], left[5], left[6], left[7], t0n);
    }

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

        float nodeRadius = nodeSizePx / 2f;
        float trimHalfSize = Math.max(0.5f, nodeRadius - TRIM_OVERLAP_PX);
        float[] trimRange = findTrimRange(xa, ya, cp1x, cp1y, cp2x, cp2y, xb, yb, trimHalfSize, trimHalfSize);
        if (trimRange == null) {

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
            default -> 0.75f; 
        };

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

        float railHalfThickness = Math.min(2.0f, Math.max(0.35f, widthScale * 0.9f));

        float arrowSize = Math.min(7.0f, Math.max(0.9f, widthScale * 1.6f));
        float arrowHalfWidth = arrowSize * (45f / 75f);
        float gapHalf = arrowHalfWidth * 1.2f;
        float channelHalfW = railHalfThickness + gapHalf;
        int arrowAlpha = Math.max(alpha, 200);
        int arrowColor = (arrowAlpha << 24) | rgb;

        int arrowCount = showArrow ? Math.min(80, Math.max(1, Math.round(dist / (arrowSize * 2.1f)))) : 0;

        float[] pathX = new float[steps + 1];
        float[] pathY = new float[steps + 1];

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
            
            quads[b] = sx + sOxOuter;
            quads[b + 1] = sy + sOyOuter;
            quads[b + 2] = sx + sOxInner;
            quads[b + 3] = sy + sOyInner;
            quads[b + 4] = ex + eOxInner;
            quads[b + 5] = ey + eOyInner;
            quads[b + 6] = ex + eOxOuter;
            quads[b + 7] = ey + eOyOuter;
            
            quads[b + 8] = sx - sOxInner;
            quads[b + 9] = sy - sOyInner;
            quads[b + 10] = sx - sOxOuter;
            quads[b + 11] = sy - sOyOuter;
            quads[b + 12] = ex - eOxOuter;
            quads[b + 13] = ey - eOyOuter;
            quads[b + 14] = ex - eOxInner;
            quads[b + 15] = ey - eOyInner;
        }

        long baseArrowPeriodMs = (long) Math.min(2200f, Math.max(350f, dist / ARROW_SPEED_PX_PER_MS));

        long arrowPeriodMs = Math.max(80L, Math.min(8000L,
                Math.round(baseArrowPeriodMs * (speedDiv / 35.0))));

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

    private static int dimArrowColor(int color) {
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int dimmed = Math.max(45, Math.round(a * 0.4f));
        return (dimmed << 24) | rgb;
    }

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

        int color = colorOverride != null ? colorOverride : geo.color;
        for (int i = 0; i < geo.segmentCount; i++) {
            int b = i * 16;
            
            queueRibbonQuad(geo.quads[b], geo.quads[b + 1],
                    geo.quads[b + 2], geo.quads[b + 3],
                    geo.quads[b + 4], geo.quads[b + 5],
                    geo.quads[b + 6], geo.quads[b + 7], color);
            
            queueRibbonQuad(geo.quads[b + 8], geo.quads[b + 9],
                    geo.quads[b + 10], geo.quads[b + 11],
                    geo.quads[b + 12], geo.quads[b + 13],
                    geo.quads[b + 14], geo.quads[b + 15], color);
        }

        queueRoundCap(geo.xa, geo.ya, geo.capRadius, color);
        queueRoundCap(geo.xb, geo.yb, geo.capRadius, color);

        if (geo.showArrow && geo.arrowCount > 0) {

            float phase = animated ? (animTick % geo.arrowPeriodMs) / (float) geo.arrowPeriodMs : 0f;

            int arrowColor = animated ? geo.arrowColor : dimArrowColor(geo.arrowColor);
            for (int i = 0; i < geo.arrowCount; i++) {

                float f = 0.02f + ((phase + (float) i / geo.arrowCount) % 1f) * 0.96f;

                float t = lookupArcT(geo.arcT, f);
                float mt = 1f - t;
                float px = mt * mt * mt * geo.xa + 3 * mt * mt * t * geo.cp1x + 3 * mt * t * t * geo.cp2x +
                        t * t * t * geo.xb;
                float py = mt * mt * mt * geo.ya + 3 * mt * mt * t * geo.cp1y + 3 * mt * t * t * geo.cp2y +
                        t * t * t * geo.yb;
                
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

    public void render(GuiGraphics g, long animTick, ResourceLocation hoveredNodeId,
                       Function<QuestNode, QuestState> stateResolver) {
        FrameProfiler.begin("dep lines");
        FrameProfiler.setCounter("edges", lineCache.size());

        int lineCountExpected = 0, arrowCountExpected = 0, lineWidthPxTenths = -1;
        for (int i = 0; i < lineGeometryCache.size(); i++) {
            LineGeometry geo = lineGeometryCache.get(i);
            if (geo.segmentCount > 0) {
                lineCountExpected++;

                if (lineWidthPxTenths < 0 && geo.quads.length >= 4) {
                    float w = (float) Math.hypot(geo.quads[2] - geo.quads[0],
                            geo.quads[3] - geo.quads[1]);
                    lineWidthPxTenths = Math.round(w * 10);
                }
            }
            if (geo.showArrow) arrowCountExpected += geo.arrowCount;

            boolean isHoverEdge = hoveredNodeId != null && i < lineCacheNodes.size() &&
                    (lineCacheNodes.get(i)[0].equals(hoveredNodeId) || lineCacheNodes.get(i)[1].equals(hoveredNodeId));
            if (!isHoverEdge) emitLineGeometry(g, geo, animTick, null, false);
        }

        if (hoveredNodeId != null) {
            for (int i = 0; i < lineCache.size() && i < lineCacheNodes.size() && i < lineGeometryCache.size(); i++) {
                ResourceLocation[] pair = lineCacheNodes.get(i);
                int[] ln = lineCache.get(i);
                if (pair[1].equals(hoveredNodeId)) {
                    
                    emitLineGeometry(g, lineGeometryCache.get(i), animTick, boostedDependencyColor(ln[4]), true);
                } else if (pair[0].equals(hoveredNodeId)) {
                    
                    emitLineGeometry(g, lineGeometryCache.get(i), animTick, boostedDependentColor(ln[4]), true);
                }
            }
        }

        FrameProfiler.setCounter("linesExpected", lineCountExpected);
        FrameProfiler.setCounter("lineWidthPxTenths", lineWidthPxTenths);
        FrameProfiler.setCounter("arrowsExpected", arrowCountExpected);
        FrameProfiler.end("dep lines");

        FrameProfiler.begin("sparks/hover/flush");

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

        flushRibbonQueue(g);

        flushArrowQueue(g);
        FrameProfiler.end("sparks/hover/flush");
    }

    public void renderLinkDragPreview(GuiGraphics g, int sx, int sy, int dragX, int dragY, long animTick, float zoom) {
        drawBezierLine(g, sx, sy, dragX, dragY, 0xFFCC44FF, 3, animTick, zoom);
        flushArrowQueue(g);
    }

    public boolean isContextMenuOpen() {
        return lineCtxOpen;
    }

    public void closeContextMenu() {
        lineCtxOpen = false;
    }

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

        String cycleLabel;
        if (isForbidden) cycleLabel = "§aType: Forbidden  â†’  Required";
        else if (!isRequired) cycleLabel = "§cType: Optional  â†’  Forbidden";
        else cycleLabel = "§eType: Required  â†’  Optional";

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
                "§bDependency line settingsâ€¦"
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

    private static Boolean cycleArrowOverride(Boolean current) {
        if (current == null) return Boolean.TRUE;
        return current ? Boolean.FALSE : null;
    }

    private static <T extends Enum<T>> T cycleLineOverride(T current, T[] values) {
        if (current == null) return values[0];
        int next = current.ordinal() + 1;
        return next < values.length ? values[next] : null;
    }

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
            
            childNode.removePrerequisite(parentNode);
            parentNode.removeChild(childNode);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Removed: " + parentNode.getTitle().getString() + " â†’ " + childNode.getTitle().getString());
        } else if (idx == 1) {
            
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
            
            childNode.setPrereqLink(lineCtxParentId, !isLink);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept(isLink ? "Unmarked as link" : "Marked as link");
        } else if (idx == 3) {
            
            childNode.setPrereqCosmetic(lineCtxParentId, !isCosmetic);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept(isCosmetic ? "Unmarked as cosmetic-only (now gates unlock)" :
                    "Marked as cosmetic-only (no longer gates unlock)");
        } else if (idx == 4) {
            
            childNode.setHideDepLine(!childNode.isHideDepLine());
            QuestFileSaver.updateHideDepLine(childNode);
            requestRebuild.run();
            feedback.accept(childNode.isHideDepLine() ? "Dep lines hidden: " + childNode.getTitle().getString() :
                    "Dep lines shown: " + childNode.getTitle().getString());
        } else if (idx == 5) {
            
            var next = cycleLineOverride(childNode.getPrereqLineShape(lineCtxParentId),
                    QuestChroniclesSettings.LineStyle.values());
            childNode.setPrereqLineShape(lineCtxParentId, next);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Line shape: " + lineOverrideLabel(next));
        } else if (idx == 6) {
            
            var next = cycleLineOverride(childNode.getPrereqLineVisual(lineCtxParentId),
                    QuestChroniclesSettings.LineVisualStyle.values());
            childNode.setPrereqLineVisual(lineCtxParentId, next);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Line style: " + lineOverrideLabel(next));
        } else if (idx == 7) {
            
            var next = cycleLineOverride(childNode.getPrereqLineSpeed(lineCtxParentId),
                    QuestChroniclesSettings.LineAnimSpeed.values());
            childNode.setPrereqLineSpeed(lineCtxParentId, next);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Dot speed: " + lineOverrideLabel(next));
        } else if (idx == 8) {
            
            Boolean next = cycleArrowOverride(childNode.getPrereqLineArrow(lineCtxParentId));
            childNode.setPrereqLineArrow(lineCtxParentId, next);
            QuestFileSaver.updateNodePrerequisites(childNode);
            requestRebuild.run();
            feedback.accept("Arrow: " + arrowOverrideLabel(next));
        } else if (idx == 9) {
            
            openLineSettings.accept(parentNode);
        }
    }

    private static int boostedDependencyColor(int col) {
        return blendTowardHue(col, 0x33CFFF);
    }

    private static int boostedDependentColor(int col) {
        return blendTowardHue(col, 0xFFAA33);
    }

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
        
        float xa = (float) x1;
        float ya = (float) y1;
        float xb = (float) x2;
        float yb = (float) y2;

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

        int steps = Math.min(Math.max(16, (int) (dist / 4.0f)), 64);

        int alpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        float baseCoreW = switch (vis) {
            case THIN -> 0.4f;
            case BOLD -> 1.2f;
            case THICK -> 1.8f;
            case WIDE -> 2.6f;
            case GLOW -> 0.9f;
            default -> 0.75f; 
        };

        float currentZoom = Math.max(0.05f, zoom);
        float coreHalfW = Math.max(baseCoreW, Math.min(1.4f, 0.4f / currentZoom));
        float outlineHalfW = coreHalfW + Math.min(0.9f, 0.45f / currentZoom);

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

        int outlineAlpha = Math.min(255, Math.max(170, alpha));
        int outlineRgb = QuestChroniclesSettings.get().getTheme() == QuestChroniclesSettings.Theme.LIGHT ? 0x1A1A1A :
                0xEDEDED;
        int outlineColor = (outlineAlpha << 24) | outlineRgb;

        com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource()
                .getBuffer(net.minecraft.client.renderer.RenderType.gui());
        org.joml.Matrix4f mat = g.pose().last().pose();

        float[] pathX = new float[steps + 1];
        float[] pathY = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float mt = 1f - t;
            pathX[i] = mt * mt * mt * xa + 3 * mt * mt * t * cp1x + 3 * mt * t * t * cp2x + t * t * t * xb;
            pathY[i] = mt * mt * mt * ya + 3 * mt * mt * t * cp1y + 3 * mt * t * t * cp2y + t * t * t * yb;
        }

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

        if (showArrow) {

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
        
        float t = ((animMs / 1800f) + lineIdx * 0.37f) % 1f;
        float mt = 1f - t;
        int bx = Math.round(mt * mt * mt * x1 + 3 * mt * mt * t * cp1x + 3 * mt * t * t * cp2x + t * t * t * x2);
        int by = Math.round(mt * mt * mt * y1 + 3 * mt * mt * t * cp1y + 3 * mt * t * t * cp2y + t * t * t * y2);
        
        g.fill(bx - 1, by - 1, bx + 2, by + 2, 0xFFFFEE88);
        g.fill(bx - 2, by - 2, bx + 3, by + 3, 0x44FFCC44);
    }

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

    private void drawArrowSprite(GuiGraphics g, float tipX, float tipY, float dirX, float dirY,
                                 float halfSize, int color) {
        arrowQueue.add(new ArrowInstance(tipX, tipY, dirX, dirY, halfSize, color));
    }

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

    private void flushRibbonQueue(GuiGraphics g) {
        FrameProfiler.setCounter("ribbonQuadsQueued", ribbonQueue.size());
        if (ribbonQueue.isEmpty()) return;
        org.joml.Matrix4f mat = g.pose().last().pose();

        g.flush();

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

    private void flushArrowQueue(GuiGraphics g) {
        FrameProfiler.setCounter("arrowsQueued", arrowQueue.size());
        if (arrowQueue.isEmpty()) return;
        org.joml.Matrix4f mat = g.pose().last().pose();

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

            float fx = a.dirX, fy = a.dirY;
            float rx = -a.dirY, ry = a.dirX;
            float half = a.halfSize;

            float halfLen = half;
            float halfWid = half * (45f / 75f);

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

