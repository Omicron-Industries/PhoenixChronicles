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
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.NotNull;

/**
 * Freeform per-quest toast designer, opened via the node context menu's "Design toast…" entry.
 * Drag the icon/title/label directly in the live preview (rendered at real screen scale over the
 * actual overview screen behind it) to reposition; the side panel edits scale/color/bold for
 * whichever element is currently selected, plus toast-wide properties (icon set, size, an
 * optional Phantasia backdrop). Saving writes a QuestToastConfig for this quest alone - every
 * other quest keeps using whichever preset ToastStyle is selected in settings.
 *
 * Row Y-positions are computed once in init() and stored in instance fields, then reused as-is
 * for the matching labels in render() - this used to be two independently hand-maintained copies
 * of the same running "y +=" arithmetic, which had already drifted out of sync once (see the old
 * comment this replaced, on the background/accent label).
 */
public class ToastDesignerScreen extends Screen {

    private enum Elem {
        ICON,
        TITLE,
        LABEL
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

    private Elem selected = Elem.TITLE;
    private Elem dragging = null;
    private QuestToastManager.ActiveToast previewToast;

    private EditBox colorBox;
    private EditBox scaleBox;
    private EditBox bgPadXBox;
    private EditBox bgPadYBox;
    private EditBox phantasiaIdBox;
    private EditBox bgColorBox;
    private EditBox accentColorBox;

    // Row Y-positions, computed once in init() and reused verbatim by render()'s labels.
    private int tabsY, scaleY, colorY, boldY, iconSetY, iconStripY, sizeY, phantasiaY, bgColorY, accentColorY, hintY;

    // Icon strip working state (mirrors QuestGroupEditorScreen's icon list editing).
    private int hoveredIconIndex = -1;

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
        if (previewToast == null)
            previewToast = QuestToastManager.makePreviewToast(node, QuestToastManager.ToastType.COMPLETED);

        int px = width - PANEL_W;
        int fx = px + MARGIN;
        int fw = PANEL_W - MARGIN * 2;
        int y = 30;

        tabsY = y;
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
        y += STRIDE + 14;

