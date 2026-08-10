package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.List;

class NodeContextMenu implements OverlayComponent {

    private final NodeCtxMenuState state;

    NodeContextMenu(NodeCtxMenuState state) {
        this.state = state;
    }

    @Override
    public boolean isVisible(ScreenContext ctx) {
        return ctx.isDevMode() && state.ctxOpen();
    }

    @Override
    public void render(ScreenContext ctx, GuiGraphics g, int mouseX, int mouseY, int contentLeft, int contentRight) {
        int mx = mouseX, my = mouseY;
        List<ChronicleOverviewScreen.CtxItem> items = state.buildCtxItems();
        int menuH = state.menuHeight(items);
        int x = state.ctxX(), y = state.ctxY();

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        g.flush();

        RenderSystem.disableDepthTest();

        int ctxAlpha = (int) Math.min(255,
                (System.currentTimeMillis() - state.ctxOpenTimeMs()) * 255 / ChronicleOverviewScreen.OPEN_FADE_MS);
        int fadedBg = (ctxAlpha << 24) | (ChronicleOverviewScreen.C_CTX_BG & 0x00FFFFFF);
        g.fill(x + 3, y + 3, x + ChronicleOverviewScreen.CTX_W + 3, y + menuH + 3,
                (Math.min(0x55, ctxAlpha / 3)) << 24);
        g.fill(x, y, x + ChronicleOverviewScreen.CTX_W, y + menuH, fadedBg);
        g.fill(x, y, x + ChronicleOverviewScreen.CTX_W, y + 1, ChronicleOverviewScreen.C_CTX_BORDER);
        g.fill(x, y + menuH - 1, x + ChronicleOverviewScreen.CTX_W, y + menuH, ChronicleOverviewScreen.C_CTX_BORDER);
        g.fill(x, y, x + 1, y + menuH, ChronicleOverviewScreen.C_CTX_BORDER);
        g.fill(x + ChronicleOverviewScreen.CTX_W - 1, y, x + ChronicleOverviewScreen.CTX_W, y + menuH,
                ChronicleOverviewScreen.C_CTX_BORDER);

        int iy = y + 2;
        if (state.ctxNode() != null) {
            g.fill(x + 1, iy, x + 3, iy + ChronicleOverviewScreen.CTX_ROW, ChronicleOverviewScreen.C_CTX_BORDER);
            g.drawString(ctx.font(), "§5" + state.shortName(state.ctxNode(), ChronicleOverviewScreen.CTX_W - 12),
                    x + 6, iy + 4, ChronicleOverviewScreen.C_CTX_TEXT);
            iy += ChronicleOverviewScreen.CTX_ROW;
        }

        boolean moveCatRowHov = false;
        for (ChronicleOverviewScreen.CtxItem item : items) {
            if (item.isSep()) {
                g.fill(x + 6, iy + 2, x + ChronicleOverviewScreen.CTX_W - 6, iy + 3,
                        ChronicleOverviewScreen.C_CTX_SEP);
                iy += ChronicleOverviewScreen.CTX_SEP;
                continue;
            }
            boolean hov = mx >= x + 1 && mx <= x + ChronicleOverviewScreen.CTX_W - 1 && my >= iy &&
                    my <= iy + ChronicleOverviewScreen.CTX_ROW;
            if (hov) g.fill(x + 1, iy, x + ChronicleOverviewScreen.CTX_W - 1, iy + ChronicleOverviewScreen.CTX_ROW,
                    ChronicleOverviewScreen.C_CTX_HOVER);
            g.drawString(ctx.font(), (item.isDanger() ? "§c" : item.color()) + item.label(), x + 8, iy + 4,
                    item.isDanger() ? ChronicleOverviewScreen.C_CTX_DANGER : ChronicleOverviewScreen.C_CTX_TEXT);
            if (hov && item.label().contains("Move to Chapter")) moveCatRowHov = true;
            iy += ChronicleOverviewScreen.CTX_ROW;
        }

        List<String> cats = ctx.buildChapterList();
        cats.remove("ALL");
        int subX = state.ctxMoveCatX(cats.size());
        int subY = state.ctxMoveCatYClamped(items, cats.size());
        int visibleRows = Math.min(cats.size(), ChronicleOverviewScreen.CTX_MOVE_CAT_MAX_ROWS);
        int subH = visibleRows * ChronicleOverviewScreen.CTX_ROW + 4;
        boolean overSubmenu = state.ctxMoveCatOpen() && mx >= subX &&
                mx <= subX + ChronicleOverviewScreen.CTX_W +
                        (cats.size() > ChronicleOverviewScreen.CTX_MOVE_CAT_MAX_ROWS ? 6 : 0) &&
                my >= subY && my <= subY + subH;
        boolean wasOpen = state.ctxMoveCatOpen();
        boolean nowOpen = state.ctxNode() != null && (moveCatRowHov || overSubmenu);
        state.setCtxMoveCatOpen(nowOpen);
        if (nowOpen && !wasOpen) state.setCtxMoveCatScroll(0);

        if (nowOpen) {
            int maxScroll = Math.max(0, cats.size() - ChronicleOverviewScreen.CTX_MOVE_CAT_MAX_ROWS);
            int scroll = Math.max(0, Math.min(state.ctxMoveCatScroll(), maxScroll));
            state.setCtxMoveCatScroll(scroll);

            g.fill(subX + 2, subY + 2, subX + ChronicleOverviewScreen.CTX_W + 2, subY + subH + 2, 0x55000000);
            g.fill(subX, subY, subX + ChronicleOverviewScreen.CTX_W, subY + subH, ChronicleOverviewScreen.C_CTX_BG);
            g.fill(subX, subY, subX + ChronicleOverviewScreen.CTX_W, subY + 1, ChronicleOverviewScreen.C_CTX_BORDER);
            g.fill(subX, subY + subH - 1, subX + ChronicleOverviewScreen.CTX_W, subY + subH,
                    ChronicleOverviewScreen.C_CTX_BORDER);
            g.fill(subX, subY, subX + 1, subY + subH, ChronicleOverviewScreen.C_CTX_BORDER);
            g.fill(subX + ChronicleOverviewScreen.CTX_W - 1, subY, subX + ChronicleOverviewScreen.CTX_W,
                    subY + subH, ChronicleOverviewScreen.C_CTX_BORDER);

            g.enableScissor(subX, subY, subX + ChronicleOverviewScreen.CTX_W, subY + subH);
            int sy = subY + 2;
            for (int i = scroll; i < Math.min(cats.size(), scroll + visibleRows + 1); i++) {
                String cat = cats.get(i);
                boolean hov = mx >= subX && mx <= subX + ChronicleOverviewScreen.CTX_W && my >= sy &&
                        my <= sy + ChronicleOverviewScreen.CTX_ROW;
                if (hov) g.fill(subX + 1, sy, subX + ChronicleOverviewScreen.CTX_W - 1,
                        sy + ChronicleOverviewScreen.CTX_ROW, ChronicleOverviewScreen.C_CTX_HOVER);
                String mark = cat.equals(state.ctxNode().getChapter()) ? "§a● " : "§8  ";
                g.drawString(ctx.font(), mark + "§7" + ctx.friendly(cat), subX + 8, sy + 4,
                        ChronicleOverviewScreen.C_CTX_TEXT);
                sy += ChronicleOverviewScreen.CTX_ROW;
            }
            g.disableScissor();

            if (cats.size() > ChronicleOverviewScreen.CTX_MOVE_CAT_MAX_ROWS) {
                int trackH = subH - 4;
                int thumbH = Math.max(10, trackH * visibleRows / cats.size());
                int thumbY = subY + 2 + (maxScroll == 0 ? 0 : (trackH - thumbH) * scroll / maxScroll);
                g.fill(subX + ChronicleOverviewScreen.CTX_W - 3, subY + 2, subX + ChronicleOverviewScreen.CTX_W - 1,
                        subY + subH - 2, 0x33FFFFFF);
                g.fill(subX + ChronicleOverviewScreen.CTX_W - 3, thumbY, subX + ChronicleOverviewScreen.CTX_W - 1,
                        thumbY + thumbH, 0x99FFFFFF);
            }
        }

        g.flush();
        g.pose().popPose();
    }
}
