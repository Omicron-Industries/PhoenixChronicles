package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.QuestToastConfig;
import net.phoenixvine.chronicles.client.QuestToastManager;
import net.phoenixvine.chronicles.client.QuestToastPresetRegistry;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Freeform per-quest toast designer, opened via the node context menu's "Design toast…" entry.
 * Drag the icon/title/label directly in the live preview (rendered at real screen scale over the
 * actual overview screen behind it) to reposition; the side panel is organized into tabs (Element,
 * Icons, Background, Presets) - it grew a lot of controls over time and a single flat scroll of
 * everything read as cluttered. Saving writes a QuestToastConfig for this quest alone - every
 * other quest keeps using whichever preset ToastStyle is selected in settings.
 *
 * Row Y-positions within the CURRENTLY ACTIVE tab are computed once in init() and stored in
 * instance fields, then reused as-is for the matching labels in render() - this used to be two
 * independently hand-maintained copies of the same running "y +=" arithmetic, which had already
 * drifted out of sync once (see the old comment this replaced, on the background/accent label).
 * Save/Cancel/hint/feedback are anchored from the BOTTOM of the panel instead, since tab content
 * height varies a lot between tabs and pinning the shared chrome keeps it from jumping around.
 */
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

    // Row Y-positions within the active tab, computed once in init() and reused verbatim by
    // render()'s labels - only the ones for the CURRENTLY active tab are meaningful in any given
    // frame, so every label draw below is gated on activeTab matching.
    private int tabsY, positionY, scaleY, colorY, boldY, iconSetY, iconStripY, sizeY, phantasiaY, bgColorY,
            accentColorY, presetY;

    // Scroll offset for the CURRENT tab's content, in panel pixels - for high GUI-scale users
    // where the shrunk logical screen height can't fit a tab's full content between the tab bar
    // and the bottom Save/Cancel chrome. contentTop/contentBottom are the CURRENT frame's valid
    // window, recomputed every init() since they depend on `height`.
    private int panelScrollY = 0;
    private int contentTop, contentBottom;

    // Live preview state - which toast type text ("Quest Complete!" vs "Quest Unlocked") is shown,
    // and any snap-alignment guide line to draw this frame while dragging (see mouseDragged).
    private QuestToastManager.ToastType previewType = QuestToastManager.ToastType.COMPLETED;
    private Float snapGuideX, snapGuideY; // real screen-pixel coordinates, null = no guide this frame

    /** In-memory clipboard so a design can be copied from one quest's designer to another's - not persisted to disk. */
    private static QuestToastConfig toastClipboard = null;

    private boolean presetDropOpen = false;
    private String feedbackMsg = null;
    private long feedbackUntil = 0;

    // Icon strip working state (mirrors QuestGroupEditorScreen's icon list editing).
    private int hoveredIconIndex = -1;
    /** Which icon strip tile's small corner ✕ badge is hovered - see renderIconStrip. */
    private int hoveredRemoveIconIndex = -1;
    /**
     * Which cfg.icons entry the Icons tab's scale box edits, and which one dragging in the preview grabs by default.
     */
    private int selectedIconIndex = -1;
    /** Which cfg.icons entry is currently being dragged in the preview - separate from the text/bg drag state. */
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
        if (previewToast == null) previewToast = QuestToastManager.makePreviewToast(node, previewType);

        // One-time migration: an auto-fit background (the only kind that ever existed before an
        // independent one was added) gets baked into a fixed box matching its CURRENT visual
        // bounds, so opening the designer never causes a jump. From this point on the background
        // only ever moves via a direct action (drag it, edit its size, or "Fit to elements now"
        // below) - never as a side effect of moving the icon/title/label, which was the whole
        // complaint auto-fit caused.
        if (cfg.bgAutoFit) {
            fitBackgroundToElements();
            cfg.bgAutoFit = false;
        }

        int px = width - PANEL_W;
        int fx = px + MARGIN;
        int fw = PANEL_W - MARGIN * 2;
        int y = 30;

        // Preview toast type - "Quest Complete!" vs "Quest Unlocked" text, so a pack dev can
        // check both without needing to trigger a real unlock/completion in-game.
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

        // ── Tab bar ──────────────────────────────────────────────────────────────────────
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

        // ── Bottom chrome's Y only depends on `height`/hadExistingConfig, not tab content - safe
        // to compute now so the scrollable content region between it and the tab bar is known
        // BEFORE building that content ───────────────────────────────────────────────────────
        int half = (fw - 6) / 2;
        int saveY = height - (hadExistingConfig ? 60 : 38);
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

        // Clamp scroll to how far this tab's content actually extends, then hide (not just visibly
        // clip, but also make un-clickable) whatever widget still falls outside [contentTop,
        // contentBottom] - a scissor would only affect rendering, not click routing, and vanilla
        // widgets don't scissor-clip themselves. Widgets built with a stale (pre-clamp)
        // panelScrollY this ONE call are a rare, self-correcting cosmetic edge case (fixed the next
        // scroll tick or tab switch), not worth a recursive rebuild to avoid.
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
        // Kept short on purpose - 4 tabs share PANEL_W, so each button only gets ~40px. "Backdrop"
        // and "Presets" were long enough to visibly clip against their own button width.
        String name = switch (t) {
            case ELEMENT -> "Elem";
            case ICONS -> "Icons";
            case BACKGROUND -> "BG";
            case PRESETS -> "Preset";
        };
        return (activeTab == t ? "§f" : "§7") + name;
    }

    // ── Tab: Element (icon/title/label position, scale, color, bold) ───────────────────────

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

        // ── Position (numeric, in addition to dragging in the preview) ─────────────────────
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

    // ── Tab: Icons (custom icon set override) ───────────────────────────────────────────────

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
        // "+ Tex" not "+ Texture" - three buttons split a third of the panel each, and the full
        // word was long enough to visibly clip against its own button width.
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

    /**
     * New icons seed their position at the base icon slot's own position (cfg.icon) - same visual
     * starting point as before this feature existed, but now free to drag apart individually
     * instead of being permanently locked to move together as one strip.
     */
    private void addIconEntry(QuestGroup.IconKind kind, String id) {
        cfg.icons.add(new QuestToastConfig.IconEntry(kind, id, cfg.icon.x, cfg.icon.y, cfg.icon.scale));
        selectedIconIndex = cfg.icons.size() - 1;
        rebuildFields();
    }

    // ── Tab: Background (size, positioning, colors, Phantasia backdrop) ────────────────────

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

    /**
     * Snaps the background to snugly wrap the icon/title/label's CURRENT positions - the direct,
     * deliberate equivalent of what auto-fit used to do continuously as a side effect of moving
     * them. Also used once, silently, to migrate a legacy auto-fit config on first open (see
     * init()) so its visual bounds don't jump the moment bgAutoFit gets cleared.
     */
    private void fitBackgroundToElements() {
        int previewW = width - PANEL_W;
        float tx = cfg.title.x * previewW, ty = cfg.title.y * height;
        float lx = cfg.label.x * previewW, ly = cfg.label.y * height;
        float ix = cfg.icon.x * previewW, iy = cfg.icon.y * height;

        // A long title/label can word-wrap to several lines (see drawCustomElement) - account for
        // that here too, using the CURRENT bg half-width as the wrap-width guess, so a manual fit
        // gives the wrapped block enough vertical room instead of assuming a single line.
        float titleHalfH = textBlockHalfHeight(cfg.title, node.getTitle().getString(), previewW);
        float labelHalfH = textBlockHalfHeight(cfg.label, "Quest Complete!", previewW);

        float minX = Math.min(tx, Math.min(lx, ix)) - cfg.bgPadX;
        float maxX = Math.max(tx, Math.max(lx, ix)) + cfg.bgPadX;
        float minY = Math.min(ty - titleHalfH, Math.min(ly - labelHalfH, iy)) - cfg.bgPadY;
        float maxY = Math.max(ty + titleHalfH, Math.max(ly + labelHalfH, iy)) + cfg.bgPadY;
        cfg.bgX = ((minX + maxX) / 2f) / previewW;
        cfg.bgY = ((minY + maxY) / 2f) / height;
        cfg.bgPadX = (maxX - minX) / 2f;
        cfg.bgPadY = (maxY - minY) / 2f;
    }

    /**
     * Half the vertical space this element's text would occupy once word-wrapped - see elementBox/drawCustomElement.
     */
    private float textBlockHalfHeight(QuestToastConfig.Element el, String text, int previewW) {
        float x = el.x * previewW;
        float screenRoomHalf = Math.min(x, previewW - x) - 8f;
        float widthCapLocal = Math.min(cfg.bgPadX * 2 - 16, screenRoomHalf * 2);
        int maxWidth = Math.max(20, Math.round(widthCapLocal / el.scale));
        int lines = font.split(Component.literal(text), maxWidth).size();
        return font.lineHeight * lines * el.scale / 2f;
    }

    // ── Tab: Presets (reuse/save/load/copy-paste) ───────────────────────────────────────────

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

    /**
     * Vanilla Screen.resize() only calls the no-arg init() on FIRST open - every later window
     * resize/GUI-scale change instead calls this (a no-op unless overridden), which is why the
     * panel/preview layout used to go stale (buttons at the old width, preview drawn at the old
     * previewW) the moment you resized after opening. Same fix as ChronicleOverviewScreen's own
     * repositionElements() override.
     */
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
    public void renderBackground(@NotNull GuiGraphics g) { /* parent renders behind us */ }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        if (parent != null) parent.render(g, -1, -1, partial);

        // Force every draw call the parent just queued through GuiGraphics's deferred
        // bufferSource() to actually submit before we draw anything of our own - without this,
        // this heavily-modded pack's rendering pipeline can leave the parent's fills/text still
        // pending when our own z-elevated content gets queued, and depending on how those two
        // batches get flushed relative to each other the parent visibly bleeds through what
        // should be an opaque panel over it (same root cause DependencyLineRenderer's raw-
        // Tesselator path was built to sidestep entirely - see its class-level comment).
        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        // Same missing-flush bug found (and fixed) elsewhere this session: the translate above
        // isn't actually "in effect" for the depth test until something flushes, so without this
        // the parent's quest node icons (z=100) could still win against this panel's own fills.
        g.flush();

        int previewW = width - PANEL_W;
        // Solid backing for the preview canvas - this used to be a 33%-alpha scrim, which read
        // as the designer having no background of its own and the quest tree underneath just
        // bleeding through almost unobstructed instead of a proper opaque design surface.
        g.fill(0, 0, previewW, height, ChroniclesThemePalette.BG);
        g.fill(0, 0, previewW, height, 0x33000000); // subtle vignette so the canvas reads distinctly from the panel

        QuestToastManager.get().renderCustom(g, font, previewW, height, previewToast, cfg);
        drawDragHandles(g, previewW, mx, my);
        if (snapGuideX != null) {
            int gx = Math.round(snapGuideX);
            for (int gy = 0; gy < height; gy += 4) g.fill(gx, gy, gx + 1, gy + 2, 0xAA55FFAA);
        }
        if (snapGuideY != null) {
            int gy = Math.round(snapGuideY);
            for (int gx = 0; gx < previewW; gx += 4) g.fill(gx, gy, gx + 2, gy + 1, 0xAA55FFAA);
        }

        // Side panel chrome
        int px = width - PANEL_W;
        g.fill(px, 0, width, height, ChroniclesThemePalette.PANEL);
        g.fill(px, 0, px + 1, height, ChroniclesThemePalette.BORDER);
        g.drawCenteredString(font, "§eToast Designer", px + PANEL_W / 2, 8, ChroniclesThemePalette.TEXT);
        g.drawString(font, "§8" + shortTitle(), px + MARGIN, 18, ChroniclesThemePalette.TEXT_FAINT, false);

        int fx = px + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        // Each row's stored Y (scaleY, colorY, ...) is the row's own start position - the box
        // itself sits 11px below that (see init()'s "new EditBox(font, fx, y + 11, ...)"), so the
        // label belongs AT that stored Y, not 11px above it. Only draw the labels belonging to
        // whichever tab is actually active this frame - the others' Y fields are stale leftovers
        // from a previous tab's layout pass. Scissored to the scrollable content region so a
        // label doesn't visually leak into the tab bar/bottom chrome when scrolled - the widgets
        // themselves are separately hidden (not just clipped) via their own .visible flag, see
        // init(), since a scissor alone wouldn't stop them from still being clickable.
        g.enableScissor(px, contentTop, width, contentBottom);
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
                // Copy/Save/Load buttons are self-labeled, nothing extra to draw here.
            }
        }
        g.disableScissor();

        int saveY = height - (hadExistingConfig ? 60 : 38);
        int feedbackY = saveY - 11;
        int hintY = saveY - 22;

        if (feedbackMsg != null && System.currentTimeMillis() < feedbackUntil) {
            g.drawString(font, "§a" + feedbackMsg, fx, feedbackY, ChroniclesThemePalette.TEXT);
        } else {
            // Actually wrapped, not a single drawString - the full sentence is wider than PANEL_W,
            // so an unwrapped draw runs past the actual window edge and reads as truncated
            // garbage (a real regression from an earlier rewrite of this screen - the wrap call
            // was accidentally dropped even though this comment describing it survived).
            List<net.minecraft.util.FormattedCharSequence> hintLines = font
                    .split(Component.literal("§8Drag elements in the preview to move them"), fw);
            int hy = hintY;
            for (net.minecraft.util.FormattedCharSequence line : hintLines) {
                g.drawString(font, line, fx, hy, ChroniclesThemePalette.TEXT_FAINT, false);
                hy += 10;
            }
        }

        // The tab/element buttons and every other widget below render through vanilla Button code
        // inside super.render() - a distinct draw pathway from the manual g.fill()/g.renderItem()
        // calls above it. Flushing the boundary here, same reasoning as the flush at the top of
        // this method, so the panel background this pathway draws over can't end up submitted out
        // of order relative to it.
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

        // Every g.renderItem() call above (the toast preview's icon, the icon-set strip) writes
        // REAL depth-buffer values, same as everywhere else in this pack that draws item icons -
        // a plain g.flush() submits queued draws but does NOT clear that depth buffer. Any picker
        // screen opened FROM here (ItemPickerScreen/FluidPickerScreen/TextureBrowserScreen for the
        // icon set) calls this exact render() as its own "draw the parent behind me" backdrop
        // WITHIN THE SAME FRAME, then draws its own opaque panel fill at z=0-ish afterward - if
        // that fill's z happens to compare "behind" whatever depth an icon rendered here left
        // behind, the fill loses the depth test and the icon shows through it. Clearing just the
        // depth buffer (not color) wipes that residual without touching anything already drawn.
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
            g.drawString(font, "§8(none — auto quest icon used)", x, y + 4, ChroniclesThemePalette.TEXT_FAINT, false);
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
            // Small corner badge to remove, so the REST of the tile is free to click-select
            // instead - click-anywhere-to-remove made it impossible to select a specific icon to
            // drag/scale once there was more than one.
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
                    net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(icon.id));
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
                            .getValue(new ResourceLocation(icon.id));
                    ChroniclesUIKit.drawFluidIcon(g, fluid, x, y, size);
                    ChroniclesUIKit.drawBorder(g, x, y, size, size, 0xFF444455);
                }
                case TEXTURE -> g.blit(new ResourceLocation(icon.id), x, y, 0, 0, size, size, size, size);
            }
        } catch (Exception ignored) {
            // Bad/renamed registry id or texture path — skip this icon rather than crash the frame.
        }
    }

    private String shortTitle() {
        String t = node.getTitle().getString();
        return t.length() > 24 ? t.substring(0, 24) + "…" : t;
    }

    private static final int BG_HANDLE_SZ = 6;

    /**
     * The 4 corner resize-handle boxes for the background, in real screen pixels, corners in [TL, TR, BL, BR] order.
     */
    private int[][] bgResizeHandles(int previewW) {
        int[] box = bgBox(previewW);
        int h = BG_HANDLE_SZ / 2;
        return new int[][] {
                { box[0] - h, box[1] - h, box[0] + h, box[1] + h }, // top-left
                { box[2] - h, box[1] - h, box[2] + h, box[1] + h }, // top-right
                { box[0] - h, box[3] - h, box[0] + h, box[3] + h }, // bottom-left
                { box[2] - h, box[3] - h, box[2] + h, box[3] + h }, // bottom-right
        };
    }

    /** Small selection outline around whichever element the mouse can currently drag. */
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
            if (e == Elem.ICON && !cfg.icons.isEmpty()) continue; // not rendered, see mouseClicked's matching skip
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

    /** The background's bounds in real screen pixels - always its own independent, directly-set box. */
    private int[] bgBox(int previewW) {
        int bx = Math.round(cfg.bgX * previewW), by = Math.round(cfg.bgY * height);
        int hw = Math.round(cfg.bgPadX), hh = Math.round(cfg.bgPadY);
        return new int[] { bx - hw, by - hh, bx + hw, by + hh };
    }

    /** Approximate clickable bounds for an element at its current position, in real screen pixels. */
    private int[] elementBox(Elem e, int previewW) {
        QuestToastConfig.Element el = elemOf(e);
        int cx = Math.round(el.x * previewW), cy = Math.round(el.y * height);
        int halfW, halfH;
        if (e == Elem.ICON) {
            halfW = halfH = Math.round(8 * el.scale) + 2;
        } else {
            // Deliberately NOT wrap-aware here. It used to recompute font.split() with the same
            // formula drawCustomElement uses, but near a screen edge that width estimate can
            // collapse small enough to wrap into many tiny lines, inflating THIS hitbox tall
            // enough to overlap a DIFFERENT element's position - clicking there silently grabbed
            // the wrong element (which then never moved and looked like it "reset" on release,
            // since it was never actually the one being dragged). A generous fixed 2-line-tall
            // hitbox is far more predictable and plenty large enough to grab either element.
            String sample = e == Elem.TITLE ? shortTitle() : "Quest Complete!";
            halfW = Math.round(font.width(sample) * el.scale / 2f) + 3;
            halfH = Math.round(font.lineHeight * 2 * el.scale / 2f) + 2;
        }
        return new int[] { cx - halfW, cy - halfH, cx + halfW, cy + halfH };
    }

    /** Clickable bounds for one independently-positioned custom icon-set entry, in real screen pixels. */
    private int[] iconEntryBox(int index, int previewW) {
        QuestToastConfig.IconEntry entry = cfg.icons.get(index);
        int cx = Math.round(entry.x * previewW), cy = Math.round(entry.y * height);
        int half = Math.round(8 * entry.scale) + 2;
        return new int[] { cx - half, cy - half, cx + half, cy + half };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (presetDropOpen) {
            if (btn == 0) {
                List<String> names = QuestToastPresetRegistry.names();
                int fx = width - PANEL_W + MARGIN;
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
        if (btn == 0 && mx < width - PANEL_W) {
            int previewW = width - PANEL_W;
            // Corner resize handles take priority over everything else - they're small and sit
            // right at the background's own corners, so they'd never get a chance to be grabbed
            // if an element's hitbox or the background's own move-hitbox were checked first.
            for (int[] hbox : bgResizeHandles(previewW)) {
                if (mx >= hbox[0] && mx <= hbox[2] && my >= hbox[1] && my <= hbox[3]) {
                    resizingBg = true;
                    return true;
                }
            }
            for (Elem e : Elem.values()) {
                // The base icon slot (cfg.icon) isn't actually rendered once a custom icon set
                // exists (see QuestToastManager.renderCustom) - each icons[] entry below takes
                // over as its own independently draggable target instead.
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
            // Elements take priority (checked above) since they're small and typically sit inside
            // the background - only fall back to grabbing the background itself if the click
            // missed all three.
            int[] box = bgBox(previewW);
            if (mx >= box[0] && mx <= box[2] && my >= box[1] && my <= box[3]) {
                draggingBg = true;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= width - PANEL_W) {
            panelScrollY = Math.max(0, panelScrollY - (int) Math.round(delta * 12));
            rebuildFields(); // re-clamps against this tab's actual content height and re-hides widgets
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private static final int SNAP_PX = 4;

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        snapGuideX = null;
        snapGuideY = null;
        int previewW = width - PANEL_W;
        if (dragging != null) {
            QuestToastConfig.Element el = elemOf(dragging);
            float px = Math.max(0.02f, Math.min(0.98f, (float) (mx / previewW)));
            float py = Math.max(0.05f, Math.min(0.95f, (float) (my / height)));

            // Snap to (and draw a guide line at) the canvas center or another element's current
            // position, on each axis independently - dragging near an existing alignment now
            // settles onto it instead of leaving things a pixel or two off. The background is
            // deliberately NOT a snap target here - it's independent and shouldn't visually couple
            // to wherever an element happens to land.
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
            float pyPos = py * height;
            for (float sy : snapYs) {
                float snapPyPos = sy * height;
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
            float py = Math.max(0.05f, Math.min(0.95f, (float) (my / height)));

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
            float pyPos = py * height;
            for (float sy : snapYs) {
                float snapPyPos = sy * height;
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
            cfg.bgY = Math.max(0.05f, Math.min(0.95f, (float) (my / height)));
            return true;
        }
        if (resizingBg) {
            // Symmetric resize about the (unmoved) center - simplest model, and consistent with
            // the numeric bgPadX/Y boxes already meaning "half-size", not "which corner is anchored".
            float bx = cfg.bgX * previewW, by = cfg.bgY * height;
            cfg.bgPadX = (float) Math.max(8.0, Math.abs(mx - bx));
            cfg.bgPadY = (float) Math.max(8.0, Math.abs(my - by));
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0 && resizingBg) {
            resizingBg = false;
            rebuildFields(); // refresh the numeric bgPadX/Y boxes to match wherever the resize settled
            return true;
        }
        if (btn == 0 && (dragging != null || draggingBg || draggingIconIndex >= 0)) {
            dragging = null;
            draggingBg = false;
            draggingIconIndex = -1;
            snapGuideX = null;
            snapGuideY = null;
            rebuildFields(); // refresh the numeric X/Y boxes to match wherever the drag settled
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // ESC
            closeDesigner();
            return true;
        }
        // Arrow-key nudge for the selected element - Shift for a bigger step. Only when no text
        // field currently has focus, so this doesn't hijack cursor movement while typing/editing
        // a number in one of the EditBoxes above, and only on the Element tab (nothing to nudge
        // otherwise).
        if (activeTab == PanelTab.ELEMENT && !isAnyEditBoxFocused()) {
            float step = (mods & 0x1) != 0 ? 0.01f : 0.002f; // GLFW_MOD_SHIFT = 0x1
            QuestToastConfig.Element el = elemOf(selected);
            boolean moved = true;
            switch (key) {
                case 263 -> el.x = Math.max(0.02f, el.x - step); // LEFT
                case 262 -> el.x = Math.min(0.98f, el.x + step); // RIGHT
                case 265 -> el.y = Math.max(0.05f, el.y - step); // UP
                case 264 -> el.y = Math.min(0.95f, el.y + step); // DOWN
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
