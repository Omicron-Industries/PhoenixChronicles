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
import org.jetbrains.annotations.NotNull;

/**
 * Inline popup for editing the background theme of a chapter category.
 * Opened from the context menu in ChronicleOverviewScreen.
 *
 * Lets the user choose:
 * - Background style (DOT_GRID / GRID_LINES / HEX_GRID / DIAGONAL / SOLID / CUSTOM)
 * - Background color tint (hex RRGGBB, no alpha — we add our own alpha)
 * - Custom texture resource location (only relevant for CUSTOM style)
 */
public class CategoryThemeScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 256;
    private static final int MARGIN = 14;
    private static final int FIELD_H = 16;
    private static final int STRIDE = FIELD_H + 10;
    private static final int ROW_H = 16;

    // Kept local: this is a deliberate per-screen accent (magenta), distinct from
    // ChroniclesThemePalette's global accent. Everything else now reads from the palette.
    private static final int ACCENT = 0xFF884499;

    private final Screen parent;
    private final String category;

    private CategoryConfig.BgStyle selectedStyle;
    private int cachedColor;
    private String cachedTexture;
    private String cachedDisplayName;

    private EditBox colorBox;
    private EditBox textureBox;
    private EditBox nameBox;

    private boolean styleDropOpen = false;
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
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;
        int y = panelTop + 28;

        // Display name — raw (untranslated) override; "" means use the slug-derived title.
        // The value here is exactly what gets written as the English lang default on save.
        nameBox = new EditBox(font, fx, y + 9, fw, FIELD_H, Component.empty());
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("§8" + defaultFriendlyName() + "  (empty = default)"));
        nameBox.setValue(cachedDisplayName);
        nameBox.setResponder(v -> cachedDisplayName = v.trim());
        addRenderableWidget(nameBox);
        y += STRIDE + 10;

        // Style button
        addRenderableWidget(Button.builder(
                Component.literal("§8Style: §7" + selectedStyle.name() + " §8▾"),
                b -> styleDropOpen = !styleDropOpen).bounds(fx, y, fw, FIELD_H).build());
        y += STRIDE;

        // Color
        colorBox = new EditBox(font, fx, y + 9, fw, FIELD_H, Component.empty());
        colorBox.setMaxLength(7);
        colorBox.setHint(Component.literal("§8#RRGGBB  (empty = default)"));
        colorBox.setValue(cachedColor != 0 ? ChroniclesUIKit.formatHexColor(cachedColor) : "");
        colorBox.setResponder(v -> cachedColor = ChroniclesUIKit.parseHexColor(v, 0));
        addRenderableWidget(colorBox);
        y += STRIDE + 10;

        // Texture (CUSTOM style only — always shown for simplicity)
        int browseW = 48;
        int browseGap = 4;
        textureBox = new EditBox(font, fx, y + 9, fw - browseW - browseGap, FIELD_H, Component.empty());
        textureBox.setMaxLength(256);
        textureBox.setHint(Component.literal("§8modid:textures/gui/bg.png  (CUSTOM style only)"));
        textureBox.setValue(cachedTexture);
        textureBox.setResponder(v -> cachedTexture = v.trim());
        addRenderableWidget(textureBox);
        addRenderableWidget(Button.builder(Component.literal("Browse…"), b -> {
            if (minecraft != null)
                minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                    textureBox.setValue(rl);
                    cachedTexture = rl;
                }));
        }).bounds(fx + fw - browseW, y + 9, browseW, FIELD_H).build());
        y += STRIDE + 10;

        // Preview swatch row (static colored row)
        y += 8;

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

    /** Slug-derived title shown as the name field's placeholder hint (e.g. THE_FACTORY -> "The Factory"). */
    private String defaultFriendlyName() {
        StringBuilder sb = new StringBuilder();
        for (String w : category.toLowerCase().replace("_", " ").split(" "))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private void save() {
        CategoryConfig cfg = new CategoryConfig();
        cfg.setStyle(selectedStyle);
        cfg.setColor(cachedColor);
        cfg.setTexture(cachedTexture);
        cfg.setDisplayName(cachedDisplayName);
        CategoryConfig.put(category, cfg);
        CategoryConfig.save();
        CategoryConfig.invalidate();

        // The name just entered is deliberately, explicitly being set - write/overwrite the
        // matching English lang key so it's the real translation-key default from now on,
        // and reload so it and any newly-added translations take effect immediately.
        if (minecraft != null) {
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

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§dTheme — §7" + category, ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;
        int y = panelTop + 28;

        // Labels
        g.drawString(font, "§8Display name", fx, y, ChroniclesThemePalette.TEXT_FAINT);
        y += STRIDE + 10;
        y += STRIDE;
        g.drawString(font, "§8Background color tint", fx, y, ChroniclesThemePalette.TEXT_FAINT);
        y += STRIDE + 10;
        g.drawString(font, "§8Custom texture (resource location)", fx, y, ChroniclesThemePalette.TEXT_FAINT);

        // Preview strip at bottom of the form area
        int previewY = panelTop + 28 + (STRIDE + 10) + STRIDE * 2 + 24 + 8;
        int bg = (cachedColor != 0) ? (0xFF000000 | cachedColor) : 0xFF0B0B0F;
        g.fill(fx, previewY, fx + fw, previewY + 22, bg);
        ChroniclesUIKit.drawBorder(g, fx, previewY, fw, 22, 0xFF333344);
        // Draw mini background preview based on selected style
        renderStylePreview(g, fx + 1, previewY + 1, fw - 2, 20);

        super.render(g, mx, my, partial);

        // Style dropdown (elevated z)
        if (styleDropOpen) {
            int dy = panelTop + 28 + FIELD_H + 1;
            ChroniclesUIKit.drawDropdown(g, font, java.util.List.of(STYLES), s -> ((CategoryConfig.BgStyle) s).name(),
                    java.util.List.of(STYLES).indexOf(selectedStyle), fx, dy, fw, ROW_H, mx, my);
        }
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
            int dy = panelTop + 28 + FIELD_H + 1;
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
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
