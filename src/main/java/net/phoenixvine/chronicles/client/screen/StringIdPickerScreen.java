package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class StringIdPickerScreen extends Screen {

    private static final int COL_HOVER = 0xFF1E1E2A;

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 200;
    private static final int HEADER_H = 22;
    private static final int SEARCH_H = 16;
    private static final int FOOTER_H = 16;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final String headerLabel;
    private final List<String> allIds;
    private final List<String> displayIds = new ArrayList<>();
    private final Consumer<String> onPick;

    private int scrollOffset = 0;
    private String hoveredId = null;

    private EditBox searchBox;
    private String searchQuery = "";

    private int panelLeft, panelTop;

    public StringIdPickerScreen(Screen parent, String headerLabel, Collection<String> ids,
                                Consumer<String> onPick) {
        super(Component.literal(headerLabel));
        this.parent = parent;
        this.headerLabel = headerLabel;
        this.allIds = new ArrayList<>(ids);
        this.allIds.sort(null);
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        clearWidgets();
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;

        int searchY = panelTop + HEADER_H + 2;
        searchBox = new EditBox(font, panelLeft + 4, searchY, PANEL_W - 8, SEARCH_H, Component.empty());
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("phoenix_chronicles.ui.search_hint"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(q -> {
            searchQuery = q;
            scrollOffset = 0;
            rebuildList();
        });
        addRenderableWidget(searchBox);

        rebuildList();
    }

    private void rebuildList() {
        displayIds.clear();
        String q = searchQuery.toLowerCase().trim();
        for (String id : allIds) {
            if (q.isEmpty() || id.toLowerCase().contains(q)) displayIds.add(id);
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.flush();
        g.fill(0, 0, width, height, ChroniclesThemePalette.BG);

        g.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + PANEL_H, ChroniclesThemePalette.PANEL);
        ChroniclesUIKit.drawBorder(g, panelLeft, panelTop, PANEL_W, PANEL_H, ChroniclesThemePalette.BORDER_LIT);

        g.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + HEADER_H, ChroniclesThemePalette.HEADER);
        g.fill(panelLeft, panelTop + HEADER_H - 1, panelLeft + PANEL_W, panelTop + HEADER_H,
                ChroniclesThemePalette.BORDER);
        g.drawCenteredString(font, "§f" + headerLabel, panelLeft + PANEL_W / 2, panelTop + 7,
                ChroniclesThemePalette.TEXT);

        int footerY = panelTop + PANEL_H - FOOTER_H;
        g.fill(panelLeft, footerY, panelLeft + PANEL_W, footerY + 1, ChroniclesThemePalette.BORDER);
        g.fill(panelLeft, footerY, panelLeft + PANEL_W, panelTop + PANEL_H, ChroniclesThemePalette.PANEL_DARK);
        g.drawString(font, "§8" + displayIds.size() + " - Esc to cancel", panelLeft + 4, footerY + 4,
                ChroniclesThemePalette.TEXT_FAINT);

        super.render(g, mx, my, partial);

        int listTop = panelTop + HEADER_H + SEARCH_H + 4;
        int listBottom = footerY - 2;
        int visRows = Math.max(1, (listBottom - listTop) / ROW_H);

        g.enableScissor(panelLeft, listTop, panelLeft + PANEL_W, listBottom);
        hoveredId = null;

        for (int i = 0; i < visRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= displayIds.size()) break;
            String id = displayIds.get(idx);
            int ry = listTop + i * ROW_H;

            boolean hov = mx >= panelLeft + 2 && mx < panelLeft + PANEL_W - 2 && my >= ry && my < ry + ROW_H;
            if (hov) g.fill(panelLeft + 2, ry, panelLeft + PANEL_W - 2, ry + ROW_H, COL_HOVER);

            String idStr = id;
            int maxW = PANEL_W - 8;
            if (font.width(idStr) > maxW) idStr = font.plainSubstrByWidth(idStr, maxW - 6) + "…";
            g.drawString(font, (hov ? "§f" : "§7") + idStr, panelLeft + 4, ry + 3, ChroniclesThemePalette.TEXT_DIM);

            if (hov) hoveredId = id;
        }
        g.disableScissor();

        if (hoveredId != null) {
            g.renderTooltip(font, Component.literal(hoveredId), mx, my);
        }

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && hoveredId != null) {
            onPick.accept(hoveredId);
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        if (btn == 0 && (mx < panelLeft || mx >= panelLeft + PANEL_W || my < panelTop || my >= panelTop + PANEL_H)) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int footerY = panelTop + PANEL_H - FOOTER_H;
        int listTop = panelTop + HEADER_H + SEARCH_H + 4;
        int listBottom = footerY - 2;
        int visRows = Math.max(1, (listBottom - listTop) / ROW_H);
        int maxScroll = Math.max(0, displayIds.size() - visRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta), maxScroll));
        return true;
    }
}