        // ── Custom icon set (toast-wide property, not tied to which tab is selected) ────────
        iconSetY = y;
        int iconBtnW = (fw - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("§7+ Item"), b -> {
            if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) cfg.icons.add(new QuestGroup.GroupIcon(QuestGroup.IconKind.ITEM, id.toString()));
            }));
        }).bounds(fx, y, iconBtnW, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("§3+ Fluid"), b -> {
            if (minecraft != null) minecraft.setScreen(new FluidPickerScreen(this,
                    fluidId -> cfg.icons.add(new QuestGroup.GroupIcon(QuestGroup.IconKind.FLUID, fluidId))));
        }).bounds(fx + iconBtnW + 4, y, iconBtnW, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("§d+ Texture"), b -> {
            if (minecraft != null) minecraft.setScreen(new TextureBrowserScreen(this,
                    texId -> cfg.icons.add(new QuestGroup.GroupIcon(QuestGroup.IconKind.TEXTURE, texId))));
        }).bounds(fx + (iconBtnW + 4) * 2, y, iconBtnW, FIELD_H).build());
        y += FIELD_H + 8;
        iconStripY = y;
        y += 16 + 10;

        // ── Size (background padding, independent of dragging) ─────────────────────────────
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
        y += STRIDE + 10;

        // ── Phantasia backdrop (optional) ───────────────────────────────────────────────────
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
        y += STRIDE + 16;

        int half = (fw - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("§aSave"), b -> save())
                .bounds(fx, y, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§7Cancel"), b -> closeDesigner())
                .bounds(fx + half + 6, y, half, 18).build());
        y += 22;
        if (hadExistingConfig) {
            addRenderableWidget(Button.builder(Component.literal("§cReset to default style"), b -> {
                QuestToastConfig.remove(node.getId().toString());
                closeDesigner();
            }).bounds(fx, y, fw, 16).build());
        }

        hintY = height - 20;
    }

    private void rebuildFields() {
        clearWidgets();
        init();
    }

    private String elemLabel(Elem e) {
        return (selected == e ? "§f" : "§7") + switch (e) {
            case ICON -> "Icon";
            case TITLE -> "Title";
            case LABEL -> "Label";
        };
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
        // label belongs AT that stored Y, not 11px above it. Subtracting 11 here put every label
        // a full row-height too high, landing inside the PREVIOUS row's box instead of just above
        // its own (e.g. "Text Color" rendering on top of the Scale box).
        g.drawString(font, "§8Scale", fx, scaleY, ChroniclesThemePalette.TEXT_FAINT);
        g.drawString(font, "§8Text Color (#AARRGGBB)", fx, colorY, ChroniclesThemePalette.TEXT_FAINT);
        g.drawString(font, "§8Icon Set  §7(overrides quest's own icon)", fx, iconSetY - 9,
                ChroniclesThemePalette.TEXT_FAINT);
        renderIconStrip(g, mx, my, fx, iconStripY);
        g.drawString(font, "§8Size (background W × H)", fx, sizeY, ChroniclesThemePalette.TEXT_FAINT);
        if (PHANTASIA) {
            g.drawString(font, "§8Phantasia Backdrop §7(optional)", fx, phantasiaY,
                    ChroniclesThemePalette.TEXT_FAINT);
        }
        g.drawString(font, "§8Background / Accent Color", fx, bgColorY, ChroniclesThemePalette.TEXT_FAINT);

        // Wrapped instead of a single drawString - the full sentence is wider than PANEL_W, so it
        // used to run past the actual window edge and read as truncated/unreadable.
        java.util.List<net.minecraft.util.FormattedCharSequence> hintLines = font
                .split(Component.literal("§8Drag elements in the preview to move them"), fw);
        int hy = hintY;
        for (net.minecraft.util.FormattedCharSequence line : hintLines) {
            g.drawString(font, line, fx, hy, ChroniclesThemePalette.TEXT_FAINT);
            hy += 10;
        }

        // The Icon/Title/Label tab buttons and every other widget below render through vanilla
        // Button code inside super.render() - a distinct draw pathway from the manual g.fill()/
        // g.renderItem() calls above it. Flushing the boundary here, same reasoning as the flush
        // at the top of this method, so the panel background this pathway draws over can't end up
        // submitted out of order relative to it.
        g.flush();
        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    private void renderIconStrip(GuiGraphics g, int mx, int my, int x, int y) {
        int sz = 16, gap = 3;
        hoveredIconIndex = -1;
        if (cfg.icons.isEmpty()) {
            g.drawString(font, "§8(none — auto quest icon used)", x, y + 4, ChroniclesThemePalette.TEXT_FAINT, false);
            return;
        }
        int ix = x;
        for (int i = 0; i < cfg.icons.size(); i++) {
            boolean hov = mx >= ix && mx < ix + sz && my >= y && my < y + sz;
            if (hov) hoveredIconIndex = i;
            renderIcon(g, cfg.icons.get(i), ix, y, sz);
            if (hov) {
                g.fill(ix, y, ix + sz, y + sz, 0x66CC2222);
                g.drawCenteredString(font, "§c✕", ix + sz / 2, y + sz / 2 - 4, 0xFFFFFFFF);
            }
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
                    if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return;
                    int col = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid)
                            .getTintColor() | 0xFF000000;
                    g.fill(x, y, x + size, y + size, col);
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

    /** Small selection outline around whichever element the mouse can currently drag. */
    private void drawDragHandles(GuiGraphics g, int previewW, int mx, int my) {
        for (Elem e : Elem.values()) {
            int[] box = elementBox(e, previewW);
            boolean hov = mx >= box[0] && mx <= box[2] && my >= box[1] && my <= box[3];
            boolean isSelected = e == selected;
            if (hov || isSelected || dragging == e) {
                int col = dragging == e ? 0xFFFFFFFF : (isSelected ? 0xFFFFCC44 : 0x88FFFFFF);
                ChroniclesUIKit.drawBorder(g, box[0], box[1], box[2] - box[0], box[3] - box[1], col);
            }
        }
    }

    /** Approximate clickable bounds for an element at its current position, in real screen pixels. */
    private int[] elementBox(Elem e, int previewW) {
        QuestToastConfig.Element el = elemOf(e);
        int cx = Math.round(el.x * previewW), cy = Math.round(el.y * height);
        int halfW, halfH;
        if (e == Elem.ICON) {
            halfW = halfH = Math.round(8 * el.scale) + 2;
        } else {
            String sample = e == Elem.TITLE ? shortTitle() : "Quest Complete!";
            halfW = Math.round(font.width(sample) * el.scale / 2f) + 3;
            halfH = Math.round(font.lineHeight * el.scale / 2f) + 2;
        }
        return new int[] { cx - halfW, cy - halfH, cx + halfW, cy + halfH };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && hoveredIconIndex >= 0 && hoveredIconIndex < cfg.icons.size()) {
            cfg.icons.remove(hoveredIconIndex);
            hoveredIconIndex = -1;
            return true;
        }
        if (btn == 0 && mx < width - PANEL_W) {
            int previewW = width - PANEL_W;
            for (Elem e : Elem.values()) {
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
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging != null) {
            int previewW = width - PANEL_W;
            QuestToastConfig.Element el = elemOf(dragging);
            el.x = Math.max(0.02f, Math.min(0.98f, (float) (mx / previewW)));
            el.y = Math.max(0.05f, Math.min(0.95f, (float) (my / height)));
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0 && dragging != null) {
            dragging = null;
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
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
