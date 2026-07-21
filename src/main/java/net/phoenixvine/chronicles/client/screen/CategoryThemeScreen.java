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

public class CategoryThemeScreen extends Screen {

    private static final int PANEL_W = 268;
    private static final int MARGIN = 12;
    private static final int FIELD_H = 13;
    private static final int STRIDE = FIELD_H + 8;
    private static final int ROW_H = 13;

    private static final int[] ROW_H_TABLE = { STRIDE + 10, STRIDE, STRIDE, STRIDE + 10, STRIDE + 10, STRIDE + 10,
            STRIDE + 10 };
    private static final int ROW_NAME = 0, ROW_ICON = 1, ROW_STYLE = 2, ROW_COLOR = 3, ROW_TEXTURE = 4,
            ROW_FOLDER = 5, ROW_PARENT = 6;
    private static final int PANEL_H;

    static {
        int h = 28;
        for (int rh : ROW_H_TABLE) h += rh;
        PANEL_H = h + 8 + 22 + 10 + 18 + 10; 
    }

    private static final int ACCENT = 0xFF884499;

    private final Screen parent;
    private final String category;

    private CategoryConfig.BgStyle selectedStyle;
    private int cachedColor;
    private String cachedTexture;
    private String cachedDisplayName;
    private String cachedIcon;
    
    private final String originalDisplayName;

    private EditBox colorBox;
    private EditBox textureBox;
    private EditBox nameBox;

    private boolean styleDropOpen = false;
    private boolean folderDropOpen = false;
    private boolean parentDropOpen = false;
    
