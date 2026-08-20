package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.phoenixvine.chronicles.client.*;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.model.*;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

class NodeRenderer {

    private final ScreenContext ctx;
    private final NodeRendererState state;
    private final GraphEditorState editorState;

    NodeRenderer(ScreenContext ctx, NodeRendererState state, GraphEditorState editorState) {
        this.ctx = ctx;
        this.state = state;
        this.editorState = editorState;
    }

    void renderNodesAndDetails(GuiGraphics g, int mx, int my, int cl, int cr, int sz) {
        int height = ctx.height();
        g.enableScissor(cl, ChronicleOverviewScreen.HEADER_H, cr, height);

        FrameProfiler.begin("node visuals");
        state.setDbgFull3DIconCount(0);
        state.setDbgCustomIconCount(0);
        state.setDbgPickedTextureIconCount(0);
        state.setDbgFluidIconCount(0);
        state.setDbgGlyphIconCount(0);
        state.dbgShapeCounts().clear();
        int visibleNodeCount = 0;

        for (Map.Entry<ResourceLocation, int[]> entry : ctx.nodeScreenPos().entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            ChronicleOverviewScreen.NodeHitbox btn = state.nodeButtons().get(node.getId());
            if (btn == null || !btn.visible) continue;
            visibleNodeCount++;
            int[] pos = entry.getValue();
            renderNodeShape(g, node, pos[0], pos[1], ctx.scaledNodeSize(node), btn.isMouseOver(mx, my),
                    node == editorState.selectedNode);
        }
        int shapeQuadCount = NodeShapeRenderer.flushFillQueue(g);

        boolean blockPanelOpen = (state.validationPanelOpen() || state.statsPanelOpen()) && ctx.isDevMode();
        int bpW = Math.min(480, cr - cl - 20);
        int bpX = cl + (cr - cl - bpW) / 2;
        int bpY = ChronicleOverviewScreen.HEADER_H + 10;
        int bpH = height - bpY - 10;
        for (Map.Entry<ResourceLocation, int[]> entry : ctx.nodeScreenPos().entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            ChronicleOverviewScreen.NodeHitbox btn = state.nodeButtons().get(node.getId());
            if (btn == null || !btn.visible) continue;
            int[] pos = entry.getValue();
            int nsz = ctx.scaledNodeSize(node);
            if (blockPanelOpen && pos[0] + nsz > bpX && pos[0] < bpX + bpW && pos[1] + nsz > bpY &&
                    pos[1] < bpY + bpH)
                continue;
            renderNodeDetails(g, node, pos[0], pos[1], nsz, btn.isMouseOver(mx, my), node == editorState.selectedNode);
        }

        int badgeQuadCount = NodeShapeRenderer.flushFillQueue(g);
        FrameProfiler.setCounter("shapeFillQuadsQueued", shapeQuadCount + badgeQuadCount);
        FrameProfiler.setCounter("visibleNodes", visibleNodeCount);
        FrameProfiler.setCounter("full3DIcons", state.dbgFull3DIconCount());
        FrameProfiler.setCounter("customIcons", state.dbgCustomIconCount());
        FrameProfiler.setCounter("pickedTexIcons", state.dbgPickedTextureIconCount());
        FrameProfiler.setCounter("fluidIcons", state.dbgFluidIconCount());
        FrameProfiler.setCounter("glyphIcons", state.dbgGlyphIconCount());
        for (Map.Entry<String, Integer> e : state.dbgShapeCounts().entrySet()) {
            FrameProfiler.setCounter("shape:" + e.getKey(), e.getValue());
        }
        FrameProfiler.end("node visuals");

        g.flush();

        FrameProfiler.begin("dev overlays");

        if (ctx.isDevMode() && !editorState.multiSelection.isEmpty()) {
            long dashPhase = (System.currentTimeMillis() / 80) % 6;
            for (ResourceLocation id : editorState.multiSelection) {
                int[] pos = ctx.nodeScreenPos().get(id);
                if (pos == null) continue;
                QuestNode selNode = QuestTreeRegistry.getQuest(id);
                int selSz = selNode != null ? ctx.scaledNodeSize(selNode) : sz;
                int x1 = pos[0] - 2, y1 = pos[1] - 2, x2 = pos[0] + selSz + 2, y2 = pos[1] + selSz + 2;
                int selCol = 0xFF00DDFF;
                for (int px = x1; px < x2; px++) {
                    if ((px + dashPhase) % 6 < 3) {
                        g.fill(px, y1, px + 1, y1 + 1, selCol);
                        g.fill(px, y2, px + 1, y2 + 1, selCol);
                    }
                }
                for (int py = y1; py < y2; py++) {
                    if ((py + dashPhase) % 6 < 3) {
                        g.fill(x1, py, x1 + 1, py + 1, selCol);
                        g.fill(x2, py, x2 + 1, py + 1, selCol);
                    }
                }
            }
        }

        if (ctx.isDevMode()) {
            float pulse = ChronicleOverviewScreen.animPulse(0.65f, 0.35f, 400.0);
            int alpha = (int) (pulse * 255) << 24;
            int errCol = alpha | 0x00FF2222;
            for (Map.Entry<ResourceLocation, int[]> entry : ctx.nodeScreenPos().entrySet()) {
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                if (node == null) continue;
                if (ctx.validationIssues(node).isEmpty()) continue;
                int[] pos = entry.getValue();
                int nsz = ctx.scaledNodeSize(node);
                int x1 = pos[0] - 3, y1 = pos[1] - 3, x2 = pos[0] + nsz + 3, y2 = pos[1] + nsz + 3;
                g.fill(x1, y1, x2, y1 + 2, errCol);
                g.fill(x1, y2 - 2, x2, y2, errCol);
                g.fill(x1, y1, x1 + 2, y2, errCol);
                g.fill(x2 - 2, y1, x2, y2, errCol);
            }
        }

        if (editorState.subgraphMode && editorState.selectedNode != null && !state.subgraphNodes().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 150f);
            g.flush();
            for (Map.Entry<ResourceLocation, int[]> entry : ctx.nodeScreenPos().entrySet()) {
                int[] pos = entry.getValue();
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                int nsz = node != null ? ctx.scaledNodeSize(node) : sz;
                if (state.subgraphNodes().contains(entry.getKey())) {

                    int x1 = pos[0] - 2, y1 = pos[1] - 2, x2 = pos[0] + nsz + 2, y2 = pos[1] + nsz + 2;
                    g.fill(x1, y1, x2, y1 + 1, 0xFF44CCFF);
                    g.fill(x1, y2 - 1, x2, y2, 0xFF44CCFF);
                    g.fill(x1, y1, x1 + 1, y2, 0xFF44CCFF);
                    g.fill(x2 - 1, y1, x2, y2, 0xFF44CCFF);

                    g.fill(pos[0], pos[1], pos[0] + nsz, pos[1] + nsz, 0x3344CCFF);
                } else {
                    g.fill(pos[0] - 1, pos[1] - 1, pos[0] + nsz + 1, pos[1] + nsz + 1, 0xCC000000);
                }
            }
            g.flush();
            g.pose().popPose();

            String badge = "§b⊛ Subgraph  §7" + state.subgraphNodes().size() + " node" +
                    (state.subgraphNodes().size() == 1 ? "" : "s") + " isolated  §8(G to exit)";
            int bw = ctx.font().width(net.minecraft.util.StringUtil.stripColor(badge)) + 10;
            int bx = cl + 6, by = ChronicleOverviewScreen.HEADER_H + 4;
            g.fill(bx, by, bx + bw, by + 12, 0xCC101018);
            ChroniclesUIKit.drawBorder(g, bx, by, bw, 12, 0xFF44CCFF);
            g.drawString(ctx.font(), badge, bx + 5, by + 2, ctx.colorText(), false);
        }

