package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.phoenixvine.chronicles.client.event.ClientTextOverrides;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.render.background.BackgroundRenderUtil;
import net.phoenixvine.chronicles.client.render.shader.DynamicShaderManager;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
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
    private static final int SEC_HEADER_H = 15;

    private static final int PREVIEW_GAP = 10;
    private static final int PREVIEW_H = 24;

    private static final int ADV_TOGGLE_GAP = 6;
    private static final int ADV_ROW_GAP = 4;
    private static final int ADV_BLOCK_H = FIELD_H * 4 + 2 * 3 + ADV_ROW_GAP;
    private static final int ADV_BOTTOM_PAD = 6;

    private static final int[] ROW_H_TABLE = { STRIDE + 10, STRIDE, STRIDE + 10, STRIDE + 10, STRIDE + 10,
            STRIDE + 10 };
    private static final int ROW_NAME = 0, ROW_ICON = 1, ROW_NAME_COLOR = 2, ROW_CATEGORY = 3, ROW_PARENT = 4,
            ROW_SIDEBAR_SHADER = 5;

    private int panelH;

    private static final int ACCENT = 0xFF884499;

    private final Screen parent;
    private final String chapter;

    private int cachedNameColor;
    private String cachedSidebarShaderId;
    private String cachedDisplayName;
    private String cachedIcon;

    private final String originalDisplayName;

    private EditBox nameColorBox;
    private EditBox sidebarShaderBox;
    private EditBox nameBox;

    private boolean categoryDropOpen = false;
    private boolean parentDropOpen = false;
    private boolean deleteConfirmArmed = false;
    private boolean advancedOpen = false;
    private int openOverrideStyleDropdown = -1;

    private final List<ChapterConfig.CanvasOverride> overrides = new ArrayList<>();
    private final List<EditBox> overrideConditionBoxes = new ArrayList<>();
    private final List<EditBox> overrideTextureBoxes = new ArrayList<>();
    private final List<EditBox> overrideShaderBoxes = new ArrayList<>();
    private final List<Integer> overrideStyleButtonY = new ArrayList<>();

    private String cachedCategoryId;
    private String cachedParentChapter;

    private int panelLeft, panelTop;

    private float uiScale = 1f;
    private int vw, vh;

    private static final ChapterConfig.BgStyle[] STYLES = ChapterConfig.BgStyle.values();

    public ChapterThemeScreen(Screen parent, String chapter) {
        super(Component.literal("Chapter Settings"));
        this.parent = parent;
        this.chapter = chapter;

        ChapterConfig cfg = ChapterConfig.get(chapter);
        this.cachedNameColor = cfg.getNameColor();
        this.cachedSidebarShaderId = cfg.getSidebarShaderId();
        this.cachedDisplayName = cfg.getDisplayName();
        this.originalDisplayName = this.cachedDisplayName;
        this.cachedIcon = cfg.getIcon();
        CategoryDefinition existingCategory = CategoryRegistry.categoryFor(chapter);
        this.cachedCategoryId = existingCategory != null ? existingCategory.id() : null;
        this.cachedParentChapter = cfg.getParentChapter();
        for (ChapterConfig.CanvasOverride ov : cfg.getCanvasOverrides()) overrides.add(ov.copy());
    }

    private int calculateContentHeight() {
        int h = 28;
        for (int rh : ROW_H_TABLE) h += rh;
        h += PREVIEW_GAP + PREVIEW_H;
        h += ADV_TOGGLE_GAP + SEC_HEADER_H;
        if (advancedOpen) {
            h += ADV_TOGGLE_GAP + overrides.size() * ADV_BLOCK_H + FIELD_H + ADV_BOTTOM_PAD;
        }
        h += 8;
        if (parent instanceof ChronicleOverviewScreen) {
            h += 18 + 6;
        }
        h += 18 + 12;
        return h;
    }

    private int previewY() {
        return rowTop(ROW_SIDEBAR_SHADER) + ROW_H_TABLE[ROW_SIDEBAR_SHADER] + PREVIEW_GAP;
    }

    private int advancedToggleY() {
        return previewY() + PREVIEW_H + ADV_TOGGLE_GAP;
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
        panelH = calculateContentHeight();

        uiScale = Math.min(1f, Math.min((width - 8f) / PANEL_W, (height - 8f) / panelH));
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);

        panelLeft = Mth.clamp((vw - PANEL_W) / 2, 4, Math.max(4, vw - PANEL_W - 4));
        panelTop = Mth.clamp((vh - panelH) / 2, 4, Math.max(4, vh - panelH - 4));

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        overrideConditionBoxes.clear();
        overrideTextureBoxes.clear();
        overrideShaderBoxes.clear();
        overrideStyleButtonY.clear();

        nameBox = new EditBox(font, fx, rowTop(ROW_NAME) + 11, fw, FIELD_H, Component.empty());
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("§8" + defaultFriendlyName()));
        nameBox.setValue(cachedDisplayName);
        nameBox.setResponder(v -> cachedDisplayName = v.replace('&', '§').trim());
        nameBox.setTooltip(Tooltip.create(Component.literal(
                "Leave empty to use the default name shown above (\"" + defaultFriendlyName() + "\").\n" +
                        "Use & for color/formatting codes, e.g. &6&lGolden Chapter.")));
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

        nameColorBox = new EditBox(font, fx, rowTop(ROW_NAME_COLOR) + 11, fw, FIELD_H, Component.empty());
        nameColorBox.setMaxLength(7);
        nameColorBox.setHint(Component.literal("§8#RRGGBB  (empty = use canvas accent color)"));
        nameColorBox.setValue(cachedNameColor != 0 ? ChroniclesUIKit.formatHexColor(cachedNameColor) : "");
        nameColorBox.setResponder(v -> cachedNameColor = ChroniclesUIKit.parseHexColor(v, 0));
        addRenderableWidget(nameColorBox);

        addRenderableWidget(Button.builder(
                Component.literal("§8Category: §7" + categoryLabel(cachedCategoryId) + " §8▾"),
                b -> categoryDropOpen = !categoryDropOpen).bounds(fx, rowTop(ROW_CATEGORY), fw, FIELD_H).build());

        String parentLabel = cachedParentChapter.isEmpty() ? "(none)" :
                (parent instanceof ChronicleOverviewScreen cos ? cos.friendly(cachedParentChapter) :
                        cachedParentChapter);
        addRenderableWidget(Button.builder(
                Component.literal("§8Parent Chapter: §7" + parentLabel + " §8▾"),
                b -> parentDropOpen = !parentDropOpen).bounds(fx, rowTop(ROW_PARENT), fw, FIELD_H).build());

        int browseW = 48;
        int browseGap = 4;
        int sidebarShaderRowY = rowTop(ROW_SIDEBAR_SHADER);
        sidebarShaderBox = new EditBox(font, fx, sidebarShaderRowY + 11, fw - browseW - browseGap, FIELD_H,
                Component.empty());
        sidebarShaderBox.setMaxLength(64);
        sidebarShaderBox.setHint(Component.literal("§8shader id  (empty = none)"));
        sidebarShaderBox.setValue(cachedSidebarShaderId);
        sidebarShaderBox.setResponder(v -> cachedSidebarShaderId = v.trim());
        addRenderableWidget(sidebarShaderBox);
        addRenderableWidget(Button.builder(Component.literal("Browse…"), b -> {
            if (minecraft != null)
                minecraft.setScreen(new ShaderPickerScreen(this, id -> {
                    cachedSidebarShaderId = id;
                    clearWidgets();
                    init();
                }));
        }).bounds(fx + fw - browseW, sidebarShaderRowY + 11, browseW, FIELD_H).build());

        int currentY = advancedToggleY() + SEC_HEADER_H;

        if (advancedOpen) {
            currentY += ADV_TOGGLE_GAP;
            int removeW = 14;
            for (int i = 0; i < overrides.size(); i++) {
                ChapterConfig.CanvasOverride ov = overrides.get(i);
                int idx = i;

                EditBox condBox = new EditBox(font, fx, currentY, fw - removeW - 4, FIELD_H, Component.empty());
                condBox.setMaxLength(128);
                condBox.setHint(Component.literal("§8condition: e.g. config:pack_mode=expert"));
                condBox.setValue(ov.condition);
                condBox.setResponder(v -> overrides.get(idx).condition = v.trim());
                addRenderableWidget(condBox);
                overrideConditionBoxes.add(condBox);

                addRenderableWidget(Button.builder(Component.literal("§c×"), b -> {
                    overrides.remove(idx);
                    if (openOverrideStyleDropdown == idx) openOverrideStyleDropdown = -1;
                    clearWidgets();
                    init();
                }).bounds(fx + fw - removeW, currentY, removeW, FIELD_H).build());

                currentY += FIELD_H + 2;

                overrideStyleButtonY.add(currentY);
                addRenderableWidget(Button.builder(
                                Component.literal("§8Style: §7" + ov.style.name() + " §8▾"),
                                b -> openOverrideStyleDropdown = (openOverrideStyleDropdown == idx) ? -1 : idx)
                        .bounds(fx, currentY, fw, FIELD_H).build());

                currentY += FIELD_H + 2;

                EditBox texBox = new EditBox(font, fx, currentY, fw, FIELD_H, Component.empty());
                texBox.setMaxLength(256);
                texBox.setHint(Component.literal("§8modid:texture.png  (CUSTOM only)"));
                texBox.setValue(ov.texture);
                texBox.setResponder(v -> overrides.get(idx).texture = v.trim());
                addRenderableWidget(texBox);
                overrideTextureBoxes.add(texBox);

                currentY += FIELD_H + 2;

                EditBox shBox = new EditBox(font, fx, currentY, fw, FIELD_H, Component.empty());
                shBox.setMaxLength(64);
                shBox.setHint(Component.literal("§8shader id  (SHADER only)"));
                shBox.setValue(ov.shaderId);
                shBox.setResponder(v -> overrides.get(idx).shaderId = v.trim());
                addRenderableWidget(shBox);
                overrideShaderBoxes.add(shBox);

                currentY += FIELD_H + ADV_ROW_GAP;
            }

            addRenderableWidget(Button.builder(Component.literal("§7+ Add Background Override"), b -> {
                overrides.add(new ChapterConfig.CanvasOverride());
                clearWidgets();
                init();
            }).bounds(fx, currentY, fw, FIELD_H).build());
            currentY += FIELD_H;
        } else {
            openOverrideStyleDropdown = -1;
        }

        currentY += 8;

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
                    }).bounds(fx, currentY, fw, 18).build());
            currentY += 18 + 6;
        }

        int half = (fw - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("§aSave"), b -> save())
                .bounds(fx, currentY, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§7Cancel"),
                        b -> {
                            if (minecraft != null) minecraft.setScreen(parent);
                        })
                .bounds(fx + half + 6, currentY, half, 18).build());
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
                        .getValue(net.minecraft.resources.ResourceLocation.parse(cachedIcon));
                if (item != null && item != net.minecraft.world.item.Items.AIR) return item;
            } catch (Exception ignored) {}
        }
        return net.minecraft.world.item.Items.BOOK;
    }

    private void save() {
        ChapterConfig existing = ChapterConfig.get(chapter);
        ChapterConfig cfg = new ChapterConfig();

        cfg.setStyle(existing.getStyle());
        cfg.setColor(existing.getColor());
        cfg.setColorAlpha(existing.getColorAlpha());
        cfg.setTexture(existing.getTexture());
        cfg.setShaderId(existing.getShaderId());
        cfg.setCanvasOverrides(overrides);

        cfg.setNameColor(cachedNameColor);
        cfg.setSidebarShaderId(cachedSidebarShaderId);
        cfg.setSidebarOverrides(existing.getSidebarOverrides());
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

            ClientTextOverrides.put(key, value);
        }

        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int rmx, int rmy, float partial) {
        g.flush();

        int mx = Math.round(rmx / uiScale);
        int my = Math.round(rmy / uiScale);

        ChroniclesUIKit.drawScrim(g, this.width, this.height);

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.pose().scale(uiScale, uiScale, 1f);

        ChroniclesUIKit.drawModalChrome(g, font, vw, vh, panelLeft, panelTop, PANEL_W, panelH, 22,
                "§dChapter Settings: §7" + chapter, ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        g.drawString(font, "§8Display Name", fx, rowTop(ROW_NAME), ChroniclesThemePalette.TEXT_FAINT);

        int iconRowY = rowTop(ROW_ICON);
        net.minecraft.world.item.Item iconItem = resolveIconPreview();
        g.fill(fx, iconRowY, fx + FIELD_H + 4, iconRowY + FIELD_H + 4, 0xFF0B0B0F);
        ChroniclesUIKit.drawBorder(g, fx, iconRowY, FIELD_H + 4, FIELD_H + 4, 0xFF333344);

        g.renderItem(new net.minecraft.world.item.ItemStack(iconItem), fx + 1, iconRowY + 1);

        g.drawString(font, "§8Display Name Color", fx, rowTop(ROW_NAME_COLOR), ChroniclesThemePalette.TEXT_FAINT);

        g.drawString(font, "§8Sidebar Row Shader", fx, rowTop(ROW_SIDEBAR_SHADER), ChroniclesThemePalette.TEXT_FAINT);
        ChroniclesUIKit.drawShaderWarning(g, font, sidebarShaderBox,
                DynamicShaderManager.lastCompileFailed(cachedSidebarShaderId));
        if (sidebarShaderBox.isMouseOver(mx, my)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("§7A .frag file's name (no extension) from"));
            tip.add(Component.literal("§7config/phoenix_chronicles/shaders/"));
            tip.add(Component.literal("§8(Independent of the canvas's own shader -- see the canvas's"));
            tip.add(Component.literal("§8 own right-click \"Edit chapter theme…\" for that.)"));
            if (DynamicShaderManager.lastCompileFailed(cachedSidebarShaderId)) {
                tip.add(Component.literal("§c⚠ failed to compile -- check the log for the real error"));
            }
            List<String> available = DynamicShaderManager.listAvailable();
            if (!available.isEmpty()) {
                tip.add(Component.literal("§8Available: §7" + String.join(", ", available)));
            }
            g.renderComponentTooltip(font, tip, mx, my);
        }

        int previewY = previewY();
        ChroniclesUIKit.drawBorder(g, fx, previewY, fw, PREVIEW_H, 0xFF333344);
        if (!cachedSidebarShaderId.isBlank()) {
            ShaderInstance shader = DynamicShaderManager.get(cachedSidebarShaderId);
            if (shader != null) {
                float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
                BackgroundRenderUtil.drawDynamicShaderQuad(g, shader, fx, previewY, fw, PREVIEW_H, t);
            } else if (DynamicShaderManager.lastCompileFailed(cachedSidebarShaderId)) {
                g.fill(fx, previewY, fx + fw, previewY + PREVIEW_H, 0xFF0B0B0F);
                g.drawCenteredString(font, "§c⚠ compile error", fx + fw / 2, previewY + (PREVIEW_H - 8) / 2, 0xFFFF5555);
            } else {
                g.fill(fx, previewY, fx + fw, previewY + PREVIEW_H, 0xFF0B0B0F);
                g.drawCenteredString(font, "§8not found", fx + fw / 2, previewY + (PREVIEW_H - 8) / 2, 0xFF555566);
            }
        } else {
            int bg = (cachedNameColor != 0) ? (0xFF000000 | cachedNameColor) : 0xFF0B0B0F;
            g.fill(fx, previewY, fx + fw, previewY + PREVIEW_H, bg);
            g.drawCenteredString(font, "§7preview", fx + fw / 2, previewY + (PREVIEW_H - 8) / 2, 0xFFAAAAAA);
        }

        int advY = advancedToggleY();
        ChroniclesUIKit.drawSectionHeader(g, font, fx, advY, fw, SEC_HEADER_H, "Advanced", !advancedOpen,
                overrides.isEmpty() ? "" : overrides.size() + " override(s)", mx, my,
                ChroniclesThemePalette.TEXT, ChroniclesThemePalette.TEXT_DIM);

        for (EditBox condBox : overrideConditionBoxes) {
            if (condBox.isMouseOver(mx, my)) {
                g.renderComponentTooltip(font, List.of(
                        Component.literal("§7Evaluated the same way quest-variant conditions are:"),
                        Component.literal("§7config:<file>#<key>=<value>, kjs:<key>=<value>,"),
                        Component.literal("§7mod:<modid>, rule:<gamerule>=<value> -- \"!\" negates,"),
                        Component.literal("§7\",\" is AND, \"|\" is OR."),
                        Component.literal("§8First override (top to bottom) whose condition is true wins,"),
                        Component.literal("§8with its whole style/texture/shader taking over as one unit;"),
                        Component.literal("§8falls back to base background settings if none match or this"),
                        Component.literal("§8list is empty.")), mx, my);
                break;
            }
        }
        for (EditBox texBox : overrideTextureBoxes) {
            if (texBox.isMouseOver(mx, my)) {
                g.renderComponentTooltip(font, List.of(Component.literal("§7modid:textures/gui/bg.png"),
                        Component.literal("§8(this override's own Style must be CUSTOM)")), mx, my);
                break;
            }
        }
        for (int i = 0; i < overrideShaderBoxes.size(); i++) {
            EditBox shBox = overrideShaderBoxes.get(i);
            ChroniclesUIKit.drawShaderWarning(g, font, shBox,
                    DynamicShaderManager.lastCompileFailed(overrides.get(i).shaderId));
            if (shBox.isMouseOver(mx, my)) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal("§7A .frag file's name from config/phoenix_chronicles/shaders/"));
                tip.add(Component.literal("§8(this override's own Style must be SHADER)"));
                if (DynamicShaderManager.lastCompileFailed(overrides.get(i).shaderId)) {
                    tip.add(Component.literal("§c⚠ failed to compile -- check the log for the real error"));
                }
                g.renderComponentTooltip(font, tip, mx, my);
                break;
            }
        }

        g.flush();
        super.render(g, mx, my, partial);

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
        if (openOverrideStyleDropdown >= 0 && openOverrideStyleDropdown < overrideStyleButtonY.size()) {
            g.flush();
            int dy = overrideStyleButtonY.get(openOverrideStyleDropdown) + FIELD_H + 1;
            ChapterConfig.BgStyle cur = overrides.get(openOverrideStyleDropdown).style;
            ChroniclesUIKit.drawDropdown(g, font, List.of(STYLES), s -> ((ChapterConfig.BgStyle) s).name(),
                    List.of(STYLES).indexOf(cur), fx, dy, fw, ROW_H, mx, my);
        }

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double rmx, double rmy, int btn) {
        double mx = rmx / uiScale;
        double my = rmy / uiScale;
        if (btn == 0 && !categoryDropOpen && !parentDropOpen && openOverrideStyleDropdown < 0) {
            int fx = panelLeft + MARGIN;
            int fw = PANEL_W - MARGIN * 2;
            int advY = advancedToggleY();
            if (mx >= fx && mx < fx + fw && my >= advY && my < advY + SEC_HEADER_H) {
                advancedOpen = !advancedOpen;
                clearWidgets();
                init();
                return true;
            }
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
        if (btn == 0 && openOverrideStyleDropdown >= 0 && openOverrideStyleDropdown < overrideStyleButtonY.size()) {
            int fx = panelLeft + MARGIN;
            int fw = PANEL_W - MARGIN * 2;
            int dy = overrideStyleButtonY.get(openOverrideStyleDropdown) + FIELD_H + 1;
            int rowIdx = openOverrideStyleDropdown;
            for (int i = 0; i < STYLES.length; i++) {
                int rowY = dy + i * ROW_H;
                if (mx >= fx && mx <= fx + fw && my >= rowY && my <= rowY + ROW_H) {
                    overrides.get(rowIdx).style = STYLES[i];
                    openOverrideStyleDropdown = -1;
                    clearWidgets();
                    init();
                    return true;
                }
            }
            openOverrideStyleDropdown = -1;
            return true;
        }
        if (btn == 0 && (mx < panelLeft || mx >= panelLeft + PANEL_W || my < panelTop || my >= panelTop + panelH)) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double rmx, double rmy, int btn, double dragX, double dragY) {
        return super.mouseDragged(rmx / uiScale, rmy / uiScale, btn, dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseReleased(double rmx, double rmy, int btn) {
        return super.mouseReleased(rmx / uiScale, rmy / uiScale, btn);
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