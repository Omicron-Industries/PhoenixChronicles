package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.common.model.CategoryDefinition;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.CategoryRegistry;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;

public class ChapterDependencyScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 250;
    private static final int HEADER_H = 20;
    private static final int ROW_H = 14;
    private static final int FOOTER_H = 26;
    private static final int INFO_H = 24;

    private record Row(String key, String label, boolean category, boolean header) {}

    private final Screen parent;
    private final Set<String> chapters;
    private final Set<String> categories;
    private final BiConsumer<Set<String>, Set<String>> onDone;
    private final List<Row> rows = new ArrayList<>();

    private int panelX, panelY;
    private int scroll = 0;

    public ChapterDependencyScreen(Screen parent, String ownChapter, Set<String> chapters, Set<String> categories,
                                   BiConsumer<Set<String>, Set<String>> onDone) {
        super(Component.literal("Chapter Dependencies"));
        this.parent = parent;
        this.chapters = new LinkedHashSet<>(chapters);
        this.categories = new LinkedHashSet<>(categories);
        this.onDone = onDone;

        List<CategoryDefinition> categoryList = CategoryRegistry.getCategories();
        if (!categoryList.isEmpty()) {
            rows.add(new Row("", "Categories (every chapter in it)", true, true));
            for (CategoryDefinition c : categoryList) rows.add(new Row(c.id(), c.displayName(), true, false));
        }
        Set<String> chapterIds = new TreeSet<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            String c = n.getChapter();
            if (c != null && !c.isBlank() && !c.equalsIgnoreCase(ownChapter))
                chapterIds.add(c.toUpperCase(Locale.ROOT));
        }
        rows.add(new Row("", "Chapters", false, true));
        for (String c : chapterIds) rows.add(new Row(c, friendly(c), false, false));
    }

    private static String friendly(String chapter) {
        String resolved = ChapterConfig.getResolvedDisplayName(chapter);
        if (resolved != null) return resolved;
        StringBuilder sb = new StringBuilder();
        for (String w : chapter.toLowerCase(Locale.ROOT).replace("_", " ").split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private int visibleRows() {
        return Math.max(1, (PANEL_H - HEADER_H - INFO_H - FOOTER_H) / ROW_H);
    }

    private int listTop() {
        return panelY + HEADER_H + INFO_H;
    }

    @Override
    protected void init() {
        panelX = width / 2 - PANEL_W / 2;
        panelY = height / 2 - PANEL_H / 2;
        int footY = panelY + PANEL_H - FOOTER_H + 5;
        addRenderableWidget(Button.builder(Component.literal("§cClear"), b -> {
            chapters.clear();
            categories.clear();
        }).bounds(panelX + 6, footY, 60, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§7Cancel"), b -> close())
                .bounds(panelX + PANEL_W - 132, footY, 60, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§aDone"), b -> {
            onDone.accept(chapters, categories);
            close();
        }).bounds(panelX + PANEL_W - 66, footY, 60, 16).build());
    }

    private void close() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        close();
    }

    private boolean isPicked(Row row) {
        return row.category() ? categories.contains(row.key()) : chapters.contains(row.key());
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelX, panelY, PANEL_W, PANEL_H, HEADER_H,
                "Chapter Dependencies");
        g.drawString(font, ChroniclesUIKit.fitText(font,
                "§7Locked until every picked chapter is complete,", PANEL_W - 12), panelX + 6,
                panelY + HEADER_H + 3, ChroniclesThemePalette.TEXT_DIM, false);
        g.drawString(font, ChroniclesUIKit.fitText(font,
                "§7on top of this quest's own prerequisites.", PANEL_W - 12), panelX + 6,
                panelY + HEADER_H + 13, ChroniclesThemePalette.TEXT_DIM, false);

        int top = listTop();
        int visible = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= rows.size()) break;
            Row row = rows.get(idx);
            int y = top + i * ROW_H;
            if (row.header()) {
                g.drawString(font, "§8" + row.label(), panelX + 6, y + 3, ChroniclesThemePalette.TEXT_FAINT, false);
                continue;
            }
            boolean hov = mx >= panelX + 4 && mx < panelX + PANEL_W - 4 && my >= y && my < y + ROW_H;
            if (hov) g.fill(panelX + 4, y, panelX + PANEL_W - 4, y + ROW_H, 0x22FFFFFF);
            boolean picked = isPicked(row);
            g.drawString(font, picked ? "§a✔" : "§8☐", panelX + 8, y + 3, ChroniclesThemePalette.TEXT, false);
            g.drawString(font, (picked ? "§f" : "§7") + ChroniclesUIKit.fitText(font, row.label(), PANEL_W - 40),
                    panelX + 22, y + 3, ChroniclesThemePalette.TEXT, false);
        }
        if (rows.size() > visible) {
            String more = (scroll + 1) + "-" + Math.min(rows.size(), scroll + visible) + " of " + rows.size();
            g.drawString(font, "§8" + more, panelX + PANEL_W - 6 - font.width(more), panelY + PANEL_H - FOOTER_H - 9,
                    ChroniclesThemePalette.TEXT_DIM, false);
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && mx >= panelX + 4 && mx < panelX + PANEL_W - 4) {
            int idx = scroll + (int) ((my - listTop()) / ROW_H);
            if (my >= listTop() && (my - listTop()) / ROW_H < visibleRows() && idx >= 0 && idx < rows.size()) {
                Row row = rows.get(idx);
                if (!row.header()) {
                    Set<String> target = row.category() ? categories : chapters;
                    if (!target.remove(row.key())) target.add(row.key());
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, rows.size() - visibleRows());
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
