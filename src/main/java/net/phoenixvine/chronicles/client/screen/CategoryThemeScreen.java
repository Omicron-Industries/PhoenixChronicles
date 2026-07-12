package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.CategoryConfig;
import net.phoenixvine.chronicles.client.ChroniclesLangPack;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.registry.ChapterFolderRegistry;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inline popup for editing the background theme of a chapter category.
 * Opened from the context menu in ChronicleOverviewScreen.
 *
 * Lets the user choose:
 * - Background style (DOT_GRID / GRID_LINES / HEX_GRID / DIAGONAL / SOLID / CUSTOM)
 * - Background color tint (hex RRGGBB, no alpha — we add our own alpha)
 * - Custom texture resource location (only relevant for CUSTOM style)
 * - Which chapter folder (sidebar grouping) this category belongs to, if any
 */
public class CategoryThemeScreen extends Screen {

    private static final int PANEL_W = 268;
    private static final int MARGIN = 12;
    private static final int FIELD_H = 13;
    private static final int STRIDE = FIELD_H + 8;
    private static final int ROW_H = 13;

    // Row heights in field-form order: Name, Icon, Style, Color, Texture, Folder. A single table
    // like this (and rowTop() below) replaces what used to be independently hand-computed offsets
    // duplicated across init()/render()/mouseClicked() - the Style dropdown's Y coordinate drifted
    // out of sync with its actual button position that way (see styleDropdownY()'s old javadoc),
    // and adding a new row without this would risk the exact same class of bug again.
    private static final int[] ROW_H_TABLE = { STRIDE + 10, STRIDE, STRIDE, STRIDE + 10, STRIDE + 10, STRIDE + 10,
            STRIDE + 10 };
    private static final int ROW_NAME = 0, ROW_ICON = 1, ROW_STYLE = 2, ROW_COLOR = 3, ROW_TEXTURE = 4,
            ROW_FOLDER = 5, ROW_PARENT = 6;
    private static final int PANEL_H;

    static {
        int h = 28;
        for (int rh : ROW_H_TABLE) h += rh;
        PANEL_H = h + 8 + 22 + 10 + 18 + 10; // + preview swatch gap/height + buttons + margins
    }

    // Kept local: this is a deliberate per-screen accent (magenta), distinct from
    // ChroniclesThemePalette's global accent. Everything else now reads from the palette.
    private static final int ACCENT = 0xFF884499;

    private final Screen parent;
    private final String category;

    private CategoryConfig.BgStyle selectedStyle;
    private int cachedColor;
    private String cachedTexture;
    private String cachedDisplayName;
    private String cachedIcon;
    /**
     * Snapshot of the display name as loaded, so save() can skip the lang-key write + full
     * resource-pack reload when the name wasn't actually touched (e.g. only the icon or color
     * changed) - reloadResourcePacks() is a multi-second stall on a heavily modded pack, and
     * running it on every save regardless of what changed was the "changing the icon triggers a
     * 5-second reload" bug.
     */
    private final String originalDisplayName;

    private EditBox colorBox;
    private EditBox textureBox;
    private EditBox nameBox;

    private boolean styleDropOpen = false;
    private boolean folderDropOpen = false;
    private boolean parentDropOpen = false;
    /** null = not in any folder ("(none)"). */
    private String cachedFolderId;
    /** "" = top-level, no parent chapter (true nesting, not folder grouping - see CategoryConfig). */
    private String cachedParentCategory;
    private int panelLeft, panelTop;

    private static final CategoryConfig.BgStyle[] STYLES = CategoryConfig.BgStyle.values();

    public CategoryThemeScreen(Screen parent, String category) {
        super(Component.literal("Chapter Theme"));
        this.parent = parent;
        this.category = category;

        CategoryConfig cfg = CategoryConfig.get(category);
        this.selectedStyle = cfg.getStyle();
        this.cachedColor = cfg.getColor();
        this.cachedTexture = cfg.getTexture();
        this.cachedDisplayName = cfg.getDisplayName(); // raw override, not the resolved/translated name
        this.originalDisplayName = this.cachedDisplayName;
        this.cachedIcon = cfg.getIcon();
        ChapterFolderRegistry.ChapterFolder existingFolder = ChapterFolderRegistry.folderFor(category);
        this.cachedFolderId = existingFolder != null ? existingFolder.id() : null;
        this.cachedParentCategory = cfg.getParentCategory();
    }

    /** Y where row `index`'s content starts (matches render()'s label-draw convention). */
    private int rowTop(int index) {
        int y = panelTop + 28;
        for (int i = 0; i < index; i++) y += ROW_H_TABLE[i];
        return y;
    }

    private String folderLabel(String id) {
        if (id == null) return "(none)";
        for (ChapterFolderRegistry.ChapterFolder f : ChapterFolderRegistry.getFolders())
            if (f.id().equals(id)) return f.label();
        return "(none)";
    }

