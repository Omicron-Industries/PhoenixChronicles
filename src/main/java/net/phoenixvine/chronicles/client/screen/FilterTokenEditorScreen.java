package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.filter.FluidFilters;
import net.phoenixvine.chronicles.filter.IFluidFilter;
import net.phoenixvine.chronicles.filter.IItemFilter;
import net.phoenixvine.chronicles.filter.ItemFilters;
import net.phoenixvine.chronicles.item.FluidFilterTokenItem;
import net.phoenixvine.chronicles.item.ItemFilterTokenItem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FilterTokenEditorScreen extends Screen {

    private enum Mode {
        AND,
        OR
    }

    private static final class Entry {

        final IItemFilter itemFilter;
        final IFluidFilter fluidFilter;

        Entry(IItemFilter f) {
            this.itemFilter = f;
            this.fluidFilter = null;
        }

        Entry(IFluidFilter f) {
            this.itemFilter = null;
            this.fluidFilter = f;
        }

        String describe() {
            return itemFilter != null ? itemFilter.describe() : fluidFilter.describe();
        }
    }

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 260;
    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 24;
    private static final int ROW_H = 18;
    private static final int CONTROL_ROWS = 3;

    private final Screen parent;
    private final ItemStack heldStack;
    private final boolean fluidMode;

    private final List<Entry> entries = new ArrayList<>();
    private Mode mode = Mode.OR;
    private boolean invert = false;

    private int panelLeft, panelTop;
    private int listTop, listBottom;
    private int scrollOffset = 0;
    private int hoveredRow = -1;

    private EditBox tagBox;
    private EditBox modBox;
    private final List<String> loadedModIds = new ArrayList<>();
    private List<String> modSuggestions = new ArrayList<>();
    private boolean modSuggestOpen = false;

    public FilterTokenEditorScreen(@Nullable Screen parent, ItemStack heldStack) {
        super(Component.literal("Configure Filter"));
        this.parent = parent;
        this.heldStack = heldStack;
        this.fluidMode = heldStack.getItem() instanceof FluidFilterTokenItem;
        loadExisting();
        for (var mod : ModList.get().getMods()) loadedModIds.add(mod.getModId());
    }

    private void loadExisting() {
        if (fluidMode) {
            IFluidFilter f = FluidFilterTokenItem.getFilter(heldStack);
            if (f == null) return;
            if (f instanceof FluidFilters.Not not) {
                invert = true;
                f = not.child();
            }
            if (f instanceof FluidFilters.AnyOf any) {
                mode = Mode.OR;
                any.children().forEach(c -> entries.add(new Entry(c)));
            } else if (f instanceof FluidFilters.AllOf all) {
                mode = Mode.AND;
                all.children().forEach(c -> entries.add(new Entry(c)));
            } else {
                entries.add(new Entry(f));
            }
        } else {
            IItemFilter f = ItemFilterTokenItem.getFilter(heldStack);
            if (f == null) return;
            if (f instanceof ItemFilters.Not not) {
                invert = true;
                f = not.child();
            }
            if (f instanceof ItemFilters.AnyOf any) {
                mode = Mode.OR;
                any.children().forEach(c -> entries.add(new Entry(c)));
            } else if (f instanceof ItemFilters.AllOf all) {
                mode = Mode.AND;
                all.children().forEach(c -> entries.add(new Entry(c)));
            } else {
                entries.add(new Entry(f));
            }
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;

        listTop = panelTop + HEADER_H + 4 + ROW_H * CONTROL_ROWS + 10;
        listBottom = panelTop + PANEL_H - FOOTER_H - 14;

        int bx = panelLeft + 4;
        int by = panelTop + HEADER_H + 4;

        if (fluidMode) {
            addRenderableWidget(Button.builder(Component.literal("§3+ Fluid"), b -> {
                if (minecraft != null) minecraft.setScreen(new FluidPickerScreen(this, fluidId -> {
                    ResourceLocation id = ResourceLocation.parse(fluidId);
                    entries.add(new Entry(FluidFilters.exact(id)));
                }));
            }).bounds(bx, by, 60, 14).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("§e+ Item"), b -> {
                if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                    Item item = stack.getItem();
                    entries.add(new Entry(ItemFilters.exact(item)));
                }));
            }).bounds(bx, by, 60, 14).build());
        }

        tagBox = new EditBox(font, bx + 64, by, 110, 14, Component.empty());
        tagBox.setMaxLength(96);
        tagBox.setHint(Component.literal(fluidMode ? "§8tag e.g. forge:milk" : "§8tag e.g. forge:ingots"));
        addRenderableWidget(tagBox);
        addRenderableWidget(Button.builder(Component.literal("§d+ Tag"), b -> addTagEntry())
                .bounds(bx + 178, by, 44, 14)
                .tooltip(Tooltip.create(Component.literal("Add an item/fluid tag as a match rule")))
                .build());

        int by2 = by + 18;
        modBox = new EditBox(font, bx, by2, 110, 14, Component.empty());
        modBox.setMaxLength(64);
        modBox.setHint(Component.literal("§8mod id e.g. create"));
        modBox.setResponder(this::updateModSuggestions);
        addRenderableWidget(modBox);
        addRenderableWidget(Button.builder(Component.literal("§b+ Mod"), b -> addModEntry())
                .bounds(bx + 114, by2, 44, 14)
                .tooltip(Tooltip.create(Component.literal("Match every item/fluid registered by a mod")))
                .build());

        int by3 = by2 + 18;
        addRenderableWidget(Button.builder(
                Component.literal(mode == Mode.AND ? "§6AND" : "§2OR"),
                b -> {
                    mode = mode == Mode.AND ? Mode.OR : Mode.AND;
                    rebuildModeButton();
                }).bounds(bx, by3, 44, 14)
                .tooltip(Tooltip.create(Component.literal(
                        "OR: item matches if it satisfies ANY listed rule\nAND: item must satisfy ALL listed rules")))
                .build());

        addRenderableWidget(Button.builder(
                Component.literal(invert ? "§cNOT: ON" : "§8NOT: off"),
                b -> {
                    invert = !invert;
                    rebuildModeButton();
                }).bounds(bx + 48, by3, 76, 14)
                .tooltip(Tooltip.create(Component.literal("Invert the whole combined filter")))
                .build());

        int footerY = panelTop + PANEL_H - FOOTER_H;
        addRenderableWidget(Button.builder(Component.literal("§a✔ Save"), b -> confirm())
                .bounds(panelLeft + PANEL_W - 60, footerY + 5, 56, 14).build());
        addRenderableWidget(Button.builder(Component.literal("§7Cancel"), b -> {
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(panelLeft + 4, footerY + 5, 56, 14).build());
    }

    private void rebuildModeButton() {
        init();
    }

    private void addTagEntry() {
        String raw = tagBox.getValue().trim();
        if (raw.isEmpty()) return;
        if (!raw.contains(":")) raw = "minecraft:" + raw;
        try {
            ResourceLocation rl = ResourceLocation.parse(raw);
            if (fluidMode) {
                entries.add(new Entry(FluidFilters.tag(rl.toString())));
            } else {
                entries.add(new Entry(ItemFilters.tag(rl.toString())));
            }
            tagBox.setValue("");
        } catch (Exception ignored) {}
    }

    private void addModEntry() {
        String modId = modBox.getValue().trim();
        if (modId.isEmpty()) return;
        if (fluidMode) entries.add(new Entry(FluidFilters.mod(modId)));
        else entries.add(new Entry(ItemFilters.mod(modId)));
        modBox.setValue("");
        modSuggestOpen = false;
    }

    private void updateModSuggestions(String query) {
        String q = query.toLowerCase().trim();
        modSuggestions = new ArrayList<>();
        modSuggestOpen = !q.isEmpty();
        if (q.isEmpty()) return;
        for (String id : loadedModIds) {
            if (id.contains(q)) modSuggestions.add(id);
            if (modSuggestions.size() >= 6) break;
        }
    }

    private void confirm() {
        if (!entries.isEmpty()) {
            CompoundTag filterTag;
            if (fluidMode) {
                List<IFluidFilter> filters = entries.stream().map(e -> e.fluidFilter).toList();
                IFluidFilter combined = filters.size() == 1 ? filters.get(0) :
                        (mode == Mode.AND ? FluidFilters.allOf(filters.toArray(new IFluidFilter[0])) :
                                FluidFilters.anyOf(filters.toArray(new IFluidFilter[0])));
                if (invert) combined = FluidFilters.not(combined);
                FluidFilterTokenItem.setFilter(heldStack, combined);
                filterTag = combined.serialize();
            } else {
                List<IItemFilter> filters = entries.stream().map(e -> e.itemFilter).toList();
                IItemFilter combined = filters.size() == 1 ? filters.get(0) :
                        (mode == Mode.AND ? ItemFilters.allOf(filters.toArray(new IItemFilter[0])) :
                                ItemFilters.anyOf(filters.toArray(new IItemFilter[0])));
                if (invert) combined = ItemFilters.not(combined);
                ItemFilterTokenItem.setFilter(heldStack, combined);
                filterTag = combined.serialize();
            }

            net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                    new net.phoenixvine.chronicles.network.packet.C2SSetFilterTokenPacket(filterTag));
        }
        if (minecraft != null) minecraft.setScreen(parent);
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
        String title = fluidMode ? "§3Configure Fluid Filter" : "§eConfigure Item Filter";
        g.drawCenteredString(font, title, panelLeft + PANEL_W / 2, panelTop + 7, ChroniclesThemePalette.TEXT);

        super.render(g, mx, my, partial);

        g.drawString(font, "§8Rules  (" + entries.size() + ")", panelLeft + 4, listTop - 10,
                ChroniclesThemePalette.TEXT_FAINT, false);

        int rowsTop = listTop;
        int rowsBottom = listBottom;
        g.enableScissor(panelLeft, rowsTop, panelLeft + PANEL_W, rowsBottom);
        hoveredRow = -1;
        int visRows = Math.max(1, (rowsBottom - rowsTop) / ROW_H);
        for (int i = 0; i < visRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= entries.size()) break;
            Entry e = entries.get(idx);
            int ry = rowsTop + i * ROW_H;
            boolean hov = mx >= panelLeft + 2 && mx < panelLeft + PANEL_W - 2 && my >= ry && my < ry + ROW_H;
            if (hov) {
                g.fill(panelLeft + 2, ry, panelLeft + PANEL_W - 2, ry + ROW_H, 0x22FFFFFF);
                hoveredRow = idx;
            }

            ItemStack iconStack = displayStackFor(e);
            if (!iconStack.isEmpty()) g.renderItem(iconStack, panelLeft + 4, ry + 1);

            String label = e.describe();
            int maxW = PANEL_W - 46;
            if (font.width(label) > maxW) label = font.plainSubstrByWidth(label, maxW - 4) + "…";
            g.drawString(font, "§7" + label, panelLeft + 24, ry + 5, ChroniclesThemePalette.TEXT_DIM, false);

            if (hov) g.drawString(font, "§c×", panelLeft + PANEL_W - 14, ry + 5, 0xFFFF5555, false);
        }
        if (entries.isEmpty())
            g.drawString(font, "§8No rules yet. Add an item/tag/mod above.", panelLeft + 4, rowsTop + 4,
                    ChroniclesThemePalette.TEXT_FAINT, false);
        g.disableScissor();

        if (entries.size() > 1) {
            String combineLabel = (mode == Mode.AND ? "§6ALL of the above" : "§2ANY of the above") +
                    (invert ? " §c(inverted)" : "");
            g.drawString(font, combineLabel, panelLeft + 4, panelTop + PANEL_H - FOOTER_H - 10,
                    ChroniclesThemePalette.TEXT_FAINT, false);
        }

        if (modSuggestOpen && !modSuggestions.isEmpty()) {
            int sx = panelLeft + 4;
            int sy = panelTop + HEADER_H + 4 + 18 * 3 + 2;
            int sw = 110;
            int sh = modSuggestions.size() * 11 + 2;
            g.fill(sx, sy, sx + sw, sy + sh, 0xFF14141C);
            ChroniclesUIKit.drawBorder(g, sx, sy, sw, sh, ChroniclesThemePalette.BORDER_LIT);
            for (int i = 0; i < modSuggestions.size(); i++) {
                g.drawString(font, "§7" + modSuggestions.get(i), sx + 2, sy + 1 + i * 11,
                        ChroniclesThemePalette.TEXT_DIM,
                        false);
            }
        }

        g.pose().popPose();
    }

    private ItemStack displayStackFor(Entry e) {
        if (e.itemFilter != null) return e.itemFilter.getDisplayStack();
        if (e.fluidFilter != null) {
            Fluid fluid = e.fluidFilter.getDisplayFluid();
            if (fluid == null) return ItemStack.EMPTY;
            Item bucket = fluid.getBucket();
            return bucket != null && bucket != net.minecraft.world.item.Items.AIR ? new ItemStack(bucket) :
                    ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && modSuggestOpen && !modSuggestions.isEmpty()) {
            int sx = panelLeft + 4;
            int sy = panelTop + HEADER_H + 4 + 18 * 3 + 2;
            int sw = 110;
            for (int i = 0; i < modSuggestions.size(); i++) {
                int ry = sy + 1 + i * 11;
                if (mx >= sx && mx < sx + sw && my >= ry && my < ry + 11) {
                    modBox.setValue(modSuggestions.get(i));
                    modSuggestOpen = false;
                    return true;
                }
            }
        }
        if (btn == 0 && hoveredRow >= 0 && mx >= panelLeft + PANEL_W - 18) {
            entries.remove(hoveredRow);
            hoveredRow = -1;
            return true;
        }
        if (btn == 0 && modSuggestOpen) {
            modSuggestOpen = false;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int visRows = Math.max(1, (listBottom - listTop) / ROW_H);
        int maxScroll = Math.max(0, entries.size() - visRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta), maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        if (key == 257 || key == 335) {
            if (tagBox != null && tagBox.isFocused()) {
                addTagEntry();
                return true;
            }
            if (modBox != null && modBox.isFocused()) {
                addModEntry();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