    private String cachedFolderId;
    
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
        this.cachedDisplayName = cfg.getDisplayName(); 
        this.originalDisplayName = this.cachedDisplayName;
        this.cachedIcon = cfg.getIcon();
        ChapterFolderRegistry.ChapterFolder existingFolder = ChapterFolderRegistry.folderFor(category);
        this.cachedFolderId = existingFolder != null ? existingFolder.id() : null;
        this.cachedParentCategory = cfg.getParentCategory();
    }

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

    private List<String> folderOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("(none)");
        for (ChapterFolderRegistry.ChapterFolder f : ChapterFolderRegistry.getFolders()) opts.add(f.label());
        opts.add("+ New folderâ€¦");
        return opts;
    }

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

        nameBox = new EditBox(font, fx, rowTop(ROW_NAME) + 11, fw, FIELD_H, Component.empty());
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("§8" + defaultFriendlyName() + "  (empty = default)"));
        nameBox.setValue(cachedDisplayName);
        nameBox.setResponder(v -> cachedDisplayName = v.trim());
        addRenderableWidget(nameBox);

        int iconPreviewW = FIELD_H + 4;
        addRenderableWidget(Button.builder(Component.literal("§7Change Icon"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        cachedIcon = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())
                                .toString();
                    }));
                })
                .bounds(fx + iconPreviewW + 4, rowTop(ROW_ICON), fw - iconPreviewW - 4, FIELD_H).build());

        addRenderableWidget(Button.builder(
                Component.literal("§8Style: §7" + selectedStyle.name() + " §8â–¾"),
                b -> styleDropOpen = !styleDropOpen).bounds(fx, rowTop(ROW_STYLE), fw, FIELD_H).build());

        colorBox = new EditBox(font, fx, rowTop(ROW_COLOR) + 11, fw, FIELD_H, Component.empty());
        colorBox.setMaxLength(7);
        colorBox.setHint(Component.literal("§8#RRGGBB  (empty = default)"));
        colorBox.setValue(cachedColor != 0 ? ChroniclesUIKit.formatHexColor(cachedColor) : "");
        colorBox.setResponder(v -> cachedColor = ChroniclesUIKit.parseHexColor(v, 0));
        addRenderableWidget(colorBox);

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
        addRenderableWidget(Button.builder(Component.literal("Browseâ€¦"), b -> {
            if (minecraft != null)
                minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                    cachedTexture = rl;
                    selectedStyle = CategoryConfig.BgStyle.CUSTOM;
                    clearWidgets();
                    init();
                }));
        }).bounds(fx + fw - browseW, textureRowY + 11, browseW, FIELD_H).build());

        addRenderableWidget(Button.builder(
                Component.literal("§8Folder: §7" + folderLabel(cachedFolderId) + " §8â–¾"),
                b -> folderDropOpen = !folderDropOpen).bounds(fx, rowTop(ROW_FOLDER), fw, FIELD_H).build());

        String parentLabel = cachedParentCategory.isEmpty() ? "(none)" :
                (parent instanceof ChronicleOverviewScreen cos ? cos.friendly(cachedParentCategory) :
                        cachedParentCategory);
        addRenderableWidget(Button.builder(
                Component.literal("§8Parent Chapter: §7" + parentLabel + " §8â–¾"),
                b -> parentDropOpen = !parentDropOpen).bounds(fx, rowTop(ROW_PARENT), fw, FIELD_H).build());

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

    private int styleDropdownY() {
        return rowTop(ROW_STYLE) + FIELD_H + 1;
    }

    private int folderDropdownY() {
        return rowTop(ROW_FOLDER) + FIELD_H + 1;
    }

    private int parentDropdownY() {
        return rowTop(ROW_PARENT) + FIELD_H + 1;
    }

    private String defaultFriendlyName() {
        StringBuilder sb = new StringBuilder();
        for (String w : category.toLowerCase().replace("_", " ").split(" "))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

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

        ChapterFolderRegistry.ChapterFolder currentFolder = ChapterFolderRegistry.folderFor(category);
        if (currentFolder != null && !currentFolder.id().equals(cachedFolderId)) {
            ChapterFolderRegistry.removeCategoryFromFolder(currentFolder.id(), category);
        }
        if (cachedFolderId != null) {
            ChapterFolderRegistry.addCategoryToFolder(cachedFolderId, category);
        }
        ChapterFolderRegistry.save();

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
    public void renderBackground(@NotNull GuiGraphics g) {  }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        if (parent != null) parent.render(g, -1, -1, partial);

        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§dTheme â€” §7" + category, ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        g.drawString(font, "§8Display Name", fx, rowTop(ROW_NAME), ChroniclesThemePalette.TEXT_FAINT);

        int iconRowY = rowTop(ROW_ICON);
        net.minecraft.world.item.Item iconItem = resolveIconPreview();
        g.fill(fx, iconRowY, fx + FIELD_H + 4, iconRowY + FIELD_H + 4, 0xFF0B0B0F);
        ChroniclesUIKit.drawBorder(g, fx, iconRowY, FIELD_H + 4, FIELD_H + 4, 0xFF333344);

        g.renderItem(new net.minecraft.world.item.ItemStack(iconItem), fx + 1, iconRowY + 1);

        g.drawString(font, "§8Background Color", fx, rowTop(ROW_COLOR), ChroniclesThemePalette.TEXT_FAINT);
        g.drawString(font, "§8Custom Background", fx, rowTop(ROW_TEXTURE),
                ChroniclesThemePalette.TEXT_FAINT);

        int previewY = rowTop(ROW_FOLDER) + STRIDE + 10 + 8;
        int bg = (cachedColor != 0) ? (0xFF000000 | cachedColor) : 0xFF0B0B0F;
        g.fill(fx, previewY, fx + fw, previewY + 22, bg);
        ChroniclesUIKit.drawBorder(g, fx, previewY, fw, 22, 0xFF333344);
        
        renderStylePreview(g, fx + 1, previewY + 1, fw - 2, 20);

        g.flush();
        super.render(g, mx, my, partial);

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
            case SOLID -> {} 
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
            List<String> valid = parentCategoryOptions(); 
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
        if (key == 256) { 
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

