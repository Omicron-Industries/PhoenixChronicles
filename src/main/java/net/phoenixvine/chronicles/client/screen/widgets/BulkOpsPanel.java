package net.phoenixvine.chronicles.client.screen.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.screen.TextureBrowserScreen;
import net.phoenixvine.chronicles.client.screen.utils.BulkOpsPanelState;
import net.phoenixvine.chronicles.client.screen.utils.GraphEditorState;
import net.phoenixvine.chronicles.client.screen.utils.ScreenContext;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BulkOpsPanel {

    public static final int PANEL_H = 51;
    private static final int ROW_H = 13;
    private static final int PANEL_W = 360;

    private static final String[] SHAPE_IDS = { "SQUARE", "CIRCLE", "DIAMOND", "HEXAGON", "TRIANGLE", "STAR",
            "PENTAGON", "SHIELD", "CROSS", "CUSTOM" };
    private static final String[] SHAPE_GLYPHS = { "■", "●", "◆", "⬡", "▲", "★", "⬠", "❖", "✚", "▩" };
    private static final String[] SIZE_LABELS = { "Tiny", "Small", "Normal", "Large", "Huge" };

    private final ScreenContext ctx;
    private final BulkOpsPanelState state;
    private final GraphEditorState editorState;

    private boolean moveCatOpen = false;

    public BulkOpsPanel(ScreenContext ctx, BulkOpsPanelState state, GraphEditorState editorState) {
        this.ctx = ctx;
        this.state = state;
        this.editorState = editorState;
    }

    public void reset() {
        moveCatOpen = false;
    }

    void render(GuiGraphics g, int mx, int my, int cl) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f);
        g.flush();
        RenderSystem.disableDepthTest();

        int n = editorState.multiSelection.size();
        int bx = cl + 4, by = ChronicleOverviewScreen.HEADER_H + 4;
        int bw = PANEL_W, bh = PANEL_H;
        var font = ctx.font();
        g.fill(bx, by, bx + bw, by + bh, 0xFF131319);
        g.fill(bx, by, bx + bw, by + 1, state.colorBorderLit());
        g.fill(bx, by, bx + 1, by + bh, state.colorBorderLit());
        g.fill(bx + bw - 1, by, bx + bw, by + bh, state.colorBorderLit());
        g.fill(bx, by + bh - 1, bx + bw, by + bh, state.colorBorderLit());
        g.fill(bx, by, bx + 2, by + bh, 0xFF00DDFF);

        g.drawString(font, "§b" + n + " selected", bx + 6, by + 4, 0xFF00DDFF);
        g.drawString(font, "§8Ctrl+click to toggle  ·  Esc to clear", bx + 6, by + 14, ctx.colorTextFaint());

        int slotW = 14, startX = bx + 6, slotY = by + 24;
        for (int i = 0; i < SHAPE_GLYPHS.length; i++) {
            int sx = startX + i * (slotW + 2);
            boolean hov = mx >= sx && mx < sx + slotW && my >= slotY && my < slotY + 12;
            if (hov) g.fill(sx, slotY, sx + slotW, slotY + 12, 0xFF222233);
            g.drawString(font, "§7" + SHAPE_GLYPHS[i], sx + 2, slotY + 2, hov ? 0xFFFFFFFF : 0xFF888899);
        }
        int actX = startX + SHAPE_GLYPHS.length * (slotW + 2) + 8;
        boolean catHov = mx >= actX && mx < actX + 58 && my >= slotY && my < slotY + 12;
        if (catHov || moveCatOpen) g.fill(actX, slotY, actX + 58, slotY + 12, 0xFF222233);
        g.drawString(font, "§7Move cat ▸", actX, slotY + 2,
                (catHov || moveCatOpen) ? 0xFFCCCCFF : ctx.colorTextDim());
        int delX = actX + 62;
        boolean delHov = mx >= delX && mx < delX + 44 && my >= slotY && my < slotY + 12;
        if (delHov) g.fill(delX, slotY, delX + 44, slotY + 12, 0xFF221212);
        g.drawString(font, "§cDel all", delX, slotY + 2, delHov ? 0xFFFF5555 : ChronicleOverviewScreen.C_CTX_DANGER);

        int sizeSlotW = 50, sizeStartX = bx + 6, sizeSlotY = slotY + ROW_H;
        for (int i = 0; i < SIZE_LABELS.length; i++) {
            int sx = sizeStartX + i * (sizeSlotW + 2);
            boolean hov = mx >= sx && mx < sx + sizeSlotW && my >= sizeSlotY && my < sizeSlotY + 12;
            if (hov) g.fill(sx, sizeSlotY, sx + sizeSlotW, sizeSlotY + 12, 0xFF222233);
            g.drawString(font, "§7" + SIZE_LABELS[i], sx + 3, sizeSlotY + 2, hov ? 0xFFFFFFFF : 0xFF888899);
        }

        if (moveCatOpen) {
            List<String> moveCats = ctx.buildChapterList();
            moveCats.remove("ALL");
            int subX = actX, subY = sizeSlotY + ROW_H, subRH = 11, subW = 90;
            g.fill(subX, subY, subX + subW, subY + moveCats.size() * subRH + 4, 0xFF1A1A24);
            g.fill(subX, subY, subX + subW, subY + 1, state.colorBorderLit());
            g.fill(subX, subY, subX + 1, subY + moveCats.size() * subRH + 4, state.colorBorderLit());
            g.fill(subX + subW - 1, subY, subX + subW, subY + moveCats.size() * subRH + 4, state.colorBorderLit());
            for (int ci = 0; ci < moveCats.size(); ci++) {
                int ry = subY + 2 + ci * subRH;
                boolean rHov = mx >= subX + 2 && mx < subX + subW - 2 && my >= ry && my < ry + subRH;
                if (rHov) g.fill(subX + 2, ry, subX + subW - 2, ry + subRH, 0xFF222233);
                g.drawString(font, "§7" + ctx.friendly(moveCats.get(ci)), subX + 4, ry + 2,
                        rHov ? 0xFFCCCCFF : ctx.colorTextDim());
            }
        }

        g.flush();
        g.pose().popPose();
    }

    boolean mouseClicked(double mx, double my, int btn, int cl) {
        if (btn != 0 || !ctx.isDevMode() || editorState.multiSelection.size() < 2) return false;

        int bx = cl + 4, by = ChronicleOverviewScreen.HEADER_H + 4;
        int bh = PANEL_H;
        int slotW = 14, startX = bx + 6, slotY = by + 24;
        int actXForGate = startX + SHAPE_IDS.length * (slotW + 2) + 8;
        int sizeSlotYForGate = slotY + ROW_H;

        boolean inPanel = (int) mx >= bx && (int) mx <= bx + PANEL_W && (int) my >= by && (int) my <= by + bh;
        boolean inDropdown = false;
        if (moveCatOpen) {
            List<String> moveCats = ctx.buildChapterList();
            moveCats.remove("ALL");
            int subY = sizeSlotYForGate + ROW_H, subW = 90, subH = moveCats.size() * 11 + 4;
            inDropdown = (int) mx >= actXForGate && (int) mx <= actXForGate + subW && (int) my >= subY &&
                    (int) my <= subY + subH;
        }
        if (!inPanel && !inDropdown) return false;
        for (int i = 0; i < SHAPE_IDS.length; i++) {
            int sx = startX + i * (slotW + 2);
            if ((int) mx >= sx && (int) mx < sx + slotW && (int) my >= slotY && (int) my < slotY + 12) {
                String newShape = SHAPE_IDS[i];
                if ("CUSTOM".equals(newShape)) {
                    List<ResourceLocation> targets = new ArrayList<>(editorState.multiSelection);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) mc.setScreen(new TextureBrowserScreen(state.thisScreen(), rl -> {
                        Map<QuestNode, String> oldShapes = new LinkedHashMap<>();
                        Map<QuestNode, String> oldTextures = new LinkedHashMap<>();
                        for (ResourceLocation id : targets) {
                            QuestNode n = QuestTreeRegistry.getQuest(id);
                            if (n != null) {
                                oldShapes.put(n, n.getShapeType());
                                oldTextures.put(n, n.getShapeTexture());
                                n.setShapeType("CUSTOM");
                                n.setShapeTexture(rl);
                                state.saveNodeShapeToDisk(n, "CUSTOM");
                                state.saveNodeShapeTextureToDisk(n);
                            }
                        }
                        ctx.setFeedback("Shape → CUSTOM for %d quests", targets.size());
                        state.rebuild();
                        ctx.pushUndo("Undo: " + oldShapes.size() + " quest shapes reverted", () -> {
                            for (Map.Entry<QuestNode, String> e : oldShapes.entrySet()) {
                                QuestNode n = e.getKey();
                                n.setShapeType(e.getValue());
                                n.setShapeTexture(oldTextures.get(n));
                                state.saveNodeShapeToDisk(n, e.getValue());
                                state.saveNodeShapeTextureToDisk(n);
                            }
                            state.rebuild();
                        }, () -> {
                            for (QuestNode n : oldShapes.keySet()) {
                                n.setShapeType("CUSTOM");
                                n.setShapeTexture(rl);
                                state.saveNodeShapeToDisk(n, "CUSTOM");
                                state.saveNodeShapeTextureToDisk(n);
                            }
                            state.rebuild();
                        });
                    }));
                    return true;
                }
                Map<QuestNode, String> oldShapes = new LinkedHashMap<>();
                for (ResourceLocation id : editorState.multiSelection) {
                    QuestNode n = QuestTreeRegistry.getQuest(id);
                    if (n != null) {
                        oldShapes.put(n, n.getShapeType());
                        n.setShapeType(newShape);

                        state.saveNodeShapeToDisk(n, newShape);
                    }
                }
                ctx.setFeedback("Shape → %s for %d quests", newShape, editorState.multiSelection.size());
                state.rebuild();
                ctx.pushUndo("Undo: " + oldShapes.size() + " quest shapes reverted", () -> {
                    for (Map.Entry<QuestNode, String> e : oldShapes.entrySet()) {
                        e.getKey().setShapeType(e.getValue());
                        state.saveNodeShapeToDisk(e.getKey(), e.getValue());
                    }
                    state.rebuild();
                }, () -> {
                    for (QuestNode n : oldShapes.keySet()) {
                        n.setShapeType(newShape);
                        state.saveNodeShapeToDisk(n, newShape);
                    }
                    state.rebuild();
                });
                return true;
            }
        }
        int actX = startX + SHAPE_IDS.length * (slotW + 2) + 8;

        if ((int) mx >= actX && (int) mx < actX + 58 && (int) my >= slotY && (int) my < slotY + 12) {
            moveCatOpen = !moveCatOpen;
            return true;
        }

        int sizeSlotW = 50, sizeStartX = bx + 6, sizeSlotY = slotY + ROW_H;
        QuestNode.NodeSize[] sizeVals = QuestNode.NodeSize.values();
        for (int i = 0; i < SIZE_LABELS.length; i++) {
            int sx = sizeStartX + i * (sizeSlotW + 2);
            if ((int) mx >= sx && (int) mx < sx + sizeSlotW && (int) my >= sizeSlotY && (int) my < sizeSlotY + 12) {
                QuestNode.NodeSize newSize = sizeVals[i];
                Map<QuestNode, QuestNode.NodeSize> oldSizes = new LinkedHashMap<>();
                for (ResourceLocation id : editorState.multiSelection) {
                    QuestNode n = QuestTreeRegistry.getQuest(id);
                    if (n != null) {
                        oldSizes.put(n, n.getNodeSize());
                        n.setNodeSize(newSize);
                        QuestFileSaver.saveOneQuestToDisk(n);
                    }
                }
                ctx.setFeedback("Size → %s for %d quests", SIZE_LABELS[i], editorState.multiSelection.size());
                state.rebuild();
                ctx.pushUndo("Undo: " + oldSizes.size() + " quest sizes reverted", () -> {
                    for (Map.Entry<QuestNode, QuestNode.NodeSize> e : oldSizes.entrySet()) {
                        e.getKey().setNodeSize(e.getValue());
                        QuestFileSaver.saveOneQuestToDisk(e.getKey());
                    }
                    state.rebuild();
                }, () -> {
                    for (QuestNode n : oldSizes.keySet()) {
                        n.setNodeSize(newSize);
                        QuestFileSaver.saveOneQuestToDisk(n);
                    }
                    state.rebuild();
                });
                return true;
            }
        }

        if (moveCatOpen) {
            List<String> moveCats = ctx.buildChapterList();
            moveCats.remove("ALL");
            int subX = actX, subY = sizeSlotY + ROW_H, subRH = 11;
            for (int ci = 0; ci < moveCats.size(); ci++) {
                int ry = subY + 2 + ci * subRH;
                if ((int) mx >= subX + 2 && (int) mx < subX + 90 - 2 && (int) my >= ry && (int) my < ry + subRH) {
                    String newCat = moveCats.get(ci);
                    Map<QuestNode, String> oldChapters = new LinkedHashMap<>();
                    for (ResourceLocation sid : new ArrayList<>(editorState.multiSelection)) {
                        QuestNode sn = QuestTreeRegistry.getQuest(sid);
                        if (sn != null) {
                            oldChapters.put(sn, sn.getChapter());
                            sn.setChapter(newCat);

                            state.saveNodeChapterToDisk(sn, newCat);
                        }
                    }
                    moveCatOpen = false;
                    ctx.setFeedback("Moved %d quests to %s", editorState.multiSelection.size(), ctx.friendly(newCat));
                    ctx.pushUndo("Undo: " + oldChapters.size() + " quests moved back", () -> {
                        for (Map.Entry<QuestNode, String> e : oldChapters.entrySet()) {
                            e.getKey().setChapter(e.getValue());
                            state.saveNodeChapterToDisk(e.getKey(), e.getValue());
                        }
                        state.rebuild();
                    }, () -> {
                        for (QuestNode n : oldChapters.keySet()) {
                            n.setChapter(newCat);
                            state.saveNodeChapterToDisk(n, newCat);
                        }
                        state.rebuild();
                    });
                    state.rebuild();
                    return true;
                }
            }
        }

        int delX = actX + 62;
        if ((int) mx >= delX && (int) mx < delX + 44 && (int) my >= slotY && (int) my < slotY + 12) {
            int count = editorState.multiSelection.size();
            Map<QuestNode, String> savedContent = new LinkedHashMap<>();
            Map<QuestNode, Path> savedPaths = new LinkedHashMap<>();
            for (ResourceLocation id : new ArrayList<>(editorState.multiSelection)) {
                QuestNode n = QuestTreeRegistry.getQuest(id);
                if (n != null) {
                    savedContent.put(n, QuestFileSaver.readRawSnbt(n));
                    savedPaths.put(n, QuestFileSaver.getQuestSnbtPath(n));
                    QuestTreeRegistry.removeQuest(id);

                    state.deleteQuestFiles(n);
                }
            }
            editorState.multiSelection.clear();
            state.rebuild();
            ctx.setFeedback("Deleted %d quests", count);
            ctx.pushUndo("Undo: " + savedContent.size() + " quests restored", () -> {
                for (Map.Entry<QuestNode, String> e : savedContent.entrySet()) {
                    QuestFileSaver.restoreRawSnbt(e.getKey(), e.getValue());
                    QuestFileLoader.loadOneFromDisk(savedPaths.get(e.getKey()));
                }
                state.rebuild();
            }, () -> {
                for (QuestNode n : savedContent.keySet()) {
                    QuestTreeRegistry.removeQuest(n.getId());
                    state.deleteQuestFiles(n);
                }
                state.rebuild();
            });
            return true;
        }
        return true;
    }
}
