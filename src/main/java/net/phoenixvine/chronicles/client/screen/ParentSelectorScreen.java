package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ParentSelectorScreen extends Screen {

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

    private static final int LIST_TOP = 72;
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
        super(Component.literal(singleSelect ? "Select Quest" : "Select Prerequisites"));
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
        this.filteredNodes.addAll(this.allAvailableNodes);
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
        net.phoenixvine.chronicles.model.CategoryDefinition cat = net.phoenixvine.chronicles.registry.CategoryRegistry
                .categoryFor(node.getChapter());
        return cat != null ? cat.displayName() : "￿";
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.resultButtons.clear();
        int midX = this.width / 2;

        this.searchBox = new EditBox(this.font, midX - 140, 47, 280, 16, Component.literal("Search..."));
        this.searchBox.setHint(Component.literal("§8Type to filter nodes..."));
        this.searchBox.setValue(pendingQuery);
        this.searchBox.setResponder(this::updateSearchFilter);
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        int midXf = this.width / 2;
        if (singleSelect) {
            this.addRenderableWidget(Button.builder(Component.literal("§7[ CANCEL ]"), b -> {
                if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
            }).bounds(midXf - 50, this.height - 28, 100, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("§a[ DONE ]"), b -> {
                if (this.minecraft != null) this.minecraft.setScreen(this.parentScreen);
                this.onSelectionComplete.accept(new ArrayList<>(this.selected));
            }).bounds(midXf - 105, this.height - 28, 100, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("§7[ CANCEL ]"), b -> {
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
        String cleanQuery = query.toLowerCase().trim();
        this.filteredNodes.clear();

        for (QuestNode node : this.allAvailableNodes) {
            boolean matchesPath = node.getId().getPath().toLowerCase().contains(cleanQuery);
            boolean matchesTitle = node.getTitle().getString().toLowerCase().contains(cleanQuery);

            if (cleanQuery.isEmpty() || matchesPath || matchesTitle) {
                this.filteredNodes.add(node);
            }
        }

        sortSelectedFirst();
        this.scrollOffset = 0;
        rebuildResultButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
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

        int visibleRows = visibleRows();
        if (this.filteredNodes.isEmpty()) {
            graphics.drawCenteredString(this.font, "§8No matching nodes located.", midX, 90,
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

        super.render(graphics, mouseX, mouseY, partialTicks);

        graphics.flush();
        graphics.pose().popPose();
    }
}
