package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.render.background.BackgroundRenderUtil;
import net.phoenixvine.chronicles.client.render.shader.DynamicShaderManager;
import net.phoenixvine.chronicles.client.util.ChapterConfig;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CanvasThemeScreen extends Screen {

    private static final int PANEL_W = 268;
    private static final int MARGIN = 12;
    private static final int FIELD_H = 13;
    private static final int STRIDE = FIELD_H + 8;
    private static final int ROW_H = 13;
    private static final int SEC_HEADER_H = 15;

    private static final int PREVIEW_GAP = 8;
    private static final int PREVIEW_H = 22;

    private static final int ADV_TOGGLE_GAP = 6;
    private static final int ADV_ROW_GAP = 4;
    private static final int ADV_BLOCK_H = FIELD_H * 4 + 2 * 3 + ADV_ROW_GAP;
    private static final int ADV_BOTTOM_PAD = 6;

    private static final int[] ROW_H_TABLE = { STRIDE, STRIDE + 10, STRIDE + 10, STRIDE + 10, STRIDE + 10 };
    private static final int ROW_STYLE = 0, ROW_COLOR = 1, ROW_OPACITY = 2, ROW_TEXTURE = 3, ROW_SHADER = 4;
    private static final int BASE_ROWS_H;

    static {
        int h = 28;
        for (int rh : ROW_H_TABLE) h += rh;
        BASE_ROWS_H = h + PREVIEW_GAP + PREVIEW_H + ADV_TOGGLE_GAP + SEC_HEADER_H + 10 + 18 + 10;
    }

    private int panelH;

    private static final int ACCENT = 0xFF884499;

    private final Screen parent;
    private final String chapter;

    private ChapterConfig.BgStyle selectedStyle;
    private int cachedColor;
    private int cachedColorAlpha;
    private String cachedTexture;
    private String cachedShaderId;

    private EditBox colorBox;
    private EditBox opacityBox;
    private EditBox textureBox;
    private EditBox shaderBox;

    private boolean styleDropOpen = false;
    private boolean advancedOpen = false;
    private int openOverrideStyleDropdown = -1;

    private final List<ChapterConfig.CanvasOverride> overrides = new ArrayList<>();
    private final List<EditBox> overrideConditionBoxes = new ArrayList<>();
    private final List<EditBox> overrideTextureBoxes = new ArrayList<>();
    private final List<EditBox> overrideShaderBoxes = new ArrayList<>();
    private final List<Integer> overrideStyleButtonY = new ArrayList<>();

    private int panelLeft, panelTop;

    private float uiScale = 1f;
    private int vw, vh;

    private static final ChapterConfig.BgStyle[] STYLES = ChapterConfig.BgStyle.values();

    public CanvasThemeScreen(Screen parent, String chapter) {
        super(Component.literal("Canvas Theme"));
        this.parent = parent;
        this.chapter = chapter;

        ChapterConfig cfg = ChapterConfig.get(chapter);
        this.selectedStyle = cfg.getStyle();
        this.cachedColor = cfg.getColor();
        this.cachedColorAlpha = cfg.getColorAlpha();
        this.cachedTexture = cfg.getTexture();
        this.cachedShaderId = cfg.getShaderId();
        for (ChapterConfig.CanvasOverride ov : cfg.getCanvasOverrides()) overrides.add(ov.copy());
    }

    private int advancedContentH() {
        if (!advancedOpen) return 0;
        return ADV_TOGGLE_GAP + overrides.size() * ADV_BLOCK_H + FIELD_H + ADV_BOTTOM_PAD;
    }

    private int previewY() {
        return rowTop(ROW_SHADER) + ROW_H_TABLE[ROW_SHADER] + PREVIEW_GAP;
    }

    private int advancedToggleY() {
        return previewY() + PREVIEW_H + ADV_TOGGLE_GAP;
    }

    private int rowTop(int index) {
        int y = panelTop + 28;
        for (int i = 0; i < index; i++) y += ROW_H_TABLE[i];
        return y;
    }

    @Override
    protected void init() {
        panelH = BASE_ROWS_H + advancedContentH();

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

        addRenderableWidget(Button.builder(
                Component.literal("§8Style: §7" + selectedStyle.name() + " §8▾"),
                b -> styleDropOpen = !styleDropOpen).bounds(fx, rowTop(ROW_STYLE), fw, FIELD_H).build());

        colorBox = new EditBox(font, fx, rowTop(ROW_COLOR) + 11, fw, FIELD_H, Component.empty());
        colorBox.setMaxLength(7);
        colorBox.setHint(Component.literal("§8#RRGGBB  (empty = default)"));
        colorBox.setValue(cachedColor != 0 ? ChroniclesUIKit.formatHexColor(cachedColor) : "");
        colorBox.setResponder(v -> cachedColor = ChroniclesUIKit.parseHexColor(v, 0));
        addRenderableWidget(colorBox);

        int transparentW = 52;
        int transparentGap = 4;
        int opacityRowY = rowTop(ROW_OPACITY);
        opacityBox = new EditBox(font, fx, opacityRowY + 11, fw - transparentW - transparentGap, FIELD_H,
                Component.empty());
        opacityBox.setMaxLength(3);
        opacityBox.setHint(Component.literal("§80-100"));
        opacityBox.setValue(String.valueOf(Math.round(cachedColorAlpha / 255f * 100)));
        opacityBox.setResponder(v -> {
            try {
                int pct = Mth.clamp(Integer.parseInt(v.trim()), 0, 100);
                cachedColorAlpha = Math.round(pct / 100f * 255);
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(opacityBox);
        addRenderableWidget(Button.builder(Component.literal("§7None"), b -> {
            cachedColorAlpha = 0;
            opacityBox.setValue("0");
        }).bounds(fx + fw - transparentW, opacityRowY + 11, transparentW, FIELD_H).build());

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

        int shaderRowY = rowTop(ROW_SHADER);
        shaderBox = new EditBox(font, fx, shaderRowY + 11, fw - browseW - browseGap, FIELD_H, Component.empty());
        shaderBox.setMaxLength(64);
        shaderBox.setHint(Component.literal("§8shader id  (empty = none)"));
        shaderBox.setValue(cachedShaderId);
        shaderBox.setResponder(v -> {
            cachedShaderId = v.trim();
            if (!cachedShaderId.isEmpty()) selectedStyle = ChapterConfig.BgStyle.SHADER;
        });
        addRenderableWidget(shaderBox);
        addRenderableWidget(Button.builder(Component.literal("Browse…"), b -> {
            if (minecraft != null)
                minecraft.setScreen(new ShaderPickerScreen(this, id -> {
                    cachedShaderId = id;
                    selectedStyle = ChapterConfig.BgStyle.SHADER;
                    clearWidgets();
                    init();
                }));
        }).bounds(fx + fw - browseW, shaderRowY + 11, browseW, FIELD_H).build());

        int advY = advancedToggleY();

        if (advancedOpen) {
            int oy = advY + SEC_HEADER_H + ADV_TOGGLE_GAP;
            int removeW = 14;
            for (int i = 0; i < overrides.size(); i++) {
                ChapterConfig.CanvasOverride ov = overrides.get(i);
                int idx = i;

                EditBox condBox = new EditBox(font, fx, oy, fw - removeW - 4, FIELD_H, Component.empty());
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
                }).bounds(fx + fw - removeW, oy, removeW, FIELD_H).build());

                oy += FIELD_H + 2;

                overrideStyleButtonY.add(oy);
                addRenderableWidget(Button.builder(
                        Component.literal("§8Style: §7" + ov.style.name() + " §8▾"),
                        b -> openOverrideStyleDropdown = (openOverrideStyleDropdown == idx) ? -1 : idx)
                        .bounds(fx, oy, fw, FIELD_H).build());

                oy += FIELD_H + 2;

                EditBox texBox = new EditBox(font, fx, oy, fw, FIELD_H, Component.empty());
                texBox.setMaxLength(256);
                texBox.setHint(Component.literal("§8modid:texture.png  (CUSTOM only)"));
                texBox.setValue(ov.texture);
                texBox.setResponder(v -> overrides.get(idx).texture = v.trim());
                addRenderableWidget(texBox);
                overrideTextureBoxes.add(texBox);

                oy += FIELD_H + 2;

                EditBox shBox = new EditBox(font, fx, oy, fw, FIELD_H, Component.empty());
                shBox.setMaxLength(64);
                shBox.setHint(Component.literal("§8shader id  (SHADER only)"));
                shBox.setValue(ov.shaderId);
                shBox.setResponder(v -> overrides.get(idx).shaderId = v.trim());
                addRenderableWidget(shBox);
                overrideShaderBoxes.add(shBox);

                oy += FIELD_H + ADV_ROW_GAP;
            }

            addRenderableWidget(Button.builder(Component.literal("§7+ Add Override"), b -> {
                overrides.add(new ChapterConfig.CanvasOverride());
                clearWidgets();
                init();
            }).bounds(fx, oy, fw, FIELD_H).build());
        } else {
            openOverrideStyleDropdown = -1;
        }

        int btnY = panelTop + panelH - 10 - 18;
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

    private void save() {
        ChapterConfig cfg = ChapterConfig.get(chapter);
        ChapterConfig fresh = new ChapterConfig();

        fresh.setNameColor(cfg.getNameColor());
        fresh.setSidebarShaderId(cfg.getSidebarShaderId());
        fresh.setSidebarOverrides(cfg.getSidebarOverrides());
        fresh.setDisplayName(cfg.getDisplayName());
        fresh.setIcon(cfg.getIcon());
        fresh.setParentChapter(cfg.getParentChapter());

        fresh.setStyle(selectedStyle);
        fresh.setColor(cachedColor);
        fresh.setColorAlpha(cachedColorAlpha);
        fresh.setTexture(cachedTexture);
        fresh.setShaderId(cachedShaderId);
        fresh.setCanvasOverrides(overrides);

        ChapterConfig.put(chapter, fresh);
        ChapterConfig.save();
        ChapterConfig.invalidate();

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
                "§dCanvas Theme: §7" + chapter, ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        g.drawString(font, "§8Background Color", fx, rowTop(ROW_COLOR), ChroniclesThemePalette.TEXT_FAINT);
        g.drawString(font, "§8Background Opacity", fx, rowTop(ROW_OPACITY), ChroniclesThemePalette.TEXT_FAINT);
        g.drawString(font, "§8Custom Background", fx, rowTop(ROW_TEXTURE), ChroniclesThemePalette.TEXT_FAINT);

        if (textureBox.isMouseOver(mx, my)) {
            g.renderComponentTooltip(font, java.util.List.of(Component.literal("§7modid:textures/gui/bg.png"),
                    Component.literal("§8(CUSTOM style only)")), mx, my);
        }

        g.drawString(font, "§8Canvas Shader", fx, rowTop(ROW_SHADER), ChroniclesThemePalette.TEXT_FAINT);
        ChroniclesUIKit.drawShaderWarning(g, font, shaderBox, DynamicShaderManager.lastCompileFailed(cachedShaderId));
        if (shaderBox.isMouseOver(mx, my)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("§7A .frag file's name (no extension) from"));
            tip.add(Component.literal("§7config/phoenix_chronicles/shaders/"));
            tip.add(Component.literal("§8(SHADER style only)"));
            if (DynamicShaderManager.lastCompileFailed(cachedShaderId)) {
                tip.add(Component.literal("§c⚠ failed to compile -- check the log for the real error"));
            }
            List<String> available = DynamicShaderManager.listAvailable();
            if (!available.isEmpty()) tip.add(Component.literal("§8Available: §7" + String.join(", ", available)));
            g.renderComponentTooltip(font, tip, mx, my);
        }

        int previewY = previewY();
        int bg = (cachedColor != 0) ? ((cachedColorAlpha << 24) | (cachedColor & 0x00FFFFFF)) : 0xFF0B0B0F;
        g.fill(fx, previewY, fx + fw, previewY + PREVIEW_H, 0xFF0B0B0F);
        g.fill(fx, previewY, fx + fw, previewY + PREVIEW_H, bg);
        ChroniclesUIKit.drawBorder(g, fx, previewY, fw, PREVIEW_H, 0xFF333344);
        renderStylePreview(g, fx + 1, previewY + 1, fw - 2, PREVIEW_H - 2);

        int advY = advancedToggleY();
        ChroniclesUIKit.drawSectionHeader(g, font, fx, advY, fw, SEC_HEADER_H, "Advanced", !advancedOpen,
                overrides.isEmpty() ? "" : overrides.size() + " override(s)", mx, my,
                ChroniclesThemePalette.TEXT, ChroniclesThemePalette.TEXT_DIM);

        for (EditBox condBox : overrideConditionBoxes) {
            if (condBox.isMouseOver(mx, my)) {
                g.renderComponentTooltip(font, java.util.List.of(
                        Component.literal("§7Evaluated the same way quest-variant conditions are:"),
                        Component.literal("§7config:<file>#<key>=<value>, kjs:<key>=<value>,"),
                        Component.literal("§7mod:<modid>, rule:<gamerule>=<value> -- \"!\" negates,"),
                        Component.literal("§7\",\" is AND, \"|\" is OR."),
                        Component.literal("§8First override (top to bottom) whose condition is true wins,"),
                        Component.literal("§8with its whole style/texture/shader taking over as one unit;"),
                        Component.literal("§8falls back to the base fields above if none match or this"),
                        Component.literal("§8list is empty.")), mx, my);
                break;
            }
        }
        for (EditBox texBox : overrideTextureBoxes) {
            if (texBox.isMouseOver(mx, my)) {
                g.renderComponentTooltip(font, java.util.List.of(Component.literal("§7modid:textures/gui/bg.png"),
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

        if (styleDropOpen) {
            g.flush();
            int dy = styleDropdownY();
            ChroniclesUIKit.drawDropdown(g, font, java.util.List.of(STYLES), s -> ((ChapterConfig.BgStyle) s).name(),
                    java.util.List.of(STYLES).indexOf(selectedStyle), fx, dy, fw, ROW_H, mx, my);
        }
        if (openOverrideStyleDropdown >= 0 && openOverrideStyleDropdown < overrideStyleButtonY.size()) {
            g.flush();
            int dy = overrideStyleButtonY.get(openOverrideStyleDropdown) + FIELD_H + 1;
            ChapterConfig.BgStyle cur = overrides.get(openOverrideStyleDropdown).style;
            ChroniclesUIKit.drawDropdown(g, font, java.util.List.of(STYLES), s -> ((ChapterConfig.BgStyle) s).name(),
                    java.util.List.of(STYLES).indexOf(cur), fx, dy, fw, ROW_H, mx, my);
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
            case SHADER -> {
                if (cachedShaderId.isBlank()) {
                    g.drawCenteredString(font, "§8shader", x + w / 2, y + h / 2 - 4, 0xFF555566);
                } else {
                    ShaderInstance shader = DynamicShaderManager.get(cachedShaderId);
                    if (shader != null) {
                        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
                        BackgroundRenderUtil.drawDynamicShaderQuad(g, shader, x, y, w, h, t);
                    } else if (DynamicShaderManager.lastCompileFailed(cachedShaderId)) {
                        g.drawCenteredString(font, "§c⚠ compile error", x + w / 2, y + h / 2 - 4, 0xFFFF5555);
                    } else {
                        g.drawCenteredString(font, "§8not found", x + w / 2, y + h / 2 - 4, 0xFF555566);
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double rmx, double rmy, int btn) {
        double mx = rmx / uiScale;
        double my = rmy / uiScale;
        if (btn == 0 && !styleDropOpen && openOverrideStyleDropdown < 0) {
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
