package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.registry.QuestToastConfig;
import net.phoenixvine.chronicles.client.registry.QuestToastManager;
import net.phoenixvine.chronicles.client.registry.QuestToastPresetRegistry;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ToastDesignerScreen extends Screen {

    private enum Elem {
        ICON,
        TITLE,
        LABEL
    }

    private enum PanelTab {
        ELEMENT,
        ICONS,
        BACKGROUND,
        PRESETS
    }

    private static final int PANEL_W = 184;
    private static final int MARGIN = 10;
    private static final int FIELD_H = 13;
    private static final int STRIDE = FIELD_H + 7;
    private static final boolean PHANTASIA = PhantasiaCompat.isAvailable();

    private static final int MIN_W = 520;
    private static final int MIN_H = 340;
    private float uiScale = 1f;
    private int vw, vh;

    private final Screen parent;
    private final QuestNode node;
    private final boolean hadExistingConfig;
    private QuestToastConfig cfg;

    private PanelTab activeTab = PanelTab.ELEMENT;
    private Elem selected = Elem.TITLE;
    private Elem dragging = null;
    private boolean draggingBg = false;
    private boolean resizingBg = false;
    private QuestToastManager.ActiveToast previewToast;

    private EditBox colorBox;
    private EditBox scaleBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox bgPadXBox;
    private EditBox bgPadYBox;
    private EditBox phantasiaIdBox;
    private EditBox bgColorBox;
    private EditBox accentColorBox;

    private int tabsY, positionY, scaleY, colorY, boldY, iconSetY, iconStripY, sizeY, phantasiaY, bgColorY,
            accentColorY, presetY;

    private int panelScrollY = 0;
    private int contentTop, contentBottom;

    private QuestToastManager.ToastType previewType = QuestToastManager.ToastType.COMPLETED;
    private Float snapGuideX, snapGuideY;

    private static QuestToastConfig toastClipboard = null;

    private boolean presetDropOpen = false;
    private String feedbackMsg = null;
    private long feedbackUntil = 0;

    private int hoveredIconIndex = -1;

    private int hoveredRemoveIconIndex = -1;

    private int selectedIconIndex = -1;

    private int draggingIconIndex = -1;
    private EditBox iconScaleBox;
    private int iconScaleY;

    public ToastDesignerScreen(Screen parent, QuestNode node) {
        super(Component.literal("Toast Designer"));
        this.parent = parent;
        this.node = node;
        QuestToastConfig existing = QuestToastConfig.getOrNull(node.getId().toString());
        this.hadExistingConfig = existing != null;
        this.cfg = existing != null ? existing.copy() : new QuestToastConfig();
    }

    @Override
    protected void init() {
        uiScale = (width < MIN_W || height < MIN_H) ? Math.min(width / (float) MIN_W, height / (float) MIN_H) : 1f;
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);

        if (previewToast == null) previewToast = QuestToastManager.makePreviewToast(node, previewType);

        if (cfg.bgAutoFit) {
            fitBackgroundToElements();
            cfg.bgAutoFit = false;
        }

        int px = vw - PANEL_W;
        int fx = px + MARGIN;
        int fw = PANEL_W - MARGIN * 2;
        int y = 30;

        addRenderableWidget(Button.builder(Component.literal(previewType ==
                QuestToastManager.ToastType.COMPLETED ? "§6Previewing: Complete" : "§bPreviewing: Unlock"),
                b -> {
                    previewType = previewType == QuestToastManager.ToastType.COMPLETED ?
                            QuestToastManager.ToastType.UNLOCKED : QuestToastManager.ToastType.COMPLETED;
                    previewToast = QuestToastManager.makePreviewToast(node, previewType);
                }).bounds(fx, y, fw, FIELD_H)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("Click to switch which toast text the live preview shows")))
                .build());
        y += STRIDE + 6;

        tabsY = y;
        PanelTab[] tabs = PanelTab.values();
        int tabW = fw / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            PanelTab t = tabs[i];
            int tx = fx + i * tabW + (i > 0 ? 2 : 0);
            int tw = tabW - (i > 0 ? 2 : 0);
            addRenderableWidget(Button.builder(Component.literal(tabLabel(t)), b -> {
                activeTab = t;
                rebuildFields();
            }).bounds(tx, y, tw, FIELD_H).build());
        }
        y += STRIDE + 8;

        int half = (fw - 6) / 2;
        int saveY = vh - (hadExistingConfig ? 60 : 38);
        contentTop = y;
        contentBottom = saveY - 4;

        int contentStartIdx = renderables.size();
        switch (activeTab) {
            case ELEMENT -> initElementTab(fx, fw, contentTop - panelScrollY);
            case ICONS -> initIconsTab(fx, fw, contentTop - panelScrollY);
            case BACKGROUND -> initBackgroundTab(fx, fw, contentTop - panelScrollY);
            case PRESETS -> initPresetsTab(fx, fw, contentTop - panelScrollY);
        }
        int contentEndIdx = renderables.size();

        int naturalBottom = contentTop - panelScrollY;
        for (int i = contentStartIdx; i < contentEndIdx; i++) {
            if (renderables.get(i) instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                naturalBottom = Math.max(naturalBottom, w.getY() + w.getHeight() + panelScrollY);
            }
        }
        int maxScroll = Math.max(0, (naturalBottom - contentTop) - (contentBottom - contentTop));
        panelScrollY = Math.max(0, Math.min(panelScrollY, maxScroll));
        for (int i = contentStartIdx; i < contentEndIdx; i++) {
            if (renderables.get(i) instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                w.visible = w.getY() + w.getHeight() > contentTop && w.getY() < contentBottom;
            }
        }

        addRenderableWidget(Button.builder(Component.literal("§aSave"), b -> save())
                .bounds(fx, saveY, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§7Cancel"), b -> closeDesigner())
                .bounds(fx + half + 6, saveY, half, 18).build());
        if (hadExistingConfig) {
            addRenderableWidget(Button.builder(Component.literal("§cReset to default style"), b -> {
                QuestToastConfig.remove(node.getId().toString());
                closeDesigner();
            }).bounds(fx, saveY + 22, fw, 16).build());
        }
    }

    private String tabLabel(PanelTab t) {
        String name = switch (t) {
            case ELEMENT -> "Elem";
            case ICONS -> "Icons";
            case BACKGROUND -> "BG";
            case PRESETS -> "Preset";
        };
        return (activeTab == t ? "§f" : "§7") + name;
    }

    private void initElementTab(int fx, int fw, int y) {
        addRenderableWidget(Button.builder(Component.literal(elemLabel(Elem.ICON)),
                b -> {
                    selected = Elem.ICON;
                    rebuildFields();
                })
                .bounds(fx, y, fw / 3 - 2, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal(elemLabel(Elem.TITLE)),
                b -> {
                    selected = Elem.TITLE;
                    rebuildFields();
                })
                .bounds(fx + fw / 3, y, fw / 3 - 2, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal(elemLabel(Elem.LABEL)),
                b -> {
                    selected = Elem.LABEL;
                    rebuildFields();
                })
                .bounds(fx + 2 * fw / 3 + 2, y, fw / 3 - 2, FIELD_H).build());
        y += STRIDE + 6;

        positionY = y;
        int posHalfW = (fw - 6) / 2;
        xBox = new EditBox(font, fx, y + 11, posHalfW, FIELD_H, Component.empty());
        xBox.setMaxLength(6);
        xBox.setValue(String.format("%.1f", elemOf(selected).x * 100));
        xBox.setResponder(v -> {
            try {
                elemOf(selected).x = Math.max(0.02f, Math.min(0.98f, Float.parseFloat(v.trim()) / 100f));
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(xBox);
        yBox = new EditBox(font, fx + posHalfW + 6, y + 11, posHalfW, FIELD_H, Component.empty());
        yBox.setMaxLength(6);
        yBox.setValue(String.format("%.1f", elemOf(selected).y * 100));
        yBox.setResponder(v -> {
            try {
                elemOf(selected).y = Math.max(0.05f, Math.min(0.95f, Float.parseFloat(v.trim()) / 100f));
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(yBox);
        y += STRIDE + 10;

        scaleY = y;
        scaleBox = new EditBox(font, fx, y + 11, fw, FIELD_H, Component.empty());
        scaleBox.setMaxLength(6);
        scaleBox.setValue(String.format("%.2f", elemOf(selected).scale));
        scaleBox.setResponder(v -> {
            try {
                elemOf(selected).scale = Math.max(0.2f, Math.min(4f, Float.parseFloat(v)));
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(scaleBox);
        y += STRIDE + 10;

        colorY = y;
        colorBox = new EditBox(font, fx, y + 11, fw, FIELD_H, Component.empty());
        colorBox.setMaxLength(7);
        colorBox.setValue(selected == Elem.ICON ? "" : ChroniclesUIKit.formatHexColor(elemOf(selected).color));
        colorBox.setEditable(selected != Elem.ICON);
        colorBox.setResponder(v -> {
            if (selected == Elem.ICON) return;
            elemOf(selected).color = ChroniclesUIKit.parseHexColor(v, elemOf(selected).color) | 0xFF000000;
        });
        addRenderableWidget(colorBox);
        y += STRIDE + 4;

        boldY = y;
        addRenderableWidget(Button.builder(
                Component.literal(selected != Elem.ICON ? "§7Bold: " + (elemOf(selected).bold ? "§aOn" : "§8Off") :
                        "§8(no bold for icon)"),
                b -> {
                    if (selected == Elem.ICON) return;
                    elemOf(selected).bold = !elemOf(selected).bold;
                    fitBackgroundToElements();
                    rebuildFields();
                }).bounds(fx, y, fw, FIELD_H).build());
        y += STRIDE + 10;

        addRenderableWidget(Button.builder(Component.literal("§7↺ Reset " + elemPlainLabel(selected)), b -> {
            switch (selected) {
                case ICON -> cfg.icon = QuestToastConfig.defaultIcon();
                case TITLE -> cfg.title = QuestToastConfig.defaultTitle();
                case LABEL -> cfg.label = QuestToastConfig.defaultLabel();
            }
            setFeedback(elemPlainLabel(selected) + " reset to default");
            rebuildFields();
        }).bounds(fx, y, fw, FIELD_H)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Restores just the currently-selected element (" + elemPlainLabel(selected) +
                                ") to its default position/scale/color - leaves the other two and the " +
                                "background untouched")))
                .build());
    }

    private void initIconsTab(int fx, int fw, int y) {
        int iconBtnW = (fw - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("§7+ Item"), b -> {
            if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) addIconEntry(QuestGroup.IconKind.ITEM, id.toString());
            }));
        }).bounds(fx, y, iconBtnW, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("§3+ Fluid"), b -> {
            if (minecraft != null) minecraft.setScreen(new FluidPickerScreen(this,
                    fluidId -> addIconEntry(QuestGroup.IconKind.FLUID, fluidId)));
        }).bounds(fx + iconBtnW + 4, y, iconBtnW, FIELD_H).build());

        addRenderableWidget(Button.builder(Component.literal("§d+ Tex"), b -> {
            if (minecraft != null) minecraft.setScreen(new TextureBrowserScreen(this,
                    texId -> addIconEntry(QuestGroup.IconKind.TEXTURE, texId)));
        }).bounds(fx + (iconBtnW + 4) * 2, y, iconBtnW, FIELD_H).build());
        y += FIELD_H + 8;
        iconSetY = y;
        y += 10;
        iconStripY = y;
        y += 20;

        if (selectedIconIndex >= 0 && selectedIconIndex < cfg.icons.size()) {
            QuestToastConfig.IconEntry entry = cfg.icons.get(selectedIconIndex);
            iconScaleY = y;
            iconScaleBox = new EditBox(font, fx, y + 11, fw, FIELD_H, Component.empty());
            iconScaleBox.setMaxLength(6);
            iconScaleBox.setValue(String.format("%.2f", entry.scale));
            iconScaleBox.setResponder(v -> {
                try {
                    entry.scale = Math.max(0.2f, Math.min(4f, Float.parseFloat(v)));
                } catch (NumberFormatException ignored) {}
            });
            addRenderableWidget(iconScaleBox);
            y += STRIDE + 8;
            addRenderableWidget(Button.builder(Component.literal("§c✕ Remove selected icon"), b -> {
                cfg.icons.remove(selectedIconIndex);
                selectedIconIndex = -1;
                rebuildFields();
            }).bounds(fx, y, fw, FIELD_H).build());
        }
    }

    private void addIconEntry(QuestGroup.IconKind kind, String id) {
        cfg.icons.add(new QuestToastConfig.IconEntry(kind, id, cfg.icon.x, cfg.icon.y, cfg.icon.scale));
        selectedIconIndex = cfg.icons.size() - 1;
        rebuildFields();
    }

    private void initBackgroundTab(int fx, int fw, int y) {
        sizeY = y;
        int halfW = (fw - 6) / 2;
        bgPadXBox = new EditBox(font, fx, y + 11, halfW, FIELD_H, Component.empty());
        bgPadXBox.setMaxLength(5);
        bgPadXBox.setValue(String.valueOf(Math.round(cfg.bgPadX)));
        bgPadXBox.setResponder(v -> {
            try {
                cfg.bgPadX = Math.max(4f, Float.parseFloat(v.trim()));
            } catch (Exception ignored) {}
        });
        addRenderableWidget(bgPadXBox);
        bgPadYBox = new EditBox(font, fx + halfW + 6, y + 11, halfW, FIELD_H, Component.empty());
        bgPadYBox.setMaxLength(5);
        bgPadYBox.setValue(String.valueOf(Math.round(cfg.bgPadY)));
        bgPadYBox.setResponder(v -> {
            try {
                cfg.bgPadY = Math.max(4f, Float.parseFloat(v.trim()));
            } catch (Exception ignored) {}
        });
        addRenderableWidget(bgPadYBox);
        y += STRIDE + 8;

        addRenderableWidget(Button.builder(Component.literal("§7⊡ Fit to elements now"), b -> {
            fitBackgroundToElements();
            setFeedback("Background fit to current elements");
            rebuildFields();
        }).bounds(fx, y, fw, FIELD_H)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "One-time snap to snugly wrap the icon/title/label's CURRENT positions - " +
                                "the background never auto-follows them, this is the only way to resync it")))
                .build());
        y += STRIDE + 10;

        if (PHANTASIA) {
            phantasiaY = y;
            phantasiaIdBox = new EditBox(font, fx, y + 11, fw, FIELD_H, Component.empty());
            phantasiaIdBox.setMaxLength(128);
            phantasiaIdBox.setHint(Component.literal("§8Phantasia machine id (optional)"));
            phantasiaIdBox.setValue(cfg.phantasiaMachineId);
            phantasiaIdBox.setResponder(v -> cfg.phantasiaMachineId = v);
            addRenderableWidget(phantasiaIdBox);
            y += STRIDE + 10;
        }

        bgColorY = y;
        bgColorBox = new EditBox(font, fx, y + 11, fw, FIELD_H, Component.empty());
        bgColorBox.setMaxLength(7);
        bgColorBox.setValue(ChroniclesUIKit.formatHexColor(cfg.bgColor));
        bgColorBox.setResponder(
                v -> cfg.bgColor = ChroniclesUIKit.parseHexColor(v, cfg.bgColor) | (cfg.bgColor & 0xFF000000));
        addRenderableWidget(bgColorBox);
        y += STRIDE + 10;

        accentColorY = y;
        accentColorBox = new EditBox(font, fx, y + 11, fw, FIELD_H, Component.empty());
        accentColorBox.setMaxLength(7);
        accentColorBox.setValue(ChroniclesUIKit.formatHexColor(cfg.accentColor));
        accentColorBox
                .setResponder(v -> cfg.accentColor = ChroniclesUIKit.parseHexColor(v, cfg.accentColor) | 0xFF000000);
        addRenderableWidget(accentColorBox);
    }

    private static final float FIT_MARGIN_X = 12f;
    private static final float FIT_MARGIN_Y = 10f;

    private void fitBackgroundToElements() {
        int previewW = vw - PANEL_W;
        float tx = cfg.title.x * previewW, ty = cfg.title.y * vh;
        float lx = cfg.label.x * previewW, ly = cfg.label.y * vh;
        float ix = cfg.icon.x * previewW, iy = cfg.icon.y * vh;

        float titleHalfH = textBlockHalfHeight(cfg.title, node.getTitle().getString(), previewW);
        float labelHalfH = textBlockHalfHeight(cfg.label, "Quest Complete!", previewW);

        float widthCap = previewW * 0.4f;
        float titleHalfW = textBlockHalfWidth(cfg.title, node.getTitle().getString(), widthCap);
        float labelHalfW = textBlockHalfWidth(cfg.label, "Quest Complete!", widthCap);

        float minX = Math.min(tx - titleHalfW, Math.min(lx - labelHalfW, ix)) - FIT_MARGIN_X;
        float maxX = Math.max(tx + titleHalfW, Math.max(lx + labelHalfW, ix)) + FIT_MARGIN_X;
        float minY = Math.min(ty - titleHalfH, Math.min(ly - labelHalfH, iy)) - FIT_MARGIN_Y;
        float maxY = Math.max(ty + titleHalfH, Math.max(ly + labelHalfH, iy)) + FIT_MARGIN_Y;
        cfg.bgX = ((minX + maxX) / 2f) / previewW;
        cfg.bgY = ((minY + maxY) / 2f) / vh;
        cfg.bgPadX = (maxX - minX) / 2f;
        cfg.bgPadY = (maxY - minY) / 2f;
    }

    private float textBlockHalfWidth(QuestToastConfig.Element el, String text, float capHalfWidth) {
        String display = (el.bold ? "§l" : "") + text;
        float halfWidth = font.width(display) * el.scale / 2f;
        return Math.min(halfWidth, capHalfWidth);
    }

    private float textBlockHalfHeight(QuestToastConfig.Element el, String text, int previewW) {
        float x = el.x * previewW;
        float screenRoomHalf = Math.min(x, previewW - x) - 8f;
        float widthCapLocal = Math.min(cfg.bgPadX * 2 - 16, screenRoomHalf * 2);
        int maxWidth = Math.max(20, Math.round(widthCapLocal / el.scale));

        String display = (el.bold ? "§l" : "") + text;
        int lines = font.split(Component.literal(display), maxWidth).size();
        return font.lineHeight * lines * el.scale / 2f;
    }

    private void initPresetsTab(int fx, int fw, int y) {
        int presetHalfW = (fw - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("§7⎘ Copy design"), b -> {
            toastClipboard = cfg.copy();
            setFeedback("Design copied");
        }).bounds(fx, y, presetHalfW, FIELD_H).build());
        addRenderableWidget(Button.builder(
                Component.literal(toastClipboard != null ? "§7⎗ Paste design" : "§8⎗ Paste (none copied)"), b -> {
                    if (toastClipboard != null) {
                        cfg = toastClipboard.copy();
                        setFeedback("Design pasted");
                        rebuildFields();
                    }
                }).bounds(fx + presetHalfW + 6, y, presetHalfW, FIELD_H).build());
        y += STRIDE + 8;

        presetY = y;
        addRenderableWidget(Button.builder(Component.literal("§7Save as preset…"), b -> {
            if (minecraft != null) minecraft.setScreen(new QuestTextInputScreen(this, "Preset Name", "", 32, name -> {
                if (name != null && !name.isBlank()) {
                    QuestToastPresetRegistry.put(name.trim(), cfg.copy());
                    setFeedback("Saved preset '" + name.trim() + "'");
                }
            }));
        }).bounds(fx, y, presetHalfW, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("§7Load preset ▾"),
                b -> presetDropOpen = !presetDropOpen).bounds(fx + presetHalfW + 6, y, presetHalfW, FIELD_H).build());
    }

    private void rebuildFields() {
        clearWidgets();
        init();
    }

    @Override
    protected void repositionElements() {
        rebuildFields();
    }

    private String elemLabel(Elem e) {
        return (selected == e ? "§f" : "§7") + elemPlainLabel(e);
    }

    private static String elemPlainLabel(Elem e) {
        return switch (e) {
            case ICON -> "Icon";
            case TITLE -> "Title";
            case LABEL -> "Label";
        };
    }

    private void setFeedback(String msg) {
        feedbackMsg = msg;
        feedbackUntil = System.currentTimeMillis() + 1800;
    }

    private QuestToastConfig.Element elemOf(Elem e) {
        return switch (e) {
            case ICON -> cfg.icon;
            case TITLE -> cfg.title;
            case LABEL -> cfg.label;
        };
    }

    private void save() {
        QuestToastConfig.put(node.getId().toString(), cfg);
        closeDesigner();
    }

    private void closeDesigner() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int rawMx, int rawMy, float partial) {
        int mx = Math.round(rawMx / uiScale);
        int my = Math.round(rawMy / uiScale);

        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.pose().scale(uiScale, uiScale, 1f);

        g.flush();

        int previewW = vw - PANEL_W;

        g.fill(0, 0, previewW, vh, ChroniclesThemePalette.BG);
        g.fill(0, 0, previewW, vh, 0x33000000);

        QuestToastManager.get().renderCustom(g, font, previewW, vh, previewToast, cfg);
        drawDragHandles(g, previewW, mx, my);
        if (snapGuideX != null) {
            int gx = Math.round(snapGuideX);
            for (int gy = 0; gy < vh; gy += 4) g.fill(gx, gy, gx + 1, gy + 2, 0xAA55FFAA);
        }
        if (snapGuideY != null) {
            int gy = Math.round(snapGuideY);
            for (int gx = 0; gx < previewW; gx += 4) g.fill(gx, gy, gx + 2, gy + 1, 0xAA55FFAA);
        }

        int px = vw - PANEL_W;
        g.fill(px, 0, vw, vh, ChroniclesThemePalette.PANEL);
        g.fill(px, 0, px + 1, vh, ChroniclesThemePalette.BORDER);
        g.drawCenteredString(font, "§eToast Designer", px + PANEL_W / 2, 8, ChroniclesThemePalette.TEXT);
        g.drawString(font, "§8" + shortTitle(), px + MARGIN, 18, ChroniclesThemePalette.TEXT_FAINT, false);

        int fx = px + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        g.enableScissor(px, contentTop, vw, contentBottom);
        switch (activeTab) {
            case ELEMENT -> {
                g.drawString(font, "§8Position (% X, Y)", fx, positionY, ChroniclesThemePalette.TEXT_FAINT);
                g.drawString(font, "§8Scale", fx, scaleY, ChroniclesThemePalette.TEXT_FAINT);
                g.drawString(font, "§8Text Color", fx, colorY, ChroniclesThemePalette.TEXT_FAINT);
            }
            case ICONS -> {
                g.drawString(font, "§8Icon Set §7(overrides icon)", fx, iconSetY - 9,
                        ChroniclesThemePalette.TEXT_FAINT);
                renderIconStrip(g, mx, my, fx, iconStripY);
                if (selectedIconIndex >= 0 && selectedIconIndex < cfg.icons.size()) {
                    g.drawString(font, "§8Scale (selected icon)", fx, iconScaleY, ChroniclesThemePalette.TEXT_FAINT);
                }
            }
            case BACKGROUND -> {
                g.drawString(font, "§8Background size (W × H)", fx, sizeY, ChroniclesThemePalette.TEXT_FAINT);
                if (PHANTASIA) {
                    g.drawString(font, "§8Phantasia §7(optional)", fx, phantasiaY, ChroniclesThemePalette.TEXT_FAINT);
                }
                g.drawString(font, "§8Background / Accent", fx, bgColorY, ChroniclesThemePalette.TEXT_FAINT);
            }
            case PRESETS -> {

            }
        }
        g.disableScissor();

        int saveY = vh - (hadExistingConfig ? 60 : 38);
        int feedbackY = saveY - 11;
        int hintY = saveY - 22;

        if (feedbackMsg != null && System.currentTimeMillis() < feedbackUntil) {
            g.drawString(font, "§a" + feedbackMsg, fx, feedbackY, ChroniclesThemePalette.TEXT);
        } else {

            List<net.minecraft.util.FormattedCharSequence> hintLines = font
                    .split(Component.literal("§8Drag elements in the preview to move them"), fw);
            int hy = hintY;
            for (net.minecraft.util.FormattedCharSequence line : hintLines) {
                g.drawString(font, line, fx, hy, ChroniclesThemePalette.TEXT_FAINT, false);
                hy += 10;
            }
        }

        g.flush();
        super.render(g, mx, my, partial);

        if (presetDropOpen) {
            g.flush();
            List<String> names = QuestToastPresetRegistry.names();
            int dw = (fw - 6) / 2;
            int dx = fx + dw + 6;
            if (names.isEmpty()) {
                int dy = presetY + FIELD_H;
                g.fill(dx, dy, dx + dw, dy + ROW_H, ChroniclesThemePalette.PANEL);
                ChroniclesUIKit.drawBorder(g, dx, dy, dw, ROW_H, ChroniclesThemePalette.BORDER);
                g.drawString(font, "§8(no presets saved yet)", dx + 4, dy + 4, ChroniclesThemePalette.TEXT_FAINT,
                        false);
            } else {
                ChroniclesUIKit.drawDropdown(g, font, names, s -> (String) s, -1, dx, presetY + FIELD_H, dw, ROW_H,
                        mx, my);
            }
        }

        g.pose().popPose();

        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, false);
        g.flush();
    }

    private static final int ROW_H = 14;

    private static final int ICON_REMOVE_BADGE = 6;

    private void renderIconStrip(GuiGraphics g, int mx, int my, int x, int y) {
        int sz = 16, gap = 3;
        hoveredIconIndex = -1;
        hoveredRemoveIconIndex = -1;
        if (cfg.icons.isEmpty()) {
            g.drawString(font, "§8(none: auto quest icon used)", x, y + 4, ChroniclesThemePalette.TEXT_FAINT, false);
            return;
        }
        int ix = x;
        for (int i = 0; i < cfg.icons.size(); i++) {
            boolean hov = mx >= ix && mx < ix + sz && my >= y && my < y + sz;
            if (hov) hoveredIconIndex = i;
            QuestToastConfig.IconEntry entry = cfg.icons.get(i);
            renderIcon(g, new QuestGroup.GroupIcon(entry.kind, entry.id), ix, y, sz);
            if (i == selectedIconIndex) ChroniclesUIKit.drawBorder(g, ix - 1, y - 1, sz + 2, sz + 2, 0xFFFFCC44);
            else if (hov) ChroniclesUIKit.drawBorder(g, ix - 1, y - 1, sz + 2, sz + 2, 0x88FFFFFF);

            int rx = ix + sz - ICON_REMOVE_BADGE, ry = y;
            boolean remHov = mx >= rx && mx < rx + ICON_REMOVE_BADGE && my >= ry && my < ry + ICON_REMOVE_BADGE;
            if (remHov) hoveredRemoveIconIndex = i;
            g.fill(rx, ry, rx + ICON_REMOVE_BADGE, ry + ICON_REMOVE_BADGE, remHov ? 0xFFCC2222 : 0xAA661111);
            g.drawString(font, "§f×", rx - 1, ry - 1, 0xFFFFFFFF, false);
            ix += sz + gap;
        }
    }

    private void renderIcon(GuiGraphics g, QuestGroup.GroupIcon icon, int x, int y, int size) {
        try {
            switch (icon.kind) {
                case ITEM -> {
                    net.minecraft.world.item.Item item = ForgeRegistries.ITEMS
                            .getValue(ResourceLocation.parse(icon.id));
                    if (item == null || item == net.minecraft.world.item.Items.AIR) return;
                    float scale = size / 16f;
                    g.pose().pushPose();
                    g.pose().translate(x + size / 2f, y + size / 2f, 0f);
                    g.pose().scale(scale, scale, scale);
                    g.renderItem(new net.minecraft.world.item.ItemStack(item), -8, -8);
                    g.pose().popPose();
                }
                case FLUID -> {
                    net.minecraft.world.level.material.Fluid fluid = ForgeRegistries.FLUIDS
                            .getValue(ResourceLocation.parse(icon.id));
                    ChroniclesUIKit.drawFluidIcon(g, fluid, x, y, size);
                    ChroniclesUIKit.drawBorder(g, x, y, size, size, 0xFF444455);
                }
                case TEXTURE -> g.blit(ResourceLocation.parse(icon.id), x, y, 0, 0, size, size, size, size);
            }
        } catch (Exception ignored) {

        }
    }

    private String shortTitle() {
        String t = node.getTitle().getString();
        return t.length() > 24 ? t.substring(0, 24) + "…" : t;
    }

    private static final int BG_HANDLE_SZ = 6;

    private int[][] bgResizeHandles(int previewW) {
        int[] box = bgBox(previewW);
        int h = BG_HANDLE_SZ / 2;
        return new int[][] {
                { box[0] - h, box[1] - h, box[0] + h, box[1] + h },
                { box[2] - h, box[1] - h, box[2] + h, box[1] + h },
                { box[0] - h, box[3] - h, box[0] + h, box[3] + h },
                { box[2] - h, box[3] - h, box[2] + h, box[3] + h },
        };
    }

    private void drawDragHandles(GuiGraphics g, int previewW, int mx, int my) {
        int[] box = bgBox(previewW);
        boolean hov = mx >= box[0] && mx <= box[2] && my >= box[1] && my <= box[3];
        if (hov || draggingBg || resizingBg) {
            int col = draggingBg || resizingBg ? 0xFFFFFFFF : 0x66FFFFFF;
            ChroniclesUIKit.drawBorder(g, box[0], box[1], box[2] - box[0], box[3] - box[1], col);
        }
        if (hov || draggingBg || resizingBg) {
            for (int[] hbox : bgResizeHandles(previewW)) {
                boolean hhov = mx >= hbox[0] && mx <= hbox[2] && my >= hbox[1] && my <= hbox[3];
                g.fill(hbox[0], hbox[1], hbox[2], hbox[3], (hhov || resizingBg) ? 0xFFFFFFFF : 0xFFAAAAAA);
                ChroniclesUIKit.drawBorder(g, hbox[0], hbox[1], hbox[2] - hbox[0], hbox[3] - hbox[1], 0xFF000000);
            }
        }
        for (Elem e : Elem.values()) {
            if (e == Elem.ICON && !cfg.icons.isEmpty()) continue;
            int[] ebox = elementBox(e, previewW);
            boolean ehov = mx >= ebox[0] && mx <= ebox[2] && my >= ebox[1] && my <= ebox[3];
            boolean isSelected = e == selected;
            if (ehov || isSelected || dragging == e) {
                int col = dragging == e ? 0xFFFFFFFF : (isSelected ? 0xFFFFCC44 : 0x88FFFFFF);
                ChroniclesUIKit.drawBorder(g, ebox[0], ebox[1], ebox[2] - ebox[0], ebox[3] - ebox[1], col);
            }
        }
        for (int i = 0; i < cfg.icons.size(); i++) {
            int[] ibox = iconEntryBox(i, previewW);
            boolean ihov = mx >= ibox[0] && mx <= ibox[2] && my >= ibox[1] && my <= ibox[3];
            boolean isSelected = i == selectedIconIndex;
            if (ihov || isSelected || draggingIconIndex == i) {
                int col = draggingIconIndex == i ? 0xFFFFFFFF : (isSelected ? 0xFFFFCC44 : 0x88FFFFFF);
                ChroniclesUIKit.drawBorder(g, ibox[0], ibox[1], ibox[2] - ibox[0], ibox[3] - ibox[1], col);
            }
        }
    }

    private int[] bgBox(int previewW) {
        int bx = Math.round(cfg.bgX * previewW), by = Math.round(cfg.bgY * vh);
        int hw = Math.round(cfg.bgPadX), hh = Math.round(cfg.bgPadY);
        return new int[] { bx - hw, by - hh, bx + hw, by + hh };
    }

    private int[] elementBox(Elem e, int previewW) {
        QuestToastConfig.Element el = elemOf(e);
        int cx = Math.round(el.x * previewW), cy = Math.round(el.y * vh);
        int halfW, halfH;
        if (e == Elem.ICON) {
            halfW = halfH = Math.round(8 * el.scale) + 2;
        } else {

            String sample = e == Elem.TITLE ? shortTitle() : "Quest Complete!";
            halfW = Math.round(font.width(sample) * el.scale / 2f) + 3;
            halfH = Math.round(font.lineHeight * 2 * el.scale / 2f) + 2;
        }
        return new int[] { cx - halfW, cy - halfH, cx + halfW, cy + halfH };
    }

    private int[] iconEntryBox(int index, int previewW) {
        QuestToastConfig.IconEntry entry = cfg.icons.get(index);
        int cx = Math.round(entry.x * previewW), cy = Math.round(entry.y * vh);
        int half = Math.round(8 * entry.scale) + 2;
        return new int[] { cx - half, cy - half, cx + half, cy + half };
    }

    @Override
    public boolean mouseClicked(double rawMx, double rawMy, int btn) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (presetDropOpen) {
            if (btn == 0) {
                List<String> names = QuestToastPresetRegistry.names();
                int fx = vw - PANEL_W + MARGIN;
                int fw = PANEL_W - MARGIN * 2;
                int dw = (fw - 6) / 2;
                int dx = fx + dw + 6;
                int dy = presetY + FIELD_H;
                for (int i = 0; i < names.size(); i++) {
                    int rowY = dy + i * ROW_H;
                    if (mx >= dx && mx <= dx + dw && my >= rowY && my <= rowY + ROW_H) {
                        QuestToastConfig preset = QuestToastPresetRegistry.getOrNull(names.get(i));
                        if (preset != null) {
                            cfg = preset.copy();
                            setFeedback("Loaded preset '" + names.get(i) + "'");
                            rebuildFields();
                        }
                        presetDropOpen = false;
                        return true;
                    }
                }
            }
            presetDropOpen = false;
            return true;
        }
        if (btn == 0 && hoveredRemoveIconIndex >= 0 && hoveredRemoveIconIndex < cfg.icons.size()) {
            cfg.icons.remove(hoveredRemoveIconIndex);
            if (selectedIconIndex == hoveredRemoveIconIndex) selectedIconIndex = -1;
            else if (selectedIconIndex > hoveredRemoveIconIndex) selectedIconIndex--;
            hoveredRemoveIconIndex = -1;
            rebuildFields();
            return true;
        }
        if (btn == 0 && hoveredIconIndex >= 0 && hoveredIconIndex < cfg.icons.size()) {
            selectedIconIndex = hoveredIconIndex;
            rebuildFields();
            return true;
        }
        if (btn == 0 && mx < vw - PANEL_W) {
            int previewW = vw - PANEL_W;

            for (int[] hbox : bgResizeHandles(previewW)) {
                if (mx >= hbox[0] && mx <= hbox[2] && my >= hbox[1] && my <= hbox[3]) {
                    resizingBg = true;
                    return true;
                }
            }
            for (Elem e : Elem.values()) {

                if (e == Elem.ICON && !cfg.icons.isEmpty()) continue;
                int[] box = elementBox(e, previewW);
                if (mx >= box[0] && mx <= box[2] && my >= box[1] && my <= box[3]) {
                    dragging = e;
                    if (selected != e) {
                        selected = e;
                        rebuildFields();
                    }
                    return true;
                }
            }
            for (int i = 0; i < cfg.icons.size(); i++) {
                int[] box = iconEntryBox(i, previewW);
                if (mx >= box[0] && mx <= box[2] && my >= box[1] && my <= box[3]) {
                    draggingIconIndex = i;
                    if (selectedIconIndex != i) {
                        selectedIconIndex = i;
                        rebuildFields();
                    }
                    return true;
                }
            }

            int[] box = bgBox(previewW);
            if (mx >= box[0] && mx <= box[2] && my >= box[1] && my <= box[3]) {
                draggingBg = true;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double rawMx, double rawMy, double delta) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (mx >= vw - PANEL_W) {
            panelScrollY = Math.max(0, panelScrollY - (int) Math.round(delta * 12));
            rebuildFields();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private static final int SNAP_PX = 4;

    @Override
    public boolean mouseDragged(double rawMx, double rawMy, int btn, double rawDx, double rawDy) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        double dx = rawDx / uiScale;
        double dy = rawDy / uiScale;
        snapGuideX = null;
        snapGuideY = null;
        int previewW = vw - PANEL_W;
        if (dragging != null) {
            QuestToastConfig.Element el = elemOf(dragging);
            float px = Math.max(0.02f, Math.min(0.98f, (float) (mx / previewW)));
            float py = Math.max(0.05f, Math.min(0.95f, (float) (my / vh)));

            List<Float> snapXs = new ArrayList<>();
            List<Float> snapYs = new ArrayList<>();
            snapXs.add(0.5f);
            snapYs.add(0.5f);
            for (Elem e : Elem.values()) {
                if (e == dragging) continue;
                QuestToastConfig.Element other = elemOf(e);
                snapXs.add(other.x);
                snapYs.add(other.y);
            }
            float pxPos = px * previewW;
            for (float sx : snapXs) {
                float snapPxPos = sx * previewW;
                if (Math.abs(pxPos - snapPxPos) <= SNAP_PX) {
                    px = sx;
                    snapGuideX = snapPxPos;
                    break;
                }
            }
            float pyPos = py * vh;
            for (float sy : snapYs) {
                float snapPyPos = sy * vh;
                if (Math.abs(pyPos - snapPyPos) <= SNAP_PX) {
                    py = sy;
                    snapGuideY = snapPyPos;
                    break;
                }
            }
            el.x = px;
            el.y = py;
            return true;
        }
        if (draggingIconIndex >= 0 && draggingIconIndex < cfg.icons.size()) {
            QuestToastConfig.IconEntry entry = cfg.icons.get(draggingIconIndex);
            float px = Math.max(0.02f, Math.min(0.98f, (float) (mx / previewW)));
            float py = Math.max(0.05f, Math.min(0.95f, (float) (my / vh)));

            List<Float> snapXs = new ArrayList<>();
            List<Float> snapYs = new ArrayList<>();
            snapXs.add(0.5f);
            snapYs.add(0.5f);
            for (int i = 0; i < cfg.icons.size(); i++) {
                if (i == draggingIconIndex) continue;
                snapXs.add(cfg.icons.get(i).x);
                snapYs.add(cfg.icons.get(i).y);
            }
            float pxPos = px * previewW;
            for (float sx : snapXs) {
                float snapPxPos = sx * previewW;
                if (Math.abs(pxPos - snapPxPos) <= SNAP_PX) {
                    px = sx;
                    snapGuideX = snapPxPos;
                    break;
                }
            }
            float pyPos = py * vh;
            for (float sy : snapYs) {
                float snapPyPos = sy * vh;
                if (Math.abs(pyPos - snapPyPos) <= SNAP_PX) {
                    py = sy;
                    snapGuideY = snapPyPos;
                    break;
                }
            }
            entry.x = px;
            entry.y = py;
            return true;
        }
        if (draggingBg) {
            cfg.bgX = Math.max(0.02f, Math.min(0.98f, (float) (mx / previewW)));
            cfg.bgY = Math.max(0.05f, Math.min(0.95f, (float) (my / vh)));
            return true;
        }
        if (resizingBg) {

            float bx = cfg.bgX * previewW, by = cfg.bgY * vh;
            cfg.bgPadX = (float) Math.max(8.0, Math.abs(mx - bx));
            cfg.bgPadY = (float) Math.max(8.0, Math.abs(my - by));
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double rawMx, double rawMy, int btn) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (btn == 0 && resizingBg) {
            resizingBg = false;
            rebuildFields();
            return true;
        }
        if (btn == 0 && (dragging != null || draggingBg || draggingIconIndex >= 0)) {
            dragging = null;
            draggingBg = false;
            draggingIconIndex = -1;
            snapGuideX = null;
            snapGuideY = null;
            rebuildFields();
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            closeDesigner();
            return true;
        }

        if (activeTab == PanelTab.ELEMENT && !isAnyEditBoxFocused()) {
            float step = (mods & 0x1) != 0 ? 0.01f : 0.002f;
            QuestToastConfig.Element el = elemOf(selected);
            boolean moved = true;
            switch (key) {
                case 263 -> el.x = Math.max(0.02f, el.x - step);
                case 262 -> el.x = Math.min(0.98f, el.x + step);
                case 265 -> el.y = Math.max(0.05f, el.y - step);
                case 264 -> el.y = Math.min(0.95f, el.y + step);
                default -> moved = false;
            }
            if (moved) {
                rebuildFields();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    private boolean isAnyEditBoxFocused() {
        return (xBox != null && xBox.isFocused()) || (yBox != null && yBox.isFocused()) ||
                (colorBox != null && colorBox.isFocused()) || (scaleBox != null && scaleBox.isFocused()) ||
                (bgPadXBox != null && bgPadXBox.isFocused()) || (bgPadYBox != null && bgPadYBox.isFocused()) ||
                (phantasiaIdBox != null && phantasiaIdBox.isFocused()) ||
                (bgColorBox != null && bgColorBox.isFocused()) ||
                (accentColorBox != null && accentColorBox.isFocused());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
