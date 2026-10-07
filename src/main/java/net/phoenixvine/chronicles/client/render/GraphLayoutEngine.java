package net.phoenixvine.chronicles.client.render;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.screen.utils.DragControllerState;
import net.phoenixvine.chronicles.client.screen.utils.GraphLayoutState;
import net.phoenixvine.chronicles.client.screen.utils.ScreenContext;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import java.util.*;

public class GraphLayoutEngine {

    private final ScreenContext ctx;
    private final DragControllerState dragState;
    private final GraphLayoutState layoutState;

    public GraphLayoutEngine(ScreenContext ctx, DragControllerState dragState, GraphLayoutState layoutState) {
        this.ctx = ctx;
        this.dragState = dragState;
        this.layoutState = layoutState;
    }

    public void placeNodeRecursive(QuestNode node, int cl, int cr) {
        if (dragState.nodeButtons().containsKey(node.getId())) return;
        if (layoutState.hiddenByCollapse().contains(node.getId())) return;

        if (layoutState.hideCompleted() && layoutState.playerData() != null &&
                layoutState.playerData().getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED) {
            for (QuestNode child : node.getChildren()) placeNodeRecursive(child, cl, cr);
            return;
        }

        int cx = node.getCustomX();
        int cy = node.getCustomY();

        int sz = scaledNodeSize(node);
        int sx = (int) (cx * ctx.posZoom()) + dragState.viewOffX() + cl - sz / 2;
        int sy = (int) (cy * ctx.posZoom()) + dragState.viewOffY() + ChronicleOverviewScreen.HEADER_H - sz / 2;

        boolean offCanvas = sx < cl - sz - 2 || sx > cr + 2 || sy < ChronicleOverviewScreen.HEADER_H - sz - 2 ||
                sy > ctx.height() + 2;

        ChronicleOverviewScreen.NodeHitbox hb = new ChronicleOverviewScreen.NodeHitbox();
        hb.x = sx;
        hb.y = sy;
        hb.w = sz;
        hb.h = sz;
        hb.visible = !offCanvas;
        if (!ctx.isDevMode() && layoutState.isGatedHidden(node)) hb.active = false;
        dragState.nodeButtons().put(node.getId(), hb);
        ctx.nodeScreenPos().put(node.getId(), new int[] { sx, sy });

        for (QuestNode child : node.getChildren()) {
            if (layoutState.catMatches(child)) placeNodeRecursive(child, cl, cr);
        }
    }

    private static final int MIN_NODE_FLOOR_PX = 12;

    public int scaledNodeSize(QuestNode node) {
        float pixelSize = node.getNodePixelSize();
        if (node.getSizeOverridePx() <= 0) pixelSize *= NodeShapeRenderer.visualSizeScale(node.getShapeType());
        float zoom = ctx.rawZoom();
        return Math.max(MIN_NODE_FLOOR_PX, Math.round(pixelSize * zoom));
    }

    public int scaledNodeSize() {
        return Math.max(ChronicleOverviewScreen.MIN_NODE_PX,
                Math.round(ChronicleOverviewScreen.NODE_SIZE * ctx.rawZoom()));
    }

    public void autoArrangeChapter() {
        final int X_STRIDE = 80;
        final int Y_STRIDE = 56;
        final int ORIGIN_X = 30;
        final int ORIGIN_Y = 30;

        String selectedChapter = ctx.selectedChapter();
        List<QuestNode> nodes = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> selectedChapter.equalsIgnoreCase(n.getChapter()))
                .toList();
        if (nodes.isEmpty()) return;

        Map<ResourceLocation, int[]> oldPositions = new HashMap<>();
        for (QuestNode n : nodes) oldPositions.put(n.getId(), new int[] { n.getCustomX(), n.getCustomY() });

        Map<ResourceLocation, Integer> layer = new HashMap<>();

        Queue<QuestNode> queue = new ArrayDeque<>();
        for (QuestNode n : nodes) {
            boolean isRoot = n.getPrerequisites().stream()
                    .noneMatch(p -> selectedChapter.equalsIgnoreCase(p.getChapter()));
            if (isRoot) {
                layer.put(n.getId(), 0);
                queue.add(n);
            }
        }