    /** Dropdown options: "(none)", every existing folder's label, then "+ New folder…" last. */
    private List<String> folderOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("(none)");
        for (ChapterFolderRegistry.ChapterFolder f : ChapterFolderRegistry.getFolders()) opts.add(f.label());
        opts.add("+ New folder…");
        return opts;
    }

    /**
     * Every other category this one could validly nest under, excluding itself and anything
     * that's already a descendant of this category (directly or transitively) - allowing either
     * would create a cycle in the parent chain that categoryBreadcrumb()/CategoryConfig.
     * getEffective() would otherwise have to guard against at read time on every frame instead.
     */
    private List<String> parentCategoryOptions() {
        List<String> all = (parent instanceof ChronicleOverviewScreen cos) ? cos.buildCategoryList() : List.of();
        Set<String> descendants = new HashSet<>();
        collectDescendants(category, all, descendants);
        List<String> opts = new ArrayList<>();
        for (String c : all) {
            if (!c.equals(category) && !descendants.contains(c)) opts.add(c);
        }
        return opts;
    }

    private String displayNameFor(String cat) {
        return (parent instanceof ChronicleOverviewScreen cos) ? cos.friendly(cat) : cat;
    }

    /** "(none)" followed by every valid parent option's display name, in parentCategoryOptions() order. */
    private List<String> parentDropdownDisplayOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("(none)");
        for (String c : parentCategoryOptions()) opts.add(displayNameFor(c));
        return opts;
    }

    private void collectDescendants(String of, List<String> all, Set<String> out) {
        for (String c : all) {
            if (out.contains(c)) continue;
            if (net.phoenixvine.chronicles.client.CategoryConfig.get(c).getParentCategory().equals(of)) {
                out.add(c);
                collectDescendants(c, all, out);
            }
        }
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        // Display name — raw (untranslated) override; "" means use the slug-derived title.
        // The value here is exactly what gets written as the English lang default on save.
        nameBox = new EditBox(font, fx, rowTop(ROW_NAME) + 11, fw, FIELD_H, Component.empty());
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("§8" + defaultFriendlyName() + "  (empty = default)"));
        nameBox.setValue(cachedDisplayName);
        nameBox.setResponder(v -> cachedDisplayName = v.trim());
        addRenderableWidget(nameBox);

        // Icon — the sidebar tab icon (icon-first FTBQ-style tab). Preview swatch is drawn in
        // render() at (fx, y) since Button can't host an item render itself.
        int iconPreviewW = FIELD_H + 4;
        addRenderableWidget(Button.builder(Component.literal("§7Change Icon"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        cachedIcon = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())
                                .toString();
                    }));
                })
                .bounds(fx + iconPreviewW + 4, rowTop(ROW_ICON), fw - iconPreviewW - 4, FIELD_H).build());

        // Style button
        addRenderableWidget(Button.builder(
                Component.literal("§8Style: §7" + selectedStyle.name() + " §8▾"),
                b -> styleDropOpen = !styleDropOpen).bounds(fx, rowTop(ROW_STYLE), fw, FIELD_H).build());

        // Color
        colorBox = new EditBox(font, fx, rowTop(ROW_COLOR) + 11, fw, FIELD_H, Component.empty());
        colorBox.setMaxLength(7);
        colorBox.setHint(Component.literal("§8#RRGGBB  (empty = default)"));
        colorBox.setValue(cachedColor != 0 ? ChroniclesUIKit.formatHexColor(cachedColor) : "");
        colorBox.setResponder(v -> cachedColor = ChroniclesUIKit.parseHexColor(v, 0));
        addRenderableWidget(colorBox);

        // Texture (CUSTOM style only — always shown for simplicity). Picking one here auto-
        // switches the Style dropdown above to CUSTOM: the two used to be fully independent
        // controls, so choosing a texture visibly filled in this field ("it shows selected") but
        // did nothing in the canvas unless you *also* remembered to flip Style to CUSTOM by hand -
        // an easy, invisible miss since nothing here suggested that second step was needed.
        int browseW = 48;
        int browseGap = 4;
        int textureRowY = rowTop(ROW_TEXTURE);
        textureBox = new EditBox(font, fx, textureRowY + 11, fw - browseW - browseGap, FIELD_H, Component.empty());
        textureBox.setMaxLength(256);
        textureBox.setHint(Component.literal("§8modid:textures/gui/bg.png  (CUSTOM style only)"));
        textureBox.setValue(cachedTexture);
        textureBox.setResponder(v -> {
            cachedTexture = v.trim();
            if (!cachedTexture.isEmpty()) selectedStyle = CategoryConfig.BgStyle.CUSTOM;
        });
        addRenderableWidget(textureBox);
        addRenderableWidget(Button.builder(Component.literal("Browse…"), b -> {
            if (minecraft != null)
                minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                    cachedTexture = rl;
                    selectedStyle = CategoryConfig.BgStyle.CUSTOM;
                    clearWidgets();
                    init();
                }));
        }).bounds(fx + fw - browseW, textureRowY + 11, browseW, FIELD_H).build());

        // Folder — which sidebar chapter-group (if any) this category belongs to.
        addRenderableWidget(Button.builder(
                Component.literal("§8Folder: §7" + folderLabel(cachedFolderId) + " §8▾"),
                b -> folderDropOpen = !folderDropOpen).bounds(fx, rowTop(ROW_FOLDER), fw, FIELD_H).build());

        // Parent chapter — true nesting (inherited theme, breadcrumb, gated on parent having a
        // completed quest), distinct from Folder above which is just visual grouping of siblings.
        String parentLabel = cachedParentCategory.isEmpty() ? "(none)" :
                (parent instanceof ChronicleOverviewScreen cos ? cos.friendly(cachedParentCategory) :
                        cachedParentCategory);
        addRenderableWidget(Button.builder(
                Component.literal("§8Parent Chapter: §7" + parentLabel + " §8▾"),
                b -> parentDropOpen = !parentDropOpen).bounds(fx, rowTop(ROW_PARENT), fw, FIELD_H).build());

        // Buttons
        int btnY = panelTop + PANEL_H - 10 - 18;
        int half = (fw - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("§aSave"), b -> save())
                .bounds(fx, btnY, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§7Cancel"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(parent);
                })
                .bounds(fx + half + 6, btnY, half, 18).build());
    }

    /**
     * Y just below a row's bottom edge, where that row's dropdown list should open. This used to
     * be two independently hardcoded formulas (one per dropdown) computed by hand rather than
     * derived from rowTop() - see this class's ROW_H_TABLE comment for how that drifted wrong
     * once before. Single source of truth now so render() and the click handlers can't drift.
     */
    private int styleDropdownY() {
        return rowTop(ROW_STYLE) + FIELD_H + 1;
    }

    private int folderDropdownY() {
        return rowTop(ROW_FOLDER) + FIELD_H + 1;
    }

    private int parentDropdownY() {
        return rowTop(ROW_PARENT) + FIELD_H + 1;
    }

    /** Slug-derived title shown as the name field's placeholder hint (e.g. THE_FACTORY -> "The Factory"). */
    private String defaultFriendlyName() {
        StringBuilder sb = new StringBuilder();
        for (String w : category.toLowerCase().replace("_", " ").split(" "))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    /**
     * Resolves cachedIcon (which may be blank/unsaved-edit) to a previewable Item, same
     * fallback-to-book behavior as CategoryConfig.getIconItem() for a config actually on disk.
     */
    private net.minecraft.world.item.Item resolveIconPreview() {
        if (cachedIcon != null && !cachedIcon.isEmpty()) {
            try {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new net.minecraft.resources.ResourceLocation(cachedIcon));
                if (item != null && item != net.minecraft.world.item.Items.AIR) return item;
            } catch (Exception ignored) {}
        }
        return net.minecraft.world.item.Items.BOOK;
    }

    private void save() {
        CategoryConfig cfg = new CategoryConfig();
        cfg.setStyle(selectedStyle);
        cfg.setColor(cachedColor);
        cfg.setTexture(cachedTexture);
        cfg.setDisplayName(cachedDisplayName);
        cfg.setIcon(cachedIcon);
        cfg.setParentCategory(cachedParentCategory);
        CategoryConfig.put(category, cfg);
        CategoryConfig.save();
        CategoryConfig.invalidate();

        // Folder membership — remove from whichever folder currently claims this category (if
        // any), then add to the newly selected one (if any). Cheap no-ops either way since
        // removeCategoryFromFolder/addCategoryToFolder are both idempotent no-ops when the
        // category isn't/is already where it should be.
        ChapterFolderRegistry.ChapterFolder currentFolder = ChapterFolderRegistry.folderFor(category);
        if (currentFolder != null && !currentFolder.id().equals(cachedFolderId)) {
            ChapterFolderRegistry.removeCategoryFromFolder(currentFolder.id(), category);
        }
        if (cachedFolderId != null) {
            ChapterFolderRegistry.addCategoryToFolder(cachedFolderId, category);
        }
        ChapterFolderRegistry.save();

        // The name just entered is deliberately, explicitly being set - write/overwrite the
        // matching English lang key so it's the real translation-key default from now on,
        // and reload so it and any newly-added translations take effect immediately. Only when
        // the name actually changed, though - reloadResourcePacks() is a genuine multi-second
        // stall on a heavily modded pack, and this screen also handles icon/color/style/texture,
        // none of which need a lang reload at all.
        if (minecraft != null && !cachedDisplayName.equals(originalDisplayName)) {
            String key = "phoenix_chronicles.category." + category.toLowerCase() + ".name";
            java.nio.file.Path base = minecraft.gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
            String value = cachedDisplayName.isEmpty() ? defaultFriendlyName() : cachedDisplayName;
            net.phoenixvine.chronicles.registry.QuestLangRegistry.writeKey(base, key, value);
            ChroniclesLangPack.reload();
        }

        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) { /* parent renders behind us */ }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        if (parent != null) parent.render(g, -1, -1, partial);

        // Force everything the parent just queued through GuiGraphics's deferred bufferSource()
        // to actually submit before we draw anything of our own - this pack's rendering pipeline
        // can otherwise leave the parent's fills/text still pending when our own content gets
        // queued, and the parent bleeds through what should be an opaque panel over it (same fix
        // ToastDesignerScreen needed - see its render() for the fuller writeup).
        g.flush();

        // Quest node icons in the parent screen render via g.renderItem() at z=100, which
        // persists in the depth buffer - this panel's own flat z=0 fills/text could still lose
        // the depth test against that and show the parent bleeding through underneath, which is
        // exactly what was happening. Push above it for this whole panel, buttons included.
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§dTheme — §7" + category, ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        // Labels
        g.drawString(font, "§8Display Name", fx, rowTop(ROW_NAME), ChroniclesThemePalette.TEXT_FAINT);

        // Icon preview swatch, next to the "Change Icon" button added in init()
        int iconRowY = rowTop(ROW_ICON);
        net.minecraft.world.item.Item iconItem = resolveIconPreview();
        g.fill(fx, iconRowY, fx + FIELD_H + 4, iconRowY + FIELD_H + 4, 0xFF0B0B0F);
        ChroniclesUIKit.drawBorder(g, fx, iconRowY, FIELD_H + 4, FIELD_H + 4, 0xFF333344);
        // Swatch box is FIELD_H+4=17px, item icons always render at a fixed 16x16 - a +2 inset
        // left the icon overflowing the box's own border by a pixel; +1 centers it (17-16=1).
        g.renderItem(new net.minecraft.world.item.ItemStack(iconItem), fx + 1, iconRowY + 1);

        g.drawString(font, "§8Background Color", fx, rowTop(ROW_COLOR), ChroniclesThemePalette.TEXT_FAINT);
        g.drawString(font, "§8Custom Background", fx, rowTop(ROW_TEXTURE),
                ChroniclesThemePalette.TEXT_FAINT);

        // Preview strip below the last form row
        int previewY = rowTop(ROW_FOLDER) + STRIDE + 10 + 8;
        int bg = (cachedColor != 0) ? (0xFF000000 | cachedColor) : 0xFF0B0B0F;
        g.fill(fx, previewY, fx + fw, previewY + 22, bg);
        ChroniclesUIKit.drawBorder(g, fx, previewY, fw, 22, 0xFF333344);
        // Draw mini background preview based on selected style
        renderStylePreview(g, fx + 1, previewY + 1, fw - 2, 20);

        // The Style/Save/Cancel/"Change icon…" buttons below render through vanilla Button code
        // inside super.render() - a distinct draw pathway from the manual g.fill()/drawString()
        // calls above it, so it gets its own flush boundary too (see the flush at the top of this
        // method for the fuller writeup on why this pack's pipeline needs these).
        g.flush();
        super.render(g, mx, my, partial);

        // Style dropdown (elevated z) - flushed separately from the panel content above it for
        // the same deferred-batching reason as the top-of-render() flush: this is effectively its
        // own overlay layer, drawn after a batch of widget rendering from super.render(), and
        // without a flush boundary here it could end up submitted in the wrong order relative to
        // that panel content and show it bleeding through the dropdown's own background fill.
        if (styleDropOpen) {
            g.flush();
            int dy = styleDropdownY();
            ChroniclesUIKit.drawDropdown(g, font, java.util.List.of(STYLES), s -> ((CategoryConfig.BgStyle) s).name(),
                    java.util.List.of(STYLES).indexOf(selectedStyle), fx, dy, fw, ROW_H, mx, my);
        }
        if (folderDropOpen) {
            g.flush();
            List<String> opts = folderOptions();
            int selIdx = opts.indexOf(folderLabel(cachedFolderId));
            ChroniclesUIKit.drawDropdown(g, font, opts, s -> (String) s, selIdx, fx, folderDropdownY(), fw, ROW_H,
                    mx, my);
        }
        if (parentDropOpen) {
            g.flush();
            List<String> opts = parentDropdownDisplayOptions();
            int selIdx = cachedParentCategory.isEmpty() ? 0 : opts.indexOf(displayNameFor(cachedParentCategory));
            ChroniclesUIKit.drawDropdown(g, font, opts, s -> (String) s, Math.max(0, selIdx), fx, parentDropdownY(),
                    fw, ROW_H, mx, my);
        }

        g.pose().popPose();
    }

    /** Renders a tiny preview of what the selected style looks like in the swatch strip. */
    private void renderStylePreview(GuiGraphics g, int x, int y, int w, int h) {
        switch (selectedStyle) {
            case DOT_GRID -> {
                for (int px = x + 4; px < x + w; px += 8)
                    for (int py = y + 4; py < y + h; py += 8)
                        g.fill(px, py, px + 1, py + 1, 0x33FFFFFF);
            }
            case GRID_LINES -> {
                for (int px = x; px < x + w; px += 10) g.fill(px, y, px + 1, y + h, 0x22FFFFFF);
                for (int py = y; py < y + h; py += 10) g.fill(x, py, x + w, py + 1, 0x22FFFFFF);
            }
            case HEX_GRID -> {
                // Simplified hex preview: staggered dots
                for (int row = 0; row < 3; row++) {
                    int ox = (row % 2 == 0) ? 0 : 6;
                    for (int col = 0; col < 5; col++) {
                        int px = x + 4 + ox + col * 12;
                        int py = y + 3 + row * 8;
                        g.fill(px, py, px + 2, py + 2, 0x44FFFFFF);
                    }
                }
            }
            case DIAGONAL_LINES -> {
                for (int d = 0; d < w + h; d += 8) {
                    int x0 = x + d, y0 = y;
                    int len = Math.min(d, Math.min(w, h));
                    for (int i = 0; i < len; i++) {
                        int px = x0 - i, py = y0 + i;
                        if (px >= x && px < x + w && py >= y && py < y + h)
                            g.fill(px, py, px + 1, py + 1, 0x22FFFFFF);
                    }
                }
            }
            case SOLID -> {} // just the background color, nothing drawn
            case CUSTOM -> g.drawCenteredString(font, "§8custom", x + w / 2, y + h / 2 - 4, 0xFF555566);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && styleDropOpen) {
            int fx = panelLeft + MARGIN;
            int fw = PANEL_W - MARGIN * 2;
            int dy = styleDropdownY();
            for (int i = 0; i < STYLES.length; i++) {
                int rowY = dy + i * ROW_H;
                if (mx >= fx && mx <= fx + fw && my >= rowY && my <= rowY + ROW_H) {
                    selectedStyle = STYLES[i];
                    styleDropOpen = false;
                    clearWidgets();
                    init();
                    return true;
                }
            }
            styleDropOpen = false;
            return true;
        }
        if (btn == 0 && folderDropOpen) {
            int fx = panelLeft + MARGIN;
            int fw = PANEL_W - MARGIN * 2;
            int dy = folderDropdownY();
            List<String> opts = folderOptions();
            for (int i = 0; i < opts.size(); i++) {
                int rowY = dy + i * ROW_H;
                if (mx >= fx && mx <= fx + fw && my >= rowY && my <= rowY + ROW_H) {
                    folderDropOpen = false;
                    if (i == 0) {
                        cachedFolderId = null;
                        clearWidgets();
                        init();
                    } else if (i == opts.size() - 1) {
                        if (minecraft != null) {
                            minecraft.setScreen(new NewFolderScreen(this, newId -> {
                                cachedFolderId = newId;
                                clearWidgets();
                                init();
                            }));
                        }
                    } else {
                        cachedFolderId = ChapterFolderRegistry.getFolders().get(i - 1).id();
                        clearWidgets();
                        init();
                    }
                    return true;
                }
            }
            folderDropOpen = false;
            return true;
        }
        if (btn == 0 && parentDropOpen) {
            int fx = panelLeft + MARGIN;
            int fw = PANEL_W - MARGIN * 2;
            int dy = parentDropdownY();
            List<String> valid = parentCategoryOptions(); // index i-1 in the dropdown (0 is "(none)")
            int optCount = valid.size() + 1;
            for (int i = 0; i < optCount; i++) {
                int rowY = dy + i * ROW_H;
                if (mx >= fx && mx <= fx + fw && my >= rowY && my <= rowY + ROW_H) {
                    cachedParentCategory = i == 0 ? "" : valid.get(i - 1);
                    parentDropOpen = false;
                    clearWidgets();
                    init();
                    return true;
                }
            }
            parentDropOpen = false;
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
        if (key == 256) { // ESC
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
