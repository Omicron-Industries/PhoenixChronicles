package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.ChapterEmergencyItems;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class EmergencyItemsScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int HEADER_H = 20;
    private static final int TAB_H = 22;
    private static final int INFO_H = 14;
    private static final int ROW_H = 20;
    private static final int VISIBLE_ROWS = 8;
    private static final int FOOTER_H = 26;

    private final Screen parent;
    private final QuestNode node;
    private final Supplier<String> chapterSupplier;

    private boolean chapterScope = false;
    private List<ItemStack> chapterItems = new ArrayList<>();

    private int scroll = 0;
    private int panelX, panelY, panelH;

    public EmergencyItemsScreen(Screen parent, QuestNode node, Supplier<String> chapterSupplier) {
        super(Component.literal("Emergency Items"));
        this.parent = parent;
        this.node = node;
        this.chapterSupplier = chapterSupplier;
    }

    private String chapter() {
        String c = chapterSupplier != null ? chapterSupplier.get() : node.getChapter();
        return c == null ? "" : c.trim();
    }

    private static Path configDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
    }

    private List<ItemStack> items() {
        return chapterScope ? new ArrayList<>(chapterItems) : new ArrayList<>(node.getEmergencyItems());
    }

    private void replaceAll(List<ItemStack> stacks) {
        if (chapterScope) {
            chapterItems = new ArrayList<>();
            for (ItemStack s : stacks) chapterItems.add(s.copy());
            ChapterEmergencyItems.save(configDir(), chapter(), chapterItems);
        } else {
            node.clearEmergencyItems();
            for (ItemStack s : stacks) node.addEmergencyItem(s);
        }
    }

    private void addItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        List<ItemStack> l = items();
        l.add(stack.copy());
        replaceAll(l);
    }

    private void setScope(boolean chapterScope) {
        this.chapterScope = chapterScope;
        if (chapterScope) chapterItems = new ArrayList<>(ChapterEmergencyItems.get(chapter()));
        scroll = 0;
        init();
    }

    @Override
    protected void init() {
        clearWidgets();
        panelH = HEADER_H + TAB_H + INFO_H + VISIBLE_ROWS * ROW_H + FOOTER_H;
        panelX = width / 2 - PANEL_W / 2;
        panelY = height / 2 - panelH / 2;

        if (chapterScope) chapterItems = new ArrayList<>(ChapterEmergencyItems.get(chapter()));

        boolean hasChapter = !chapter().isEmpty();
        int tabY = panelY + HEADER_H + 3;
        int tabW = (PANEL_W - 18) / 2;
        Button questTab = Button.builder(
                Component.literal(chapterScope ? "§7This Quest" : "§f▸ This Quest"), b -> setScope(false))
                .bounds(panelX + 6, tabY, tabW, 16).build();
        questTab.active = chapterScope;
        addRenderableWidget(questTab);

        Button chapterTab = Button.builder(
                Component.literal(chapterScope ? "§f▸ Chapter Default" : "§7Chapter Default"), b -> setScope(true))
                .bounds(panelX + 12 + tabW, tabY, tabW, 16)
                .tooltip(Tooltip.create(Component.literal(hasChapter ?
                        "Fallback items for every quest in " + chapter() + " that has no list of its own." :
                        "Pick a chapter for this quest first.")))
                .build();
        chapterTab.active = hasChapter && !chapterScope;
        addRenderableWidget(chapterTab);

        List<ItemStack> list = items();
        int maxScroll = Math.max(0, list.size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int listTop = panelY + HEADER_H + TAB_H + INFO_H;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            int y = listTop + row * ROW_H + 2;
            int right = panelX + PANEL_W - 6;
            final int index = idx;

            addRenderableWidget(Button.builder(Component.literal("§c×"), b -> {
                List<ItemStack> l = items();
                if (index < l.size()) l.remove(index);
                replaceAll(l);
                init();
            }).bounds(right - 14, y, 14, 16)
                    .tooltip(Tooltip.create(Component.literal("Remove")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> changeCount(index, hasShiftDown() ? 10 : 1))
                    .bounds(right - 32, y, 16, 16)
                    .tooltip(Tooltip.create(Component.literal("Add 1 (Shift: 10)")))
                    .build());
            addRenderableWidget(
                    Button.builder(Component.literal("−"), b -> changeCount(index, hasShiftDown() ? -10 : -1))
                            .bounds(right - 50, y, 16, 16)
                            .tooltip(Tooltip.create(Component.literal("Remove 1 (Shift: 10)")))
                            .build());
        }

        int footY = panelY + panelH - FOOTER_H + 5;
        addRenderableWidget(Button.builder(Component.literal("§a+ Add Items…"), b -> {
            if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, this::addItem));
        }).bounds(panelX + 6, footY, 90, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Right-click items in the picker to select several, then press Select.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("§cClear All"), b -> {
            replaceAll(new ArrayList<>());
            scroll = 0;
            init();
        }).bounds(panelX + 100, footY, 70, 16).build());
        addRenderableWidget(Button.builder(Component.literal("§fDone"), b -> onClose())
                .bounds(panelX + PANEL_W - 66, footY, 60, 16).build());
    }

    private void changeCount(int index, int delta) {
        List<ItemStack> l = items();
        if (index < 0 || index >= l.size()) return;
        ItemStack s = l.get(index).copy();
        int max = Math.max(1, s.getMaxStackSize());
        s.setCount(Math.max(1, Math.min(max, s.getCount() + delta)));
        l.set(index, s);
        replaceAll(l);
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int total = items().size();
        if (total > VISIBLE_ROWS) {
            scroll = Math.max(0, Math.min(total - VISIBLE_ROWS, scroll - (int) Math.signum(delta)));
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelX, panelY, PANEL_W, panelH, HEADER_H,
                "Emergency Items");

        List<ItemStack> list = items();
        String info;
        if (chapterScope) {
            info = "§7Default for " + chapter() + " quests with no list of their own.";
        } else if (list.isEmpty()) {
            int fallback = ChapterEmergencyItems.get(chapter()).size();
            info = fallback > 0 ?
                    "§8None set: using the chapter default (" + fallback + " item(s))." :
                    "§8No fallback items. Players can't recover lost items.";
        } else {
            info = "§7Claimable once per player while this quest is active.";
        }
        g.drawString(font, info, panelX + 6, panelY + HEADER_H + TAB_H + 3, ChroniclesThemePalette.TEXT_DIM, false);

        int listTop = panelY + HEADER_H + TAB_H + INFO_H;
        ItemStack hovered = null;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            ItemStack s = list.get(idx);
            int y = listTop + row * ROW_H;
            g.renderItem(s, panelX + 6, y + 2);
            String label = ChroniclesUIKit.fitText(font, s.getHoverName().getString(), PANEL_W - 110);
            g.drawString(font, "§f" + label, panelX + 28, y + 6, ChroniclesThemePalette.TEXT, false);
            String count = "×" + s.getCount();
            g.drawString(font, "§7" + count, panelX + PANEL_W - 56 - 4 - font.width(count), y + 6,
                    ChroniclesThemePalette.TEXT_DIM, false);
            if (mouseX >= panelX + 6 && mouseX < panelX + PANEL_W - 60 && mouseY >= y && mouseY < y + ROW_H) {
                hovered = s;
            }
        }
        if (list.size() > VISIBLE_ROWS) {
            String more = (scroll + 1) + "-" + Math.min(list.size(), scroll + VISIBLE_ROWS) + " of " +
                    list.size() + "  (scroll)";
            g.drawString(font, "§8" + more, panelX + PANEL_W - 6 - font.width(more),
                    panelY + panelH - FOOTER_H - 9, ChroniclesThemePalette.TEXT_DIM, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
