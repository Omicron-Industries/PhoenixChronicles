package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

public class DragController {

    private final ScreenContext ctx;
    private final DragControllerState state;
    private final GraphEditorState editorState;

    private int lastMoveOrigX = 0, lastMoveOrigY = 0;
    private long lastMoveTimeMs = 0;

    private Map<ResourceLocation, int[]> dragUndoOrigBulk;
    private QuestNode dragUndoSingleNode;
    private int dragUndoSinglePreX, dragUndoSinglePreY;

    public DragController(ScreenContext ctx, DragControllerState state, GraphEditorState editorState) {
        this.ctx = ctx;
        this.state = state;
        this.editorState = editorState;
    }

    void handleLiveDragging(int mx, int my) {
        if (editorState.draggedNode == null) return;
        updateDraggedNodeScreenPos(mx, my, currentDragSnap());
    }

    public int currentDragSnap() {
        if (state.dragForceSnap()) return state.gridSnap();
        return state.gridSnapEnabled() ? state.gridSnap() : 1;
    }

    int[] computeDraggedNodeSnapLogicalPos(double mx, double my, int snap) {
        int cl = ctx.sidebarW();
        int logX = (int) ((mx - state.dragGrabX() - cl - state.viewOffX()) / ctx.posZoom());
        int logY = (int) ((my - state.dragGrabY() - ChronicleOverviewScreen.HEADER_H - state.viewOffY()) /
                ctx.posZoom());
        logX = Math.round((float) logX / snap) * snap;
        logY = Math.round((float) logY / snap) * snap;
        return new int[] { logX, logY };
    }

    public void updateDraggedNodeScreenPos(double mx, double my, int snap) {
        int cl = ctx.sidebarW();
        int[] logPos = computeDraggedNodeSnapLogicalPos(mx, my, snap);
        int logX = logPos[0], logY = logPos[1];

        int nx = (int) (logX * ctx.posZoom()) + cl + state.viewOffX();
        int ny = (int) (logY * ctx.posZoom()) + ChronicleOverviewScreen.HEADER_H + state.viewOffY();

        ChronicleOverviewScreen.NodeHitbox b = state.nodeButtons().get(editorState.draggedNode.getId());
        if (b != null) {
            b.setX(nx);
            b.setY(ny);
        }

        ctx.nodeScreenPos().put(editorState.draggedNode.getId(), new int[] { nx, ny });
        editorState.draggedNode.setCustomPosition(logX, logY);

        if (editorState.bulkDragOrigPositions != null) {
            int[] grabbedOrig = editorState.bulkDragOrigPositions.get(editorState.draggedNode.getId());
            if (grabbedOrig != null) {
                int deltaX = logX - grabbedOrig[0];
                int deltaY = logY - grabbedOrig[1];
                for (Map.Entry<ResourceLocation, int[]> e : editorState.bulkDragOrigPositions.entrySet()) {
                    if (e.getKey().equals(editorState.draggedNode.getId())) continue;
                    QuestNode other = QuestTreeRegistry.getQuest(e.getKey());
                    if (other == null) continue;
                    other.setCustomPosition(e.getValue()[0] + deltaX, e.getValue()[1] + deltaY);
                    refreshNodeScreenPos(other);
                    state.refreshEdgeEndpointsFor(other);
                }
            }
        }
    }

    public void beginDragUndo(QuestNode capturedNode, int preX, int preY) {
        if (editorState.multiSelection.contains(capturedNode.getId()) && editorState.multiSelection.size() >= 2) {
            Map<ResourceLocation, int[]> orig = new LinkedHashMap<>();
            for (ResourceLocation id : editorState.multiSelection) {
                QuestNode n = QuestTreeRegistry.getQuest(id);
                if (n != null) orig.put(id, new int[] { n.getCustomX(), n.getCustomY() });
            }
            editorState.bulkDragOrigPositions = orig;
            dragUndoOrigBulk = orig;
            dragUndoSingleNode = null;
        } else {
            editorState.bulkDragOrigPositions = null;
            dragUndoOrigBulk = null;
            dragUndoSingleNode = capturedNode;
            dragUndoSinglePreX = preX;
            dragUndoSinglePreY = preY;
        }
    }