        if (queue.isEmpty()) {
            nodes.forEach(n -> layer.put(n.getId(), 0));
        }
        int safety = nodes.size() * nodes.size();
        while (!queue.isEmpty() && safety-- > 0) {
            QuestNode n = queue.poll();
            int myLayer = layer.getOrDefault(n.getId(), 0);
            for (QuestNode child : n.getChildren()) {
                if (!selectedChapter.equalsIgnoreCase(child.getChapter())) continue;
                int childLayer = layer.getOrDefault(child.getId(), -1);
                if (childLayer < myLayer + 1) {
                    layer.put(child.getId(), myLayer + 1);
                    queue.add(child);
                }
            }
        }

        nodes.forEach(n -> layer.putIfAbsent(n.getId(), 0));

        Map<Integer, List<QuestNode>> byLayer = new TreeMap<>();
        for (QuestNode n : nodes) byLayer.computeIfAbsent(layer.get(n.getId()), k -> new ArrayList<>()).add(n);

        for (Map.Entry<Integer, List<QuestNode>> e : byLayer.entrySet()) {
            if (e.getKey() == 0) continue;
            e.getValue().sort(Comparator.comparingDouble(n -> {
                List<QuestNode> prereqs = n.getPrerequisites().stream()
                        .filter(p -> selectedChapter.equalsIgnoreCase(p.getChapter())).toList();
                if (prereqs.isEmpty()) return 0.0;
                return prereqs.stream()
                        .mapToInt(p -> byLayer.getOrDefault(layer.getOrDefault(p.getId(), 0), List.of()).indexOf(p))
                        .average().orElse(0.0);
            }));
        }

        for (Map.Entry<Integer, List<QuestNode>> e : byLayer.entrySet()) {
            int lyr = e.getKey();
            List<QuestNode> layerNodes = e.getValue();
            for (int slot = 0; slot < layerNodes.size(); slot++) {
                int x = ORIGIN_X + lyr * X_STRIDE;
                int y = ORIGIN_Y + slot * Y_STRIDE;
                layerNodes.get(slot).setCustomPosition(x, y);
            }
        }

        for (QuestNode n : nodes) QuestFileSaver.saveOneQuestToDisk(n, false);
        layoutState.resetViewOffset();
        dragState.rebuild();
        ctx.setFeedback("Auto-arranged %d quest(s)", nodes.size());

        Map<ResourceLocation, int[]> newPositions = new HashMap<>();
        for (QuestNode n : nodes) newPositions.put(n.getId(), new int[] { n.getCustomX(), n.getCustomY() });
        ctx.undoRedo().push(
                () -> applyPositions(nodes, oldPositions),
                () -> applyPositions(nodes, newPositions));
    }

    public void rotateChapter90() {
        String selectedChapter = ctx.selectedChapter();
        List<QuestNode> nodes = QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> selectedChapter.equalsIgnoreCase(n.getChapter()))
                .toList();
        if (nodes.isEmpty()) return;

        Map<ResourceLocation, int[]> oldPositions = new HashMap<>();
        for (QuestNode n : nodes) oldPositions.put(n.getId(), new int[] { n.getCustomX(), n.getCustomY() });

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (QuestNode n : nodes) {
            minX = Math.min(minX, n.getCustomX());
            maxX = Math.max(maxX, n.getCustomX());
            minY = Math.min(minY, n.getCustomY());
            maxY = Math.max(maxY, n.getCustomY());
        }
        int cx = (minX + maxX) / 2;
        int cy = (minY + maxY) / 2;

        Map<ResourceLocation, int[]> newPositions = new HashMap<>();
        for (QuestNode n : nodes) {
            int x = n.getCustomX(), y = n.getCustomY();
            int newX = cx - (y - cy);
            int newY = cy + (x - cx);
            newPositions.put(n.getId(), new int[] { newX, newY });
        }

        applyPositions(nodes, newPositions);
        ctx.setFeedback("Rotated %d quest(s) in chapter", nodes.size());

        ctx.undoRedo().push(
                () -> applyPositions(nodes, oldPositions),
                () -> applyPositions(nodes, newPositions));
    }

    private void applyPositions(List<QuestNode> nodes, Map<ResourceLocation, int[]> positions) {
        for (QuestNode n : nodes) {
            int[] pos = positions.get(n.getId());
            if (pos != null) {
                n.setCustomPosition(pos[0], pos[1]);
                QuestFileSaver.saveOneQuestToDisk(n, false);
            }
        }
        dragState.rebuild();
    }
}
