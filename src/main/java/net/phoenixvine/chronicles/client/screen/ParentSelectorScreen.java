package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.common.model.CategoryDefinition;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.CategoryRegistry;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.wiki.client.suite.SuiteHudBar;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

public class ParentSelectorScreen extends Screen implements SuiteHudBar.Aware {

    private final Screen parentScreen;
    private final QuestNode editingNode;
    private final Consumer<List<QuestNode>> onSelectionComplete;
    private final List<QuestNode> selected = new ArrayList<>();
    private final boolean singleSelect;

    private EditBox searchBox;
    private String pendingQuery = "";
    private final List<QuestNode> filteredNodes = new ArrayList<>();
    private final List<QuestNode> allAvailableNodes = new ArrayList<>();
    private final List<Button> resultButtons = new ArrayList<>();
    private int scrollOffset = 0;

    private String chapterFilter = "";
    private boolean chapterDropdownOpen = false;
    private final List<String> availableChapters = new ArrayList<>();
    private int chapterBtnX, chapterBtnY, chapterBtnW;
    private static final int CHAPTER_BTN_H = 16;
    private static final int CHAPTER_ROW_H = 16;
    private int chapterDropScroll = 0;

    private static final int CHAPTER_ROW_Y = 68;
    private static final int LIST_TOP = 92;
    private static final int ROW_STRIDE = 22;

    private int arrowUpX, arrowUpY, arrowUpW, arrowDownX, arrowDownY, arrowDownW, arrowH;

    private int visibleRows() {
        int footerTop = this.height - 46;
        return Math.max(1, (footerTop - LIST_TOP) / ROW_STRIDE);
    }

    public static ParentSelectorScreen multiSelect(Screen parentScreen, QuestNode editingNode,
                                                   List<QuestNode> initiallySelected,
                                                   Consumer<List<QuestNode>> onDone) {
        return new ParentSelectorScreen(parentScreen, editingNode, initiallySelected, onDone, false);
    }

    public static ParentSelectorScreen singleSelect(Screen parentScreen, QuestNode editingNode,
                                                    Consumer<QuestNode> onDone) {
        return new ParentSelectorScreen(parentScreen, editingNode, List.of(),
                list -> {
                    if (!list.isEmpty()) onDone.accept(list.get(0));
                }, true);
    }

    private ParentSelectorScreen(Screen parentScreen, QuestNode editingNode, List<QuestNode> initiallySelected,
                                 Consumer<List<QuestNode>> onSelectionComplete, boolean singleSelect) {
        super(Component.translatable(singleSelect ? "phoenix_chronicles.screen.parent_selector.title_single" :
                "phoenix_chronicles.screen.parent_selector.title_multi"));
        this.parentScreen = parentScreen;
        this.editingNode = editingNode;
        this.onSelectionComplete = onSelectionComplete;
        this.singleSelect = singleSelect;
        this.selected.addAll(initiallySelected);

        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (this.editingNode == null || !node.getId().equals(this.editingNode.getId())) {
                this.allAvailableNodes.add(node);
            }
        }

        TreeSet<String> chapterSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (QuestNode node : this.allAvailableNodes) {
            if (node.getChapter() != null && !node.getChapter().isEmpty()) chapterSet.add(node.getChapter());
        }
        this.availableChapters.add("");
        this.availableChapters.addAll(chapterSet);

        if (this.editingNode != null && this.editingNode.getChapter() != null &&
                chapterSet.contains(this.editingNode.getChapter())) {
            this.chapterFilter = this.editingNode.getChapter();
        }