    public void finalizeDragRelease() {
        if (editorState.bulkDragOrigPositions != null) {
            int count = editorState.bulkDragOrigPositions.size();
            Map<ResourceLocation, int[]> orig = dragUndoOrigBulk;
            Map<ResourceLocation, int[]> after = new LinkedHashMap<>();
            for (ResourceLocation id : editorState.bulkDragOrigPositions.keySet()) {
                QuestNode n = QuestTreeRegistry.getQuest(id);
                if (n != null) {
                    state.saveNodeToDisk(n);
                    after.put(id, new int[] { n.getCustomX(), n.getCustomY() });
                }
            }
            editorState.bulkDragOrigPositions = null;
            editorState.lastMovedNode = null;
            ctx.setFeedback("Moved %d quests", count);
            if (orig != null && !orig.equals(after)) {
                ctx.undoRedo().push(
                        () -> applyBulkPositions(orig),
                        () -> applyBulkPositions(after));
            }
            dragUndoOrigBulk = null;
        } else if (editorState.draggedNode != null) {
            state.saveNodeToDisk(editorState.draggedNode);
            editorState.lastMovedNode = editorState.draggedNode;
            lastMoveOrigX = state.dragOrigX();
            lastMoveOrigY = state.dragOrigY();
            lastMoveTimeMs = System.currentTimeMillis();

            QuestNode node = dragUndoSingleNode;
            if (node != null) {
                int preX = dragUndoSinglePreX, preY = dragUndoSinglePreY;
                int postX = node.getCustomX(), postY = node.getCustomY();
                if (preX != postX || preY != postY) {
                    ctx.undoRedo().push(
                            () -> {
                                node.setCustomPosition(preX, preY);
                                state.saveNodeToDisk(node);
                                state.rebuild();
                            },
                            () -> {
                                node.setCustomPosition(postX, postY);
                                state.saveNodeToDisk(node);
                                state.rebuild();
                            });
                }
                dragUndoSingleNode = null;
            }
        }
    }

    private void applyBulkPositions(Map<ResourceLocation, int[]> positions) {
        for (Map.Entry<ResourceLocation, int[]> e : positions.entrySet()) {
            QuestNode n = QuestTreeRegistry.getQuest(e.getKey());
            if (n != null) {
                n.setCustomPosition(e.getValue()[0], e.getValue()[1]);
                state.saveNodeToDisk(n);
            }
        }
        state.rebuild();
    }

    void renderDragSnapPosBox(GuiGraphics g, int mx, int my) {
        if (editorState.draggedNode == null) return;
        int[] logPos = computeDraggedNodeSnapLogicalPos(mx, my, currentDragSnap());
        String label = "X: " + logPos[0] + ", Y: " + logPos[1];
        int tw = ctx.font().width(label);
        int bx = mx + 14, by = my + 14;
        g.fill(bx - 3, by - 2, bx + tw + 3, by + 11, 0xCC101010);
        ChroniclesUIKit.drawBorder(g, bx - 3, by - 2, tw + 6, 13, 0x66FFFFFF);
        g.drawString(ctx.font(), "§f" + label, bx, by, ctx.colorText(), false);
    }

    public void refreshNodeScreenPos(QuestNode node) {
        int cl = ctx.sidebarW();
        int sx = (int) (node.getCustomX() * ctx.posZoom()) + state.viewOffX() + cl;
        int sy = (int) (node.getCustomY() * ctx.posZoom()) + state.viewOffY() + ChronicleOverviewScreen.HEADER_H;
        ctx.nodeScreenPos().put(node.getId(), new int[] { sx, sy });
        ChronicleOverviewScreen.NodeHitbox b = state.nodeButtons().get(node.getId());
        if (b != null) {
            int sz = ctx.scaledNodeSize(node);
            b.setX(sx);
            b.setY(sy);
            b.w = sz;
            b.h = sz;
        }
    }

    void renderSnapGridOverlay(GuiGraphics g, int cl, int cr) {
        if (state.gridSnap() <= 1) return;
        int step = Math.round(state.gridSnap() * ctx.posZoom());
        if (step < 6) return;

        int top = ChronicleOverviewScreen.HEADER_H, bottom = ctx.height();
        int color = 0x40FFFFFF;

        int firstX = cl + Math.floorMod(state.viewOffX(), step);
        int firstY = top + Math.floorMod(state.viewOffY(), step);
        for (int x = firstX; x < cr; x += step) {
            for (int y = firstY; y < bottom; y += step) {
                NodeShapeRenderer.queueFillRect(g, x, y, x + 1, y + 1, color);
            }
        }
        NodeShapeRenderer.flushFillQueue(g);
    }

    void renderSnapCursorBox(GuiGraphics g, int mx, int my, int cl, int cr) {
        if (state.gridSnap() <= 1) return;
        if (mx < cl || mx > cr || my < ChronicleOverviewScreen.HEADER_H || my > ctx.height()) return;

        int snap = state.gridSnap();
        int logX = (int) ((mx - cl - state.viewOffX()) / ctx.posZoom());
        int logY = (int) ((my - ChronicleOverviewScreen.HEADER_H - state.viewOffY()) / ctx.posZoom());
        logX = Math.round((float) logX / snap) * snap;
        logY = Math.round((float) logY / snap) * snap;

        int sx = (int) (logX * ctx.posZoom()) + cl + state.viewOffX();
        int sy = (int) (logY * ctx.posZoom()) + ChronicleOverviewScreen.HEADER_H + state.viewOffY();
        int sz = Math.round(snap * ctx.posZoom());
        if (sz < 4) return;

        int color = 0x88FFFFFF;
        g.fill(sx, sy, sx + sz, sy + 1, color);
        g.fill(sx, sy + sz - 1, sx + sz, sy + sz, color);
        g.fill(sx, sy, sx + 1, sy + sz, color);
        g.fill(sx + sz - 1, sy, sx + sz, sy + sz, color);
    }
}
