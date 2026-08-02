package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.ChapterConfig;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.model.CategoryDefinition;
import net.phoenixvine.chronicles.registry.CategoryRegistry;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChapterThemeScreen extends Screen {

    private static final int PANEL_W = 268;
    private static final int MARGIN = 12;
    private static final int FIELD_H = 13;
    private static final int STRIDE = FIELD_H + 8;
    private static final int ROW_H = 13;

    private static final int[] ROW_H_TABLE = { STRIDE + 10, STRIDE, STRIDE, STRIDE + 10, STRIDE + 10, STRIDE + 10,
            STRIDE + 10 };
    private static final int ROW_NAME = 0, ROW_ICON = 1, ROW_STYLE = 2, ROW_COLOR = 3, ROW_TEXTURE = 4,
            ROW_CATEGORY = 5, ROW_PARENT = 6;
    private static final int PANEL_H;

    static {
        int h = 28;
        for (int rh : ROW_H_TABLE) h += rh;
        PANEL_H = h + 8 + 22 + 10 + 22 + 18 + 10;
    }

    private static final int ACCENT = 0xFF884499;

    private final Screen parent;
    private final String chapter;

    private ChapterConfig.BgStyle selectedStyle;
    private int cachedColor;
    private String cachedTexture;
    private String cachedDisplayName;
    private String cachedIcon;

    private final String originalDisplayName;

    private EditBox colorBox;
    private EditBox textureBox;
    private EditBox nameBox;

    private boolean styleDropOpen = false;
    private boolean categoryDropOpen = false;
    private boolean parentDropOpen = false;
    private boolean deleteConfirmArmed = false;

    private String cachedCategoryId;

    private String cachedParentChapter;
    private int panelLeft, panelTop;

    private static final ChapterConfig.BgStyle[] STYLES = ChapterConfig.BgStyle.values();

    public ChapterThemeScreen(Screen parent, String chapter) {
        super(Component.literal("Chapter Theme"));
        this.parent = parent;
        this.chapter = chapter;

        ChapterConfig cfg = ChapterConfig.get(chapter);
        this.selectedStyle = cfg.getStyle();
        this.cachedColor = cfg.getColor();
        this.cachedTexture = cfg.getTexture();
        this.cachedDisplayName = cfg.getDisplayName();
        this.originalDisplayName = this.cachedDisplayName;
        this.cachedIcon = cfg.getIcon();
        CategoryDefinition existingCategory = CategoryRegistry.categoryFor(chapter);
        this.cachedCategoryId = existingCategory != null ? existingCategory.id() : null;
        this.cachedParentChapter = cfg.getParentChapter();
    }

    private int rowTop(int index) {
        int y = panelTop + 28;
        for (int i = 0; i < index; i++) y += ROW_H_TABLE[i];
        return y;
    }

    private String categoryLabel(String id) {
        if (id == null) return "(none)";
        for (CategoryDefinition c : CategoryRegistry.getCategories())
            if (c.id().equals(id)) return c.displayName();
        return "(none)";
    }

    private List<String> categoryOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("(none)");
        for (CategoryDefinition c : CategoryRegistry.getCategories()) opts.add(c.displayName());
        opts.add("+ New category…");
        return opts;
    }

    private List<String> parentChapterOptions() {
        List<String> all = (parent instanceof ChronicleOverviewScreen cos) ? cos.buildChapterList() : List.of();
        Set<String> descendants = new HashSet<>();
        collectDescendants(chapter, all, descendants);
        List<String> opts = new ArrayList<>();
        for (String c : all) {
            if (!c.equals(chapter) && !descendants.contains(c)) opts.add(c);
        }
        return opts;
    }

    private String displayNameFor(String cat) {
        return (parent instanceof ChronicleOverviewScreen cos) ? cos.friendly(cat) : cat;
    }

    private List<String> parentDropdownDisplayOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("(none)");
        for (String c : parentChapterOptions()) opts.add(displayNameFor(c));
        return opts;
    }

    private void collectDescendants(String of, List<String> all, Set<String> out) {
        for (String c : all) {
            if (out.contains(c)) continue;
            if (ChapterConfig.get(c).getParentChapter().equals(of)) {
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
                Component.literal("§8Style: §7" + selectedStyle.name() + " §8▾"),
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
        textureBox.setHint(Component.literal("§8modid:texture.png"));
        textureBox.setValue(cachedTexture);
        textureBox.setResponder(v -> {
            cachedTexture = v.trim();
            if (!cachedTexture.isEmpty()) selectedStyle = ChapterConfig.BgStyle.CUSTOM;
        });
        addRenderableWidget(textureBox);
        addRenderableWidget(Button.builder(Component.literal("Browse…"), b -> {
            if (minecraft != null)
                minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                    cachedTexture = rl;
                    selectedStyle = ChapterConfig.BgStyle.CUSTOM;
                    clearWidgets();
                    init();
                }));
        }).bounds(fx + fw - browseW, textureRowY + 11, browseW, FIELD_H).build());

        addRenderableWidget(Button.builder(
                Component.literal("§8Category: §7" + categoryLabel(cachedCategoryId) + " §8▾"),
                b -> categoryDropOpen = !categoryDropOpen).bounds(fx, rowTop(ROW_CATEGORY), fw, FIELD_H).build());

        String parentLabel = cachedParentChapter.isEmpty() ? "(none)" :
                (parent instanceof ChronicleOverviewScreen cos ? cos.friendly(cachedParentChapter) :
                        cachedParentChapter);
        addRenderableWidget(Button.builder(
                Component.literal("§8Parent Chapter: §7" + parentLabel + " §8▾"),
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

        if (parent instanceof ChronicleOverviewScreen cos) {
            int questCount = cos.chapterQuestCount(chapter);
            String label;
            if (questCount == 0) {
                label = "§cDelete Chapter";
            } else if (deleteConfirmArmed) {
                label = "§c§lClick again to delete " + questCount + " quest(s)";
            } else {
                label = "§6Delete Chapter §7(" + questCount + " quests)";
            }
            addRenderableWidget(Button.builder(Component.literal(label),
                    b -> {
                        if (questCount == 0 || deleteConfirmArmed) {
                            cos.deleteChapter(chapter);
                            if (minecraft != null) minecraft.setScreen(parent);
                        } else {
                            deleteConfirmArmed = true;
                            clearWidgets();
                            init();
                        }
                    }).bounds(fx, btnY - 18 - 4, fw, 18).build());
        }
    }

    private int styleDropdownY() {
        return rowTop(ROW_STYLE) + FIELD_H + 1;
    }

    private int categoryDropdownY() {
        return rowTop(ROW_CATEGORY) + FIELD_H + 1;
    }

    private int parentDropdownY() {
        return rowTop(ROW_PARENT) + FIELD_H + 1;
    }

    private String defaultFriendlyName() {
        StringBuilder sb = new StringBuilder();
        for (String w : chapter.toLowerCase().replace("_", " ").split(" "))
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
        ChapterConfig cfg = new ChapterConfig();
        cfg.setStyle(selectedStyle);
        cfg.setColor(cachedColor);
        cfg.setTexture(cachedTexture);
        cfg.setDisplayName(cachedDisplayName);
        cfg.setIcon(cachedIcon);
        cfg.setParentChapter(cachedParentChapter);
        ChapterConfig.put(chapter, cfg);
        ChapterConfig.save();
        ChapterConfig.invalidate();

        CategoryDefinition currentCategory = CategoryRegistry.categoryFor(chapter);
        if (currentCategory != null && !currentCategory.id().equals(cachedCategoryId)) {
            CategoryRegistry.removeChapterFromCategory(currentCategory.id(), chapter);
        }
        if (cachedCategoryId != null) {
            CategoryRegistry.addChapterToCategory(cachedCategoryId, chapter);
        }
        CategoryRegistry.save();

        if (minecraft != null && !cachedDisplayName.equals(originalDisplayName)) {
            String key = "phoenix_chronicles.chapter." + chapter.toLowerCase() + ".name";
            java.nio.file.Path base = minecraft.gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
            String value = cachedDisplayName.isEmpty() ? defaultFriendlyName() : cachedDisplayName;
            net.phoenixvine.chronicles.registry.QuestLangRegistry.writeKey(base, key, value);

            net.phoenixvine.chronicles.client.ClientTextOverrides.put(key, value);
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

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§dTheme — §7" + chapter, ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
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

        if (textureBox.isMouseOver(mx, my)) {
            g.renderComponentTooltip(font, java.util.List.of(Component.literal("§7modid:textures/gui/bg.png"),
                    Component.literal("§8(CUSTOM style only)")), mx, my);
        }

        int previewY = rowTop(ROW_CATEGORY) + STRIDE + 10 + 8;
        int bg = (cachedColor != 0) ? (0xFF000000 | cachedColor) : 0xFF0B0B0F;
        g.fill(fx, previewY, fx + fw, previewY + 22, bg);
        ChroniclesUIKit.drawBorder(g, fx, previewY, fw, 22, 0xFF333344);

        renderStylePreview(g, fx + 1, previewY + 1, fw - 2, 20);

        g.flush();
        super.render(g, mx, my, partial);

        if (styleDropOpen) {
            g.flush();
            int dy = styleDropdownY();
            ChroniclesUIKit.drawDropdown(g, font, java.util.List.of(STYLES), s -> ((ChapterConfig.BgStyle) s).name(),
                    java.util.List.of(STYLES).indexOf(selectedStyle), fx, dy, fw, ROW_H, mx, my);
        }
        if (categoryDropOpen) {
            g.flush();
            List<String> opts = categoryOptions();
            int selIdx = opts.indexOf(categoryLabel(cachedCategoryId));
            ChroniclesUIKit.drawDropdown(g, font, opts, s -> (String) s, selIdx, fx, categoryDropdownY(), fw, ROW_H,
                    mx, my);
        }
        if (parentDropOpen) {
            g.flush();
            List<String> opts = parentDropdownDisplayOptions();
            int selIdx = cachedParentChapter.isEmpty() ? 0 : opts.indexOf(displayNameFor(cachedParentChapter));
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
        if (btn == 0 && categoryDropOpen) {
            int fx = panelLeft + MARGIN;
            int fw = PANEL_W - MARGIN * 2;
            int dy = categoryDropdownY();
            List<String> opts = categoryOptions();
            for (int i = 0; i < opts.size(); i++) {
                int rowY = dy + i * ROW_H;
                if (mx >= fx && mx <= fx + fw && my >= rowY && my <= rowY + ROW_H) {
                    categoryDropOpen = false;
                    if (i == 0) {
                        cachedCategoryId = null;
                        clearWidgets();
                        init();
                    } else if (i == opts.size() - 1) {
                        if (minecraft != null) {
                            minecraft.setScreen(new NewFolderScreen(this, newId -> {
                                cachedCategoryId = newId;
                                clearWidgets();
                                init();
                            }));
                        }
                    } else {
                        cachedCategoryId = CategoryRegistry.getCategories().get(i - 1).id();
                        clearWidgets();
                        init();
                    }
                    return true;
                }
            }
            categoryDropOpen = false;
            return true;
        }
        if (btn == 0 && parentDropOpen) {
            int fx = panelLeft + MARGIN;
            int fw = PANEL_W - MARGIN * 2;
            int dy = parentDropdownY();
            List<String> valid = parentChapterOptions();
            int optCount = valid.size() + 1;
            for (int i = 0; i < optCount; i++) {
                int rowY = dy + i * ROW_H;
                if (mx >= fx && mx <= fx + fw && my >= rowY && my <= rowY + ROW_H) {
                    cachedParentChapter = i == 0 ? "" : valid.get(i - 1);
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