        recomputeFilteredNodes();
    }

    private String friendlyChapter(String chapter) {
        if (chapter == null || chapter.isEmpty())
            return net.minecraft.client.resources.language.I18n.get("phoenix_chronicles.ui.all_chapters");
        String resolved = ChapterConfig.getResolvedDisplayName(chapter);
        if (resolved != null) return resolved;
        StringBuilder sb = new StringBuilder();
        for (String w : chapter.toLowerCase().replace("_", " ").split(" "))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private void recomputeFilteredNodes() {
        String cleanQuery = this.pendingQuery.toLowerCase().trim();
        this.filteredNodes.clear();

        for (QuestNode node : this.allAvailableNodes) {
            if (!this.chapterFilter.isEmpty() && !this.chapterFilter.equalsIgnoreCase(node.getChapter())) continue;

            boolean matchesPath = node.getId().getPath().toLowerCase().contains(cleanQuery);
            boolean matchesTitle = node.getTitle().getString().toLowerCase().contains(cleanQuery);

            if (cleanQuery.isEmpty() || matchesPath || matchesTitle) {
                this.filteredNodes.add(node);
            }
        }

        sortSelectedFirst();
    }

    private void sortSelectedFirst() {
        this.filteredNodes.sort(java.util.Comparator
                .comparing((QuestNode n) -> !this.selected.contains(n))
                .thenComparing(this::categoryLabelOf, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(QuestNode::getChapter, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(n -> n.getTitle().getString(), String.CASE_INSENSITIVE_ORDER));
    }

    private String categoryLabelOf(QuestNode node) {
        CategoryDefinition cat = CategoryRegistry
                .categoryFor(node.getChapter());
        return cat != null ? cat.displayName() : "￿";
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.resultButtons.clear();
        int midX = this.width / 2;

        this.searchBox = new EditBox(this.font, midX - 140, 47, 280, 16,
                Component.translatable("phoenix_chronicles.screen.parent_selector.search_value"));
        this.searchBox.setHint(Component.translatable("phoenix_chronicles.screen.parent_selector.search_hint"));
        this.searchBox.setValue(pendingQuery);
        this.searchBox.setResponder(this::updateSearchFilter);
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        int midXf = this.width / 2;
        if (singleSelect) {
            this.addRenderableWidget(
                    Button.builder(Component.translatable("phoenix_chronicles.ui.cancel_bracketed"), b -> {
                        if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
                    }).bounds(midXf - 50, this.height - 28, 100, 20).build());
        } else {
            this.addRenderableWidget(
                    Button.builder(Component.translatable("phoenix_chronicles.ui.done_bracketed"), b -> {
                        if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
                        this.onSelectionComplete.accept(new ArrayList<>(this.selected));
                    }).bounds(midXf - 105, this.height - 28, 100, 20).build());
            this.addRenderableWidget(
                    Button.builder(Component.translatable("phoenix_chronicles.ui.cancel_bracketed"), b -> {
                        if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
                    }).bounds(midXf + 5, this.height - 28, 100, 20).build());
        }

        rebuildResultButtons();
    }

    private void rebuildResultButtons() {
        for (Button b : resultButtons) removeWidget(b);
        resultButtons.clear();

        int visibleRows = visibleRows();
        int maxScroll = Math.max(0, this.filteredNodes.size() - visibleRows);
        if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;
        if (this.scrollOffset < 0) this.scrollOffset = 0;

        int midX = this.width / 2;
        int visibleCount = Math.min(this.filteredNodes.size() - this.scrollOffset, visibleRows);

        for (int i = 0; i < visibleCount; i++) {
            QuestNode targetNode = this.filteredNodes.get(this.scrollOffset + i);
            String base = targetNode.getId().getPath() + " (" + targetNode.getTitle().getString() + ")";

            boolean isSel = selected.contains(targetNode);
            String label = singleSelect ? base : (isSel ? "§a[x] " : "§7[ ] ") + base;
            Button btn = Button.builder(Component.literal(label), b -> {
                if (singleSelect) {
                    if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
                    this.onSelectionComplete.accept(List.of(targetNode));
                    return;
                }
                if (!selected.remove(targetNode)) selected.add(targetNode);
                sortSelectedFirst();
                rebuildResultButtons();
            }).bounds(midX - 150, LIST_TOP + (i * ROW_STRIDE), 300, 18).build();
            this.addRenderableWidget(btn);
            this.resultButtons.add(btn);
        }
    }

    private void updateSearchFilter(String query) {
        this.pendingQuery = query;
        recomputeFilteredNodes();
        this.scrollOffset = 0;
        rebuildResultButtons();
    }

    private int visibleDropRows() {
        int dropY = chapterBtnY + CHAPTER_BTN_H;
        int panelBottom = this.height - 10;
        int maxRowsFit = Math.max(1, (panelBottom - dropY) / CHAPTER_ROW_H);
        return Math.min(Math.min(this.availableChapters.size(), 8), maxRowsFit);
    }

    private void setChapterFilter(String chapter) {
        this.chapterFilter = chapter;
        this.chapterDropdownOpen = false;
        recomputeFilteredNodes();
        this.scrollOffset = 0;
        rebuildResultButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.chapterDropdownOpen) {
            int visibleDropRows = visibleDropRows();
            int maxDropScroll = Math.max(0, this.availableChapters.size() - visibleDropRows);
            chapterDropScroll = Math.max(0, Math.min(maxDropScroll, chapterDropScroll - (int) Math.signum(delta)));
            return true;
        }
        int visibleRows = visibleRows();
        if (this.filteredNodes.size() > visibleRows) {
            int maxScroll = Math.max(0, this.filteredNodes.size() - visibleRows);
            this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (int) Math.signum(delta)));
            rebuildResultButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (this.chapterDropdownOpen) {
            if (btn == 0) {
                int visibleDropRows = visibleDropRows();
                int dropY = chapterBtnY + CHAPTER_BTN_H;
                int maxDropScroll = Math.max(0, this.availableChapters.size() - visibleDropRows);
                chapterDropScroll = Math.max(0, Math.min(maxDropScroll, chapterDropScroll));
                for (int i = 0; i < visibleDropRows; i++) {
                    int rowIdx = chapterDropScroll + i;
                    if (rowIdx >= this.availableChapters.size()) break;
                    int ry = dropY + i * CHAPTER_ROW_H;
                    if (mx >= chapterBtnX && mx < chapterBtnX + chapterBtnW && my >= ry && my < ry + CHAPTER_ROW_H) {
                        setChapterFilter(this.availableChapters.get(rowIdx));
                        return true;
                    }
                }
            }

            this.chapterDropdownOpen = false;
            return true;
        }
        if (btn == 0 && mx >= chapterBtnX && mx < chapterBtnX + chapterBtnW &&
                my >= chapterBtnY && my < chapterBtnY + CHAPTER_BTN_H) {
            this.chapterDropdownOpen = true;
            this.chapterDropScroll = 0;
            return true;
        }
        if (btn == 0 && this.filteredNodes.size() > visibleRows()) {
            int maxScroll = Math.max(0, this.filteredNodes.size() - visibleRows());
            if (mx >= arrowUpX && mx < arrowUpX + arrowUpW && my >= arrowUpY && my < arrowUpY + arrowH &&
                    scrollOffset > 0) {
                scrollOffset--;
                rebuildResultButtons();
                return true;
            }
            if (mx >= arrowDownX && mx < arrowDownX + arrowDownW && my >= arrowDownY && my < arrowDownY + arrowH &&
                    scrollOffset < maxScroll) {
                scrollOffset++;
                rebuildResultButtons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 300f);
        graphics.flush();
        graphics.fill(0, 0, width, height, ChroniclesThemePalette.BG);

        int midX = this.width / 2;

        graphics.fill(midX - 170, 10, midX + 170, this.height - 6, ChroniclesThemePalette.PANEL);
        ChroniclesUIKit.drawBorder(graphics, midX - 170, 10, 340, this.height - 16, ChroniclesThemePalette.BORDER_LIT);

        graphics.drawCenteredString(this.font,
                singleSelect ? "§dSelect a Quest" : "§dSelect Prerequisites  §8(" + selected.size() + " picked)",
                midX, 18, ChroniclesThemePalette.TEXT);

        graphics.drawCenteredString(this.font,
                singleSelect ? "§7Click a quest to select it" :
                        "§7Click any number of rows to toggle them - §a[x]§7 = selected",
                midX, 30, ChroniclesThemePalette.TEXT_FAINT);

        chapterBtnX = midX - 140;
        chapterBtnY = CHAPTER_ROW_Y;
        chapterBtnW = 280;
        boolean chapterHov = mouseX >= chapterBtnX && mouseX < chapterBtnX + chapterBtnW &&
                mouseY >= chapterBtnY && mouseY < chapterBtnY + CHAPTER_BTN_H;
        graphics.fill(chapterBtnX, chapterBtnY, chapterBtnX + chapterBtnW, chapterBtnY + CHAPTER_BTN_H,
                chapterHov || chapterDropdownOpen ? ChroniclesThemePalette.SEL_TAB : ChroniclesThemePalette.PANEL);
        ChroniclesUIKit.drawBorder(graphics, chapterBtnX, chapterBtnY, chapterBtnW, CHAPTER_BTN_H,
                chapterDropdownOpen ? ChroniclesThemePalette.BORDER_LIT : ChroniclesThemePalette.BORDER);
        graphics.drawString(this.font, "§7Chapter:  §f" + friendlyChapter(chapterFilter),
                chapterBtnX + 5, chapterBtnY + 4, ChroniclesThemePalette.TEXT, false);
        graphics.drawString(this.font, chapterDropdownOpen ? "§f▲" : "§f▼",
                chapterBtnX + chapterBtnW - 12, chapterBtnY + 4, ChroniclesThemePalette.TEXT, false);

        int visibleRows = visibleRows();
        if (this.filteredNodes.isEmpty()) {
            graphics.drawCenteredString(this.font, "§8No matching nodes located.", midX, LIST_TOP + 4,
                    ChroniclesThemePalette.TEXT_FAINT);
        } else if (this.filteredNodes.size() > visibleRows) {
            int shownEnd = Math.min(this.filteredNodes.size(), this.scrollOffset + visibleRows);
            int maxScroll = Math.max(0, this.filteredNodes.size() - visibleRows);
            String label = (this.scrollOffset + 1) + "-" + shownEnd + " of " + this.filteredNodes.size();

            int indicatorY = this.height - 40;
            arrowH = 12;
            arrowUpW = this.font.width("▲") + 8;
            arrowDownW = this.font.width("▼") + 8;
            int labelW = this.font.width(label) + 10;
            int totalW = arrowUpW + labelW + arrowDownW;
            int px = midX - totalW / 2;

            arrowUpX = px;
            arrowUpY = indicatorY - 2;
            arrowDownX = px + arrowUpW + labelW;
            arrowDownY = arrowUpY;

            boolean canUp = scrollOffset > 0;
            boolean canDown = scrollOffset < maxScroll;
            boolean overUp = mouseX >= arrowUpX && mouseX < arrowUpX + arrowUpW && mouseY >= arrowUpY &&
                    mouseY < arrowUpY + arrowH;
            boolean overDown = mouseX >= arrowDownX && mouseX < arrowDownX + arrowDownW && mouseY >= arrowDownY &&
                    mouseY < arrowDownY + arrowH;

            int upColor = !canUp ? 0xFF3A3A42 :
                    (overUp ? ChroniclesThemePalette.TEXT : ChroniclesThemePalette.TEXT_DIM);
            int downColor = !canDown ? 0xFF3A3A42 :
                    (overDown ? ChroniclesThemePalette.TEXT : ChroniclesThemePalette.TEXT_DIM);
            graphics.drawCenteredString(this.font, "▲", arrowUpX + arrowUpW / 2, indicatorY, upColor);
            graphics.drawCenteredString(this.font, "§8" + label, px + arrowUpW + labelW / 2, indicatorY,
                    ChroniclesThemePalette.TEXT_FAINT);
            graphics.drawCenteredString(this.font, "▼", arrowDownX + arrowDownW / 2, indicatorY, downColor);
        }

        if (chapterDropdownOpen) {
            for (Button b : resultButtons) b.visible = false;
        }
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (chapterDropdownOpen) {
            for (Button b : resultButtons) b.visible = true;
            graphics.flush();
            renderChapterDropdown(graphics, mouseX, mouseY);
        }

        graphics.flush();
        graphics.pose().popPose();
    }

    private void renderChapterDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        int visibleDropRows = visibleDropRows();
        int maxDropScroll = Math.max(0, this.availableChapters.size() - visibleDropRows);
        chapterDropScroll = Math.max(0, Math.min(maxDropScroll, chapterDropScroll));

        int dropY = chapterBtnY + CHAPTER_BTN_H;
        int dropH = visibleDropRows * CHAPTER_ROW_H;

        graphics.fill(chapterBtnX, dropY, chapterBtnX + chapterBtnW, dropY + dropH, ChroniclesThemePalette.PANEL_DARK);
        ChroniclesUIKit.drawBorder(graphics, chapterBtnX, dropY, chapterBtnW, dropH, ChroniclesThemePalette.BORDER_LIT);

        for (int i = 0; i < visibleDropRows; i++) {
            int rowIdx = chapterDropScroll + i;
            if (rowIdx >= this.availableChapters.size()) break;
            String chapter = this.availableChapters.get(rowIdx);
            int ry = dropY + i * CHAPTER_ROW_H;

            boolean hov = mouseX >= chapterBtnX && mouseX < chapterBtnX + chapterBtnW &&
                    mouseY >= ry && mouseY < ry + CHAPTER_ROW_H;
            boolean isCurrent = chapter.equalsIgnoreCase(this.chapterFilter);
            if (hov) graphics.fill(chapterBtnX, ry, chapterBtnX + chapterBtnW, ry + CHAPTER_ROW_H,
                    ChroniclesThemePalette.SEL_TAB);

            String prefix = isCurrent ? "§d● " : "§7";
            graphics.drawString(this.font, prefix + "§f" + friendlyChapter(chapter), chapterBtnX + 5, ry + 4,
                    ChroniclesThemePalette.TEXT, false);
        }

        if (maxDropScroll > 0) {
            if (chapterDropScroll > 0)
                graphics.drawString(this.font, "§8▲", chapterBtnX + chapterBtnW - 9, dropY - 9,
                        ChroniclesThemePalette.TEXT_FAINT, false);
            if (chapterDropScroll < maxDropScroll)
                graphics.drawString(this.font, "§8▼", chapterBtnX + chapterBtnW - 9, dropY + dropH + 1,
                        ChroniclesThemePalette.TEXT_FAINT, false);
        }
    }
}
