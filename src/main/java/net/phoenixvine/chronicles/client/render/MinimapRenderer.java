package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MinimapRenderer {

    private static final int MM_W = 130, MM_H = 78, MM_PAD = 4;

    private int[] renderState = null;

    public int[] bounds(int cr, int height) {
        int mx2 = cr - MM_PAD;
        int my2 = height - MM_PAD;
        return new int[] { mx2 - MM_W, my2 - MM_H, mx2, my2 };
    }

    public void render(GuiGraphics g, Font font, int cl, int cr, int width, int height, int headerH,
                       float zoom, int viewOffX, int viewOffY, Predicate<QuestNode> catMatches,
                       Function<QuestNode, QuestState> stateLookup) {
        int[] b = bounds(cr, height);
        int bx = b[0], by = b[1], bx2 = b[2], by2 = b[3];

        g.fill(bx, by, bx2, by2, 0xE0060609);

        g.fill(bx, by, bx2, by + 1, 0xFF334);
        g.fill(bx, by2 - 1, bx2, by2, 0xFF334);
        g.fill(bx, by, bx + 1, by2, 0xFF334);
        g.fill(bx2 - 1, by, bx2, by2, 0xFF334);

        int innerW = MM_W - 2, innerH = MM_H - 14;
        int innerX = bx + 1, innerY = by + 13;

        g.drawString(font, "§8Map  §7M", bx + 3, by + 3, 0xFF666677, false);
        g.fill(bx, by + 12, bx2, by + 13, 0xFF222233);

        Collection<QuestNode> nodes = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> catMatches.test(n) && !n.isFlagDisabled(null))
                .collect(Collectors.toList());

        if (nodes.isEmpty()) {
            g.drawCenteredString(font, "§8empty", bx + MM_W / 2, by + MM_H / 2 - 4, 0xFF444455);
            return;
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (QuestNode n : nodes) {
            int nx = n.getCustomX(), ny = n.getCustomY();
            int npx = n.getNodePixelSize();
            if (nx < minX) minX = nx;
            if (nx + npx > maxX) maxX = nx + npx;
            if (ny < minY) minY = ny;
            if (ny + npx > maxY) maxY = ny + npx;
        }
        int rangeX = Math.max(1, maxX - minX);
        int rangeY = Math.max(1, maxY - minY);
        float scaleX = (float) innerW / rangeX;
        float scaleY = (float) innerH / rangeY;
        float scale = Math.min(scaleX, scaleY) * 0.9f;

        int offsetX = innerX + (int) ((innerW - rangeX * scale) / 2);
        int offsetY = innerY + (int) ((innerH - rangeY * scale) / 2);

        g.enableScissor(innerX, innerY, innerX + innerW, innerY + innerH);

        for (QuestNode n : nodes) {
            QuestState st = stateLookup.apply(n);
            int col = switch (st) {
                case COMPLETED -> 0xFF00BB66;
                case ACTIVE -> 0xFFBB8800;
                case UNLOCKED -> 0xFF5566CC;
                default -> 0xFF333344;
            };
            int dx = offsetX + (int) ((n.getCustomX() - minX) * scale);
            int dy = offsetY + (int) ((n.getCustomY() - minY) * scale);
            int ds = Math.max(2, (int) (n.getNodePixelSize() * scale));
            g.fill(dx, dy, dx + ds, dy + ds, col);
        }

        int canvasW = cr - cl, canvasH = height - headerH;
        float vpLeft = (-viewOffX) / zoom;
        float vpTop = (-viewOffY) / zoom;
        float vpRight = vpLeft + canvasW / zoom;
        float vpBottom = vpTop + canvasH / zoom;
        int rx1 = offsetX + (int) ((vpLeft - minX) * scale);
        int ry1 = offsetY + (int) ((vpTop - minY) * scale);
        int rx2 = offsetX + (int) ((vpRight - minX) * scale);
        int ry2 = offsetY + (int) ((vpBottom - minY) * scale);
        g.fill(rx1, ry1, rx2, ry1 + 1, 0xFFFFFFAA);
        g.fill(rx1, ry2 - 1, rx2, ry2, 0xFFFFFFAA);
        g.fill(rx1, ry1, rx1 + 1, ry2, 0xFFFFFFAA);
        g.fill(rx2 - 1, ry1, rx2, ry2, 0xFFFFFFAA);

        g.disableScissor();

        renderState = new int[] { offsetX, offsetY, minX, minY, (int) (scale * 1000) };
    }

    public boolean isInMinimap(double x, double y, boolean minimapOpen, int width, int height) {
        if (!minimapOpen) return false;
        int[] b = bounds(width, height);
        return x >= b[0] && x < b[2] && y >= b[1] && y < b[3];
    }

    public int[] panTo(double sx, double sy, int cl, int width, int height, int headerH, float zoom) {
        if (renderState == null) return null;
        int offsetX = renderState[0], offsetY = renderState[1];
        int minX = renderState[2], minY = renderState[3];
        float scale = renderState[4] / 1000f;

        float cx = (float) (sx - offsetX) / scale + minX;
        float cy = (float) (sy - offsetY) / scale + minY;

        int canvasW = width - cl, canvasH = height - headerH;
        int newViewOffX = (int) (canvasW / 2f - cx * zoom);
        int newViewOffY = (int) (canvasH / 2f - cy * zoom);
        return new int[] { newViewOffX, newViewOffY };
    }
}