        FrameProfiler.end("dev overlays");

        FrameProfiler.begin("badges/labels");

        boolean bulkPanelOpen = ctx.isDevMode() && editorState.multiSelection.size() >= 2;
        int bulkX = cl + 4, bulkY = ChronicleOverviewScreen.HEADER_H + 4, bulkW = 360,
                bulkBottom = ChronicleOverviewScreen.HEADER_H + 4 + BulkOpsPanel.PANEL_H;
        for (Map.Entry<ResourceLocation, int[]> entry : ctx.nodeScreenPos().entrySet()) {
            QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
            if (node == null) continue;
            ChronicleOverviewScreen.NodeHitbox btn = state.nodeButtons().get(node.getId());
            if (btn == null || !btn.visible) continue;
            int[] pos = entry.getValue();
            QuestState st = ctx.getState(node);
            int nodeSz = ctx.scaledNodeSize(node);

            if (st == QuestState.UNLOCKED && nodeSz >= 20 &&
                    !net.phoenixvine.chronicles.client.SeenQuestTracker.isSeen(node.getId())) {
                int badgeX = pos[0] + nodeSz - 2;
                int badgeY = pos[1] - 1;
                g.fill(badgeX, badgeY, badgeX + ctx.font().width("NEW") + 4, badgeY + 8, 0xFF1144BB);
                g.drawString(ctx.font(), "NEW", badgeX + 2, badgeY + 1, 0xFFAADDFF, false);
            }
            if (st == QuestState.COMPLETED && nodeSz >= 12 &&
                    !node.getEffectiveRewards(Minecraft.getInstance().getSingleplayerServer()).isEmpty()) {
                net.phoenixvine.chronicles.capability.PlayerQuestData pd = state.testMode() ? state.testModeData() :
                        state.playerData();
                if (pd != null) {
                    int badgeX = pos[0] - 4;
                    int badgeY = pos[1] - 4;
                    if (!pd.hasClaimedRewards(node.getId())) {
                        float pulse = ChronicleOverviewScreen.animPulse(0.7f, 0.3f, 600.0);
                        int bAlpha = (int) (pulse * 255) << 24;
                        g.fill(badgeX, badgeY, badgeX + 9, badgeY + 9, bAlpha | 0x00BB6600);
                        g.drawString(ctx.font(), "!", badgeX + 2, badgeY + 1, bAlpha | 0x00FFD700, false);
                    } else {

                        g.fill(badgeX, badgeY, badgeX + 9, badgeY + 9, 0xFF0A2210);
                        g.drawString(ctx.font(), "✔", badgeX + 2, badgeY + 1, 0xFF55CC77, false);
                    }
                }
            }

            int labelY = pos[1] + nodeSz + 4;
            boolean labelUnderBulkPanel = bulkPanelOpen && labelY < bulkBottom + ctx.font().lineHeight &&
                    pos[0] + nodeSz > bulkX && pos[0] < bulkX + bulkW;
            if (ctx.posZoom() >= 0.55f && !labelUnderBulkPanel) {
                int baseAlpha = ctx.posZoom() >= 0.75f ? 0xFF : (int) ((ctx.posZoom() - 0.55f) / 0.20f * 0xFF);
                int lc = st == QuestState.COMPLETED ? state.colorTextDone() :
                        st == QuestState.ACTIVE ? state.colorTextActive() :
                                st == QuestState.LOCKED ? ctx.colorTextFaint() : ctx.colorTextDim();
                if (baseAlpha < 0xFF) lc = (lc & 0x00FFFFFF) | (baseAlpha << 24);
                ChroniclesUIKit.drawScaledCenteredString(g, ctx.font(), state.shortLabel(node), pos[0] + nodeSz / 2f,
                        labelY, lc, QuestChroniclesSettings.get().getTextScaleMultiplier());
            }
        }
        FrameProfiler.end("badges/labels");

        g.disableScissor();
    }

    @Nullable
    ResourceLocation resolveShapeTexture(QuestNode node) {
        String tex = node.getShapeTexture();
        if (tex == null || tex.isEmpty()) return null;
        try {
            return CustomTextureCache.resolve(ResourceLocation.parse(tex));
        } catch (Exception ignored) {
            return null;
        }
    }

    void renderNodeShape(GuiGraphics g, QuestNode node, int x, int y, int sz,
                         boolean hovered, boolean selected) {
        QuestNode linkTargetNode = state.resolveLinkTarget(node);
        QuestNode displaySource = linkTargetNode != null ? linkTargetNode : node;
        QuestState st = ctx.getState(displaySource);

        boolean showActiveAccent = st == QuestState.ACTIVE && hovered;
        int fill = switch (st) {
            case COMPLETED -> state.colorNodeDone();
            case ACTIVE -> showActiveAccent ? state.colorNodeActive() : state.colorNodeUnlocked();
            case LOCKED -> state.colorNodeLocked();
            default -> state.colorNodeUnlocked();
        };
        int border = switch (st) {
            case COMPLETED -> ctx.colorNodeBorderDone();
            case ACTIVE -> showActiveAccent ? state.colorNodeBorderActive() : state.colorNodeBorderUnlocked();
            case LOCKED -> ctx.isDevMode() ? state.colorNodeBorderDev() : state.colorNodeBorderLocked();
            default -> state.colorNodeBorderUnlocked();
        };
        if (selected) border = ChronicleOverviewScreen.C_NBORD_SEL;
        if (hovered) fill = ChronicleOverviewScreen.blendColor(fill, 0xFFFFFFFF, 0.08f);

        boolean roomForEffects = sz >= 14;

        String shape = node.getShapeType() != null ? node.getShapeType().toUpperCase() : "SQUARE";
        ResourceLocation shapeTex = "CUSTOM".equals(shape) ? resolveShapeTexture(node) : null;

        FrameProfiler.begin("node:effects");

        if (selected) {
            int hlColor = (border & 0x00FFFFFF) | 0x44000000;
            int hx = x - 2, hy = y - 2, hsz = sz + 4;
            switch (shape) {
                case "CIRCLE" -> NodeShapeRenderer.fillCircle(g, hx, hy, hsz, hlColor);
                case "DIAMOND" -> NodeShapeRenderer.fillDiamond(g, hx, hy, hsz, hlColor);
                case "HEXAGON" -> NodeShapeRenderer.fillHexagon(g, hx, hy, hsz, hlColor);
                case "TRIANGLE" -> NodeShapeRenderer.fillTriangle(g, hx, hy, hsz, hlColor);
                case "STAR" -> NodeShapeRenderer.fillStar(g, hx, hy, hsz, hlColor);
                case "PENTAGON" -> NodeShapeRenderer.fillPentagon(g, hx, hy, hsz, hlColor);
                case "SHIELD" -> NodeShapeRenderer.fillShield(g, hx, hy, hsz, hlColor);
                case "CROSS" -> NodeShapeRenderer.fillCross(g, hx, hy, hsz, hlColor);
                default -> g.fill(hx, hy, hx + hsz, hy + hsz, hlColor);
            }
        }

        FrameProfiler.end("node:effects");

        FrameProfiler.begin("node:shape");
        state.dbgShapeCounts().merge(shape, 1, Integer::sum);

        if (roomForEffects) {
            switch (shape) {
                case "CIRCLE" -> NodeShapeRenderer.fillCircle(g, x + 2, y + 2, sz, 0x44000000);
                case "DIAMOND" -> NodeShapeRenderer.fillDiamond(g, x + 2, y + 2, sz, 0x44000000);
                case "HEXAGON" -> NodeShapeRenderer.fillHexagon(g, x + 2, y + 2, sz, 0x44000000);
                case "TRIANGLE" -> NodeShapeRenderer.fillTriangle(g, x + 2, y + 2, sz, 0x44000000);
                case "STAR" -> NodeShapeRenderer.fillStar(g, x + 2, y + 2, sz, 0x44000000);
                case "PENTAGON" -> NodeShapeRenderer.fillPentagon(g, x + 2, y + 2, sz, 0x44000000);
                case "SHIELD" -> NodeShapeRenderer.fillShield(g, x + 2, y + 2, sz, 0x44000000);
                case "CROSS" -> NodeShapeRenderer.fillCross(g, x + 2, y + 2, sz, 0x44000000);
                case "CUSTOM" -> {
                    if (shapeTex != null)
                        NodeShapeRenderer.blitCustomShape(g, shapeTex, x + 2, y + 2, sz, sz, 0x44000000);
                    else NodeShapeRenderer.queueFillRect(g, x + 2, y + 2, x + sz + 2, y + sz + 2, 0x44000000);
                }
                default -> NodeShapeRenderer.queueFillRect(g, x + 2, y + 2, x + sz + 2, y + sz + 2, 0x44000000);
            }
        }

        int thickness = ChronicleOverviewScreen.nodeBorderThickness(sz);

        int fx = x + thickness, fy = y + thickness;
        int fsz = Math.max(1, sz - 2 * thickness);

        boolean hasBackground = false;
        String backgroundType = node.getBackgroundType();
        if (backgroundType != null && !backgroundType.isEmpty()) {
            net.phoenixvine.chronicles.client.render.IQuestBackground background = net.phoenixvine.chronicles.registry.QuestBackgroundRegistry
                    .get(backgroundType);
            if (background != null) {
                background.render(g, node, fx, fy, fsz, System.currentTimeMillis());
                hasBackground = true;
            }
        }

        switch (shape) {
            case "CIRCLE" -> {
                if (!hasBackground) NodeShapeRenderer.fillCircle(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineCircle(g, x, y, sz, border, thickness);
            }
            case "DIAMOND" -> {
                if (!hasBackground) NodeShapeRenderer.fillDiamond(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineDiamond(g, x, y, sz, border, thickness);
            }
            case "HEXAGON" -> {
                if (!hasBackground) NodeShapeRenderer.fillHexagon(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineHexagon(g, x, y, sz, border, thickness);
            }
            case "TRIANGLE" -> {
                if (!hasBackground) NodeShapeRenderer.fillTriangle(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineTriangle(g, x, y, sz, border, thickness);
            }
            case "STAR" -> {
                if (!hasBackground) NodeShapeRenderer.fillStar(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineStar(g, x, y, sz, border, thickness);
            }
            case "PENTAGON" -> {
                if (!hasBackground) NodeShapeRenderer.fillPentagon(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlinePentagon(g, x, y, sz, border, thickness);
            }
            case "SHIELD" -> {
                if (!hasBackground) NodeShapeRenderer.fillShield(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineShield(g, x, y, sz, border, thickness);
            }
            case "CROSS" -> {
                if (!hasBackground) NodeShapeRenderer.fillCross(g, fx, fy, fsz, fill);
                NodeShapeRenderer.outlineCross(g, x, y, sz, border, thickness);
            }
            case "CUSTOM" -> {
                if (shapeTex != null) {

                    int pad = Math.max(1, thickness);
                    NodeShapeRenderer.blitCustomShape(g, shapeTex, x - pad, y - pad, sz + pad * 2, sz + pad * 2,
                            border);
                    if (!hasBackground) NodeShapeRenderer.blitCustomShape(g, shapeTex, x, y, sz, sz, fill);
                } else {

                    if (!hasBackground) NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + sz, fill);
                    NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + thickness, border);
                    NodeShapeRenderer.queueFillRect(g, x, y + sz - thickness, x + sz, y + sz, border);
                    NodeShapeRenderer.queueFillRect(g, x, y, x + thickness, y + sz, border);
                    NodeShapeRenderer.queueFillRect(g, x + sz - thickness, y, x + sz, y + sz, border);
                }
            }
            default -> {
                if (!hasBackground) NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + sz, fill);
                NodeShapeRenderer.queueFillRect(g, x, y, x + sz, y + thickness, border);
                NodeShapeRenderer.queueFillRect(g, x, y + sz - thickness, x + sz, y + sz, border);
                NodeShapeRenderer.queueFillRect(g, x, y, x + thickness, y + sz, border);
                NodeShapeRenderer.queueFillRect(g, x + sz - thickness, y, x + sz, y + sz, border);
            }
        }
        FrameProfiler.end("node:shape");
    }

    static void withNoIconMipBleed(GuiGraphics g, Runnable render) {
        g.flush();
        int texId = net.minecraft.client.Minecraft.getInstance().getTextureManager()
                .getTexture(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).getId();
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(texId);
        com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
        try {
            render.run();
        } finally {
            g.flush();
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(texId);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
                    org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR);
        }
    }

    void renderNodeDetails(GuiGraphics g, QuestNode node, int x, int y, int sz,
                           boolean hovered, boolean selected) {
        QuestNode linkTargetNode = state.resolveLinkTarget(node);
        QuestNode displaySource = linkTargetNode != null ? linkTargetNode : node;
        QuestState st = ctx.getState(displaySource);

        FrameProfiler.begin("node:overlays");

        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();

        if (node.getEffectiveVisibility(server) == QuestNode.Visibility.DISABLED) {
            g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0xBB0B0B0F);
            g.drawCenteredString(ctx.font(), "§8✕", x + sz / 2, y + sz / 2 - 4, 0xFF444444);
        }

        if (ctx.isDevMode() && node.isFlagDisabled(null)) {
            g.fill(x - 2, y - 2, x + sz + 2, y - 1, 0xBB7722BB);
            g.fill(x - 2, y + sz + 1, x + sz + 2, y + sz + 2, 0xBB7722BB);
            g.fill(x - 2, y - 1, x - 1, y + sz + 1, 0xBB7722BB);
            g.fill(x + sz + 1, y - 1, x + sz + 2, y + sz + 1, 0xBB7722BB);
            g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0xCC0B0B0F);
            g.drawCenteredString(ctx.font(), "§5⚑", x + sz / 2, y + sz / 2 - 4, 0xFFAA44CC);
        }

        if (st == QuestState.LOCKED && !ctx.isDevMode()) {
            NodeShapeRenderer.queueFillRect(g, x + 1, y + 1, x + sz - 1, y + sz - 1, 0x440B0B0F);

            int w = sz - 2;
            for (int d = -sz; d < sz; d += 6) {
                int lxStart = Math.max(0, -d);
                int lxEnd = Math.min(w, w - d);
                if (lxEnd <= lxStart) continue;
                float sx = x + 1 + lxStart, sy = y + 1 + lxStart + d;
                float ex = x + 1 + lxEnd - 1, ey = y + 1 + lxEnd - 1 + d;
                NodeShapeRenderer.queueThinLine(g, sx, sy, ex, ey, 0.5f, 0x160B0B0F);
            }
        }
        FrameProfiler.end("node:overlays");

        FrameProfiler.begin("node:progress");

        List<QuestTask> tasks = node.getEffectiveTasks(server);
        if (QuestChroniclesSettings.get().isShowProgressArc() && !tasks.isEmpty() && sz >= 14) {
            int total = 0, done = 0;
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                for (QuestTask t : tasks) {
                    if (t.isOptional()) continue;
                    total++;
                    if (state.isTaskDone(t)) done++;
                }
            }
            if (total > 0) {
                float fraction = st == QuestState.COMPLETED ? 1f : (float) done / total;
                int arcColor = st == QuestState.COMPLETED ? ctx.colorNodeBorderDone() :
                        st == QuestState.ACTIVE ? state.colorNodeBorderActive() : 0xFF4488BB;
                drawProgressArc(g, x + sz / 2, y + sz / 2, sz / 2 + 3, fraction, arcColor, 0x22FFFFFF);
            }
        }

        if (st == QuestState.UNLOCKED && sz >= 20) {
            float readyPulse = ChronicleOverviewScreen.animPulse(0.65f, 0.35f, 700.0);
            int dotAlpha = (int) (readyPulse * 0xFF) & 0xFF;
            int dotColor = (dotAlpha << 24) | 0x004488FF;
            g.fill(x + sz - 6, y + 1, x + sz - 1, y + 6, dotColor);
        }
        FrameProfiler.end("node:progress");

        FrameProfiler.begin("node:icon");
        FrameProfiler.begin("node:icon:lookup");

        String questPath = displaySource.getId().getPath();
        ResourceLocation customIcon = QuestIconCache.get(questPath);
        ResourceLocation pickedTexture = null;
        if (customIcon == null && !displaySource.getIconTexture().isEmpty()) {
            try {
                pickedTexture = ResourceLocation.parse(displaySource.getIconTexture());
            } catch (Exception ignored) {}
        }
        net.minecraft.world.level.material.Fluid pickedFluid = null;
        if (customIcon == null && pickedTexture == null && !displaySource.getIconFluid().isEmpty()) {
            try {
                net.minecraft.world.level.material.Fluid f = net.minecraftforge.registries.ForgeRegistries.FLUIDS
                        .getValue(ResourceLocation.parse(displaySource.getIconFluid()));
                if (f != null && f != net.minecraft.world.level.material.Fluids.EMPTY) pickedFluid = f;
            } catch (Exception ignored) {}
        }
        FrameProfiler.end("node:icon:lookup");

        int borderThickness = ChronicleOverviewScreen.nodeBorderThickness(sz);
        int fillSz = Math.max(1, sz - borderThickness * 2);
        if (customIcon != null && sz >= 8) {
            int[] dims = QuestIconCache.getDimensions(questPath);
            int pad = Math.max(2, fillSz / 8);
            int iconSz = Math.max(1, fillSz - pad * 2);
            int off = (sz - iconSz) / 2;
            g.blit(customIcon, x + off, y + off, 0, 0, iconSz, iconSz, dims[0], dims[1]);
            if (sz >= 20) {
                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
            }
            state.setDbgCustomIconCount(state.dbgCustomIconCount() + 1);
        } else if (pickedTexture != null && sz >= 8) {
            int pad = Math.max(2, fillSz / 8);
            int iconSz = Math.max(1, fillSz - pad * 2);
            int off = (sz - iconSz) / 2;
            g.blit(pickedTexture, x + off, y + off, 0, 0, iconSz, iconSz, iconSz, iconSz);
            if (sz >= 20) {
                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
            }
            state.setDbgPickedTextureIconCount(state.dbgPickedTextureIconCount() + 1);
        } else if (pickedFluid != null && sz >= 8) {

            int pad = Math.max(2, fillSz / 8);
            int iconSz = Math.max(1, fillSz - pad * 2);
            int off = (sz - iconSz) / 2;
            int col = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(pickedFluid)
                    .getTintColor() | 0xFF000000;
            g.fill(x + off, y + off, x + off + iconSz, y + off + iconSz, col);
            if (sz >= 20) {
                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
            }
            state.setDbgFluidIconCount(state.dbgFluidIconCount() + 1);
        } else {
            Item icon = displaySource.getIconItem();
            QuestTask iconTask = null;
            if (icon == null) {
                iconTask = state.fallbackTaskIconTask(displaySource);
                icon = iconTask != null ?
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(iconTask.getDisplayItemId()) :
                        null;
            } else {
                iconTask = state.matchingIconTask(displaySource, icon);
            }

            if (icon != null && icon != Items.AIR && sz >= 6) {

                float scale = fillSz / 16f * 0.75f;
                float cx = x + sz / 2f, cy = y + sz / 2f;
                ItemStack iconStack = iconTask != null ? state.nbtAwareIconStack(iconTask, icon) :
                        state.cachedIconStack(icon);

                FrameProfiler.begin("node:icon3d");
                g.pose().pushPose();
                try {
                    g.pose().translate(cx, cy, 100f);

                    g.pose().scale(scale, scale, 1f);

                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    withNoIconMipBleed(g, () -> g.renderItem(iconStack, -8, -8));
                } catch (Exception ignored) {} finally {
                    g.pose().popPose();
                }
                FrameProfiler.end("node:icon3d");

                FrameProfiler.begin("node:icon:badge");
                renderStateBadge(g, x, y, sz, st);
                FrameProfiler.end("node:icon:badge");
                state.setDbgFull3DIconCount(state.dbgFull3DIconCount() + 1);
            } else if (sz >= 10) {
                String glyph = switch (st) {
                    case COMPLETED -> "✔";
                    case ACTIVE -> "▶";
                    case LOCKED -> "✕";
                    default -> "○";
                };
                int gc = switch (st) {
                    case COMPLETED -> ctx.colorNodeBorderDone();
                    case ACTIVE -> state.colorNodeBorderActive();
                    case LOCKED -> ctx.isDevMode() ? state.colorNodeBorderDev() : state.colorNodeBorderLocked();
                    default -> state.colorNodeBorderUnlocked();
                };
                g.drawCenteredString(ctx.font(), glyph, x + sz / 2, y + sz / 2 - 4, gc);
                state.setDbgGlyphIconCount(state.dbgGlyphIconCount() + 1);
            }
        }
        FrameProfiler.end("node:icon");

        FrameProfiler.begin("node:badges");

        if (node.isLinkStub() && sz >= 8) {

            float badgeScale = Math.max(0.6f, Math.min(2.2f, sz / 24f));
            int badgeW = Math.round(8 * badgeScale);
            int badgeH = Math.round(7 * badgeScale);
            if (linkTargetNode != null) {
                g.fill(x, y, x + badgeW, y + badgeH, 0xEE101820);
            } else {
                g.fill(x, y, x + badgeW, y + badgeH, 0xEE330808);
            }

            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(badgeScale, badgeScale, 1f);
            if (linkTargetNode != null) {
                g.drawString(ctx.font(), "§b🔗", 1, 0, 0xFF66CCFF, false);
            } else {
                g.drawString(ctx.font(), "§c!", 2, 0, 0xFFFF6666, false);
            }
            g.pose().popPose();
        }

        if (ctx.isDevMode() && sz >= 14 && !node.isLinkStub()) {
            List<String> issues = ctx.validationIssues(node);
            if (!issues.isEmpty()) {
                int bx = x, by = y + sz - 7;
                g.fill(bx, by, bx + 8, by + 7, 0xEE331800);
                g.drawString(ctx.font(), "§6!", bx + 2, by, 0xFFFFAA00, false);
            }
        }

        if (sz >= 14 && ChronicleOverviewScreen.collapsedSubtreeRoots.contains(node.getId())) {
            int bx = x + sz - 8, by = y + sz - 7;
            g.fill(bx, by, bx + 8, by + 7, 0xEE10182A);
            g.drawString(ctx.font(), "§b▶", bx, by, 0xFF66CCFF, false);
        }
        FrameProfiler.end("node:badges");
    }

    void renderQuestGroup(GuiGraphics g, QuestGroup grp, int cl, int cr) {
        int sx = (int) (grp.getX() * ctx.posZoom()) + state.viewOffX() + cl;
        int sy = (int) (grp.getY() * ctx.posZoom()) + state.viewOffY() + ChronicleOverviewScreen.HEADER_H;
        int sw = (int) (grp.getWidth() * ctx.posZoom());
        int sh = (int) (grp.getHeight() * ctx.posZoom());

        if (sx + sw < cl || sx > cr || sy + sh < ChronicleOverviewScreen.HEADER_H || sy > ctx.height()) return;

        g.fill(sx, sy, sx + sw, sy + sh, grp.getColor());

        int bc = grp.getBorderColor();
        g.fill(sx, sy, sx + sw, sy + 1, bc);
        g.fill(sx, sy + sh - 1, sx + sw, sy + sh, bc);
        g.fill(sx, sy, sx + 1, sy + sh, bc);
        g.fill(sx + sw - 1, sy, sx + sw, sy + sh, bc);

        g.fill(sx + 1, sy + 1, sx + sw - 1, sy + ChronicleOverviewScreen.GROUP_LABEL_BAR_H,
                (grp.getBorderColor() & 0x00FFFFFF) | 0x55000000);

        if (sw > 20) {
            String label = grp.getLabel();
            int maxLabelW = sw - 8;
            if (ctx.font().width(label.replaceAll("§.", "")) > maxLabelW) {
                label = ctx.font().plainSubstrByWidth(label, maxLabelW - 6) + "…";
            }
            g.drawString(ctx.font(), "§f" + label, sx + 4, sy + 2, 0xFFFFFFFF);
        }

        List<QuestGroup.GroupIcon> icons = grp.getIcons();
        if (!icons.isEmpty() && sw >= 24 && sh > ChronicleOverviewScreen.GROUP_LABEL_BAR_H + 14) {
            int iconSz = 10, gap = 2;
            int stripY = sy + ChronicleOverviewScreen.GROUP_LABEL_BAR_H + 2;
            int ix = sx + sw - 4;
            for (int i = icons.size() - 1; i >= 0 && ix - iconSz >= sx + 4; i--) {
                ix -= iconSz;
                renderGroupIcon(g, icons.get(i), ix, stripY, iconSz);
                ix -= gap;
            }
        }
    }

    void renderGroupIcon(GuiGraphics g, QuestGroup.GroupIcon icon, int x, int y, int size) {
        try {
            switch (icon.kind) {
                case ITEM -> {
                    Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getValue(ResourceLocation.parse(icon.id));
                    if (item == null || item == Items.AIR) return;
                    float scale = size / 16f;
                    g.pose().pushPose();
                    try {
                        g.pose().translate(x + size / 2f, y + size / 2f, 100f);
                        g.pose().scale(scale, scale, 1f);
                        ItemStack groupIconStack = new ItemStack(item);
                        withNoIconMipBleed(g, () -> g.renderItem(groupIconStack, -8, -8));
                    } finally {

                        g.pose().popPose();
                    }
                }
                case FLUID -> {
                    net.minecraft.world.level.material.Fluid fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS
                            .getValue(ResourceLocation.parse(icon.id));
                    ChroniclesUIKit.drawFluidIcon(g, fluid, x, y, size);
                    g.fill(x, y, x + size, y + 1, 0xFF444455);
                    g.fill(x, y + size - 1, x + size, y + size, 0xFF444455);
                    g.fill(x, y, x + 1, y + size, 0xFF444455);
                    g.fill(x + size - 1, y, x + size, y + size, 0xFF444455);
                }
                case TEXTURE -> g.blit(ResourceLocation.parse(icon.id), x, y, 0, 0, size, size, size, size);
            }
        } catch (Exception ignored) {

        }
    }

    @Nullable
    QuestGroup groupAtLabelBar(double mx, double my, int cl) {
        for (QuestGroup grp : QuestGroupManager.forChapter(ctx.selectedChapter())) {
            int sx = (int) (grp.getX() * ctx.posZoom()) + state.viewOffX() + cl;
            int sy = (int) (grp.getY() * ctx.posZoom()) + state.viewOffY() + ChronicleOverviewScreen.HEADER_H;
            int sw = (int) (grp.getWidth() * ctx.posZoom());
            if (mx >= sx && mx <= sx + sw && my >= sy && my <= sy + ChronicleOverviewScreen.GROUP_LABEL_BAR_H) {
                return grp;
            }
        }
        return null;
    }

    BackgroundPictureConfig.Picture pictureAt(double mx, double my, int cl) {
        List<BackgroundPictureConfig.Picture> pics = BackgroundPictureConfig.get(ctx.selectedChapter());
        BackgroundPictureConfig.Picture hit = null;
        for (BackgroundPictureConfig.Picture pic : pics) {
            int[] rect = BackgroundPictureRenderer.screenRect(pic, cl, ChronicleOverviewScreen.HEADER_H,
                    ctx.posZoom(), state.viewOffX(), state.viewOffY());
            if (mx >= rect[0] && mx <= rect[2] && my >= rect[1] && my <= rect[3]) hit = pic;
        }
        return hit;
    }

    void drawProgressArc(GuiGraphics g, int cx, int cy, int r,
                         float fraction, int fillColor, int bgColor) {
        double gs = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        float s = (float) (1.0 / gs);

        g.pose().pushPose();
        g.pose().scale(s, s, 1f);

        int pcx = (int) Math.round(cx * gs);
        int pcy = (int) Math.round(cy * gs);

        for (int dr = 0; dr <= 1; dr++) {
            int pr = (int) Math.round((r - dr * 0.5) * gs);
            if (pr <= 0) continue;
            int steps = Math.max(64, pr * 5);
            for (int i = 0; i < steps; i++) {
                double angle = (i * 2.0 * Math.PI / steps) - Math.PI / 2.0;
                int px = (int) Math.round(pcx + pr * Math.cos(angle));
                int py = (int) Math.round(pcy + pr * Math.sin(angle));
                int col = (i < fraction * steps) ? fillColor : bgColor;
                if ((col >>> 24) == 0) continue;
                NodeShapeRenderer.queueFillRect(g, px, py, px + 1, py + 1, col);
            }
        }

        g.pose().popPose();
    }

    void renderStateBadge(GuiGraphics g, int nx, int ny, int sz, QuestState st) {
        int badgeSz = Math.min(8, Math.max(4, sz / 5));
        int bx = nx + sz - badgeSz - 1, by = ny + sz - badgeSz - 1;
        int bc = switch (st) {
            case COMPLETED -> ctx.colorNodeBorderDone();
            case ACTIVE -> state.colorNodeBorderActive();
            case LOCKED -> state.colorNodeBorderLocked();
            default -> 0xFF4488FF;
        };
        NodeShapeRenderer.queueFillRect(g, bx - 1, by - 1, bx + badgeSz + 1, by + badgeSz + 1, 0xAA0B0B0F);
        NodeShapeRenderer.queueFillRect(g, bx, by, bx + badgeSz, by + badgeSz, bc);
    }

    void renderNodeTooltip(GuiGraphics g, QuestNode node, int mx, int my) {
        QuestNode linkTarget = state.resolveLinkTarget(node);
        node = linkTarget != null ? linkTarget : node;

        QuestState st = ctx.getState(node);
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        String title = node.getEffectiveTitleRaw(server).getString();
        String sub = node.getSubtitle() != null && !node.getSubtitle().isBlank() ? node.getSubtitle() : null;

        boolean showFull = net.minecraft.client.gui.screens.Screen.hasShiftDown();

        List<String> lines = new java.util.ArrayList<>();
        lines.add("§f" + title);
        if (sub != null) lines.add("§8" + sub);
        if (node.isOptional()) lines.add("§d★ Optional quest");

        if (showFull) {

            List<QuestTask> tasks = node.getEffectiveTasks(server);
            int taskDone = 0, taskTotal = 0;
            List<String> taskLines = new java.util.ArrayList<>();
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                for (QuestTask t : tasks) {
                    if (t.isOptional()) continue;
                    taskTotal++;
                    boolean done = state.isTaskDone(t);
                    if (done) taskDone++;
                    String prog = t.getProgressString(player);
                    String check = done ? "§a✔ " : "§8✗ ";
                    taskLines.add(check + "§7" + t.getDescription().getString() +
                            (prog != null && !prog.isBlank() ? " §8(" + prog + ")" : ""));
                }
            }

            String stateLine = switch (st) {
                case COMPLETED -> "§a✔ Complete";
                case ACTIVE -> "§e▶ In progress — " + taskDone + "/" + taskTotal;
                case UNLOCKED -> "§b○ Ready to start";
                case LOCKED -> "§8✕ Locked";
            };

            List<String> prereqLines = new java.util.ArrayList<>();
            if (!node.getPrerequisites().isEmpty()) {
                for (QuestNode req : node.getPrerequisites()) {
                    QuestState rs = ctx.getState(req);
                    String mark = rs == QuestState.COMPLETED ? "§a✔" : "§8○";
                    prereqLines.add("  " + mark + " §8" + req.getEffectiveTitleRaw(server).getString());
                }
            }

            lines.add(stateLine);
            if (!taskLines.isEmpty()) {
                lines.add("§8─────────────");
                lines.addAll(taskLines.stream().limit(6).toList());
                if (taskLines.size() > 6) lines.add("§8  … +" + (taskLines.size() - 6) + " more");
            }
            if (!prereqLines.isEmpty() && st == QuestState.LOCKED) {
                lines.add("§8─────────────");
                lines.add("§8Requires:");
                lines.addAll(prereqLines.stream().limit(4).toList());
            }

            if (ctx.isDevMode()) {
                List<String> issues = ctx.validationIssues(node);
                if (!issues.isEmpty()) {
                    lines.add("§8─────────────");
                    lines.add("§6⚠ Validation issues:");
                    issues.forEach(i -> lines.add("  §e• §7" + i));
                }
            }
        } else {
            lines.add("§8Hold Shift for details");
        }

        float ts = QuestChroniclesSettings.get().getTextScaleMultiplier();
        int lineH = Math.round((ctx.font().lineHeight + 2) * ts);
        int padH = 6, padW = 8;
        int tipW = Math.round(lines.stream().mapToInt(ctx.font()::width).max().orElse(60) * ts) + padW * 2;
        int tipH = lines.size() * lineH + padH * 2;

        int tx = mx + 10, ty = my + 12;
        if (tx + tipW > ctx.width() - 4) tx = mx - tipW - 4;
        if (ty + tipH > ctx.height() - 4) ty = my - tipH - 4;

        tx = Math.max(4, tx);
        ty = Math.max(4, ty);

        if (state.minimapOpen()) {
            int[] mb = state.minimapBounds(ctx.width());
            boolean overlapsX = tx < mb[2] && tx + tipW > mb[0];
            boolean overlapsY = ty < mb[3] && ty + tipH > mb[1];
            if (overlapsX && overlapsY) {
                int leftOfMap = mb[0] - tipW - 4;
                if (leftOfMap >= 4) tx = leftOfMap;
                else ty = Math.max(4, mb[1] - tipH - 4);
            }
        }

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.flush();

        g.fill(tx, ty, tx + tipW, ty + tipH, 0xFF0D0D14);
        g.fill(tx, ty, tx + tipW, ty + 1, state.colorBorderLit());
        g.fill(tx, ty + tipH - 1, tx + tipW, ty + tipH, state.colorBorderLit());
        g.fill(tx, ty, tx + 1, ty + tipH, state.colorBorderLit());
        g.fill(tx + tipW - 1, ty, tx + tipW, ty + tipH, state.colorBorderLit());
        g.fill(tx, ty, tx + 1, ty + tipH, 0xFF884499);

        int lx = tx + padW, ly = ty + padH;
        for (String line : lines) {
            ChroniclesUIKit.drawScaledString(g, ctx.font(), line, lx, ly, 0xFFCCCCDD, ts);
            ly += lineH;
        }
        g.pose().popPose();
    }
}
