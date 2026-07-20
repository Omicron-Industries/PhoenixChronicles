package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;

import java.util.List;

/**
 * Lightweight style editor opened from the ✎ button in QuestTasksScreen.
 * Edits presentation fields only: title, description, shape, node size, dev notes.
 * Saves via QuestFileSaver on confirm.
 */
public class QuestStyleEditorScreen extends Screen {

    // ── Colours ───────────────────────────────────────────────────────────────
    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_DONE, C_ACTIVE;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 28;
    private static final int MARGIN = 10;
    private static final int FIELD_H = 14;
    private static final int LABEL_GAP = 3;
    private static final int ROW_H = FIELD_H + LABEL_GAP + 9; // label + box + gap

    private static final int PANEL_W = 420;

    // ── State ────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final QuestNode node;

    private EditBox titleBox;
    private EditBox devNotesBox;

    // Description is edited via the full rich-text editor (QuestTextInputScreen), opened by
    // clicking this preview box, rather than being a plain EditBox with a separate preview.
    private String descRaw;
    private int descPreviewX, descPreviewY, descPreviewW, descPreviewH;
    // Panel bounds, refreshed every render() — used by mouseClicked() to detect outside-click.
    private int panelBoundsX, panelBoundsY, panelBoundsH;

    private String selectedShape;
    /** Picked texture for the "CUSTOM" shape - ignored for every other shape id. */
    private String selectedShapeTexture;
    private QuestNode.NodeSize selectedSize;
    // Only true once the user actually clicks a size preset button - setNodeSize() always clears
    // any freeform pixel override set via the canvas's own "Resize (scroll + drag)…" mode as a
    // side effect, so applyAndSave() must NOT call it just because this screen was opened and
    // saved for an unrelated field (shape/description/etc), or it'd silently wipe that override.
    private boolean sizeChanged = false;

    // Description multiline simulation: list of rows that wrap
    private static final int DESC_ROWS = 5;
    private static final int DESC_PREVIEW_H = 40;
    private static final String[] SHAPES = { "CIRCLE", "SQUARE", "DIAMOND", "HEXAGON", "PENTAGON", "CUSTOM" };
    private static final String[] SHAPE_GLYPHS = { "●", "■", "◆", "⬡", "⬠", "▩" };

    // Hover / click tracking
    private int hovShapeIdx = -1;
    private int hovSaveBtn, hovCancelBtn;
    private int saveX, saveY, cancelX, cancelY;
    private static final int BTN_W = 70, BTN_H = 16;

    // ── Dirty / feedback ─────────────────────────────────────────────────────
    private String feedbackMsg = null;
    private long feedbackUntil = 0;

    public QuestStyleEditorScreen(Screen parent, QuestNode node) {
        super(Component.literal("Quest Style"));
        this.parent = parent;
        this.node = node;
        this.selectedShape = node.getShapeType() != null ? node.getShapeType() : "SQUARE";
        this.selectedShapeTexture = node.getShapeTexture() != null ? node.getShapeTexture() : "";
        this.selectedSize = node.getNodeSize() != null ? node.getNodeSize() : QuestNode.NodeSize.NORMAL;
        this.descRaw = node.getDescriptionRaw().getString();
    }

    @Override
    protected void init() {
        super.init();
        ChroniclesTheme t = ChroniclesTheme.current();
        C_BG = t.bg.getColor();
        C_PANEL = t.panel.getColor();
        C_HEADER = t.header.getColor();
        C_BORDER = t.border.getColor();
        C_TEXT = t.text.getColor();
        C_TEXT_DIM = t.textDim.getColor();
        C_TEXT_FAINT = t.textFaint.getColor();
        C_DONE = t.done.getColor();
        C_ACTIVE = t.activeColor.getColor();

        int px = (width - PANEL_W) / 2;
        int rowsNeeded = 3 /* title/desc/notes */ + 1 /* shape */ + 1 /* size */ + 1 /* padding */;
        int panelH = HEADER_H + FOOTER_H + rowsNeeded * ROW_H + DESC_ROWS * (FIELD_H - 5) + MARGIN * 3 + 40;
        int py = Math.max(4, (height - panelH) / 2);

        int fieldX = px + MARGIN;
        int fieldW = PANEL_W - MARGIN * 2;
        int cy = py + HEADER_H + MARGIN;

        // Title
        cy += LABEL_GAP + 9;
        titleBox = new EditBox(font, fieldX, cy, fieldW, FIELD_H, Component.empty());
        titleBox.setMaxLength(120);
        // Raw (untranslated) default - this screen edits the quest's baked SNBT text, and a
        // live translation should stay in lang/en_us.json rather than get shown here and
        // silently re-baked as the new default the moment style-only changes are saved.
        titleBox.setValue(node.getTitleRaw().getString());
        titleBox.setBordered(false);
        addWidget(titleBox);
        cy += FIELD_H + ROW_H - FIELD_H - LABEL_GAP - 9 + 4;

        // Description — clickable preview box, opens the full rich-text editor (see render()/
        // mouseClicked()) instead of an inline EditBox.
        cy += LABEL_GAP + 9 + 2;
        cy += DESC_PREVIEW_H + 8;

        // Dev notes
        cy += LABEL_GAP + 9 + 6;
        devNotesBox = new EditBox(font, fieldX, cy, fieldW, FIELD_H, Component.empty());
        devNotesBox.setMaxLength(500);
        devNotesBox.setValue(node.getDevNotes());
        devNotesBox.setBordered(false);
        addWidget(devNotesBox);

        setFocused(titleBox);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        hovShapeIdx = -1;
        hovSaveBtn = 0;
        hovCancelBtn = 0;

        // Background — render the actual parent screen (dimmed) instead of raw world, matching
        // the pattern established elsewhere this session (CategoryThemeScreen, ToastDesignerScreen).
        if (parent != null) parent.render(g, -1, -1, partial);
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.flush();
        g.fill(0, 0, width, height, 0xAA000000);

        int px = (width - PANEL_W) / 2;
        int rowsNeeded = 5;
        int panelH = HEADER_H + FOOTER_H + rowsNeeded * ROW_H + DESC_PREVIEW_H + MARGIN * 4 + 30;
        int py = Math.max(4, (height - panelH) / 2);
        this.panelBoundsX = px;
        this.panelBoundsY = py;
        this.panelBoundsH = panelH;

        // Panel background + border
        g.fill(px, py, px + PANEL_W, py + panelH, C_PANEL);
        drawBorder(g, px, py, PANEL_W, panelH);

        // Header
        g.fill(px, py, px + PANEL_W, py + HEADER_H, C_HEADER);
        g.fill(px, py + HEADER_H - 1, px + PANEL_W, py + HEADER_H, C_BORDER);
        g.drawString(font, "§fQuest Style  §8— " + node.getId().getPath(), px + MARGIN, py + 9, C_TEXT, false);

        int fieldX = px + MARGIN;
        int fieldW = PANEL_W - MARGIN * 2;
        int cy = py + HEADER_H + MARGIN;

        // ── Title ─────────────────────────────────────────────────────────────
        g.drawString(font, "§8TITLE", fieldX, cy, C_TEXT_FAINT, false);
        cy += LABEL_GAP + 8;
        drawField(g, fieldX, cy, fieldW, FIELD_H, titleBox.isFocused());
        if (titleBox != null) {
            titleBox.setX(fieldX);
            titleBox.setY(cy);
            titleBox.setWidth(fieldW);
            titleBox.render(g, mx, my, partial);
        }
        cy += FIELD_H + 10;

        // ── Description ───────────────────────────────────────────────────────
        // Clickable preview only — click opens the full rich-text editor (QuestTextInputScreen),
        // which has the actual color-picker/format-code tools; this box just previews the result.
        g.drawString(font, "§8DESCRIPTION  §7(click to edit — rich text)", fieldX, cy, C_TEXT_FAINT, false);
        cy += LABEL_GAP + 8;
        descPreviewX = fieldX;
        descPreviewY = cy;
        descPreviewW = fieldW;
        descPreviewH = DESC_PREVIEW_H;
        boolean descHov = mx >= descPreviewX && mx < descPreviewX + descPreviewW &&
                my >= descPreviewY && my < descPreviewY + descPreviewH;
        drawField(g, descPreviewX, descPreviewY, descPreviewW, descPreviewH, descHov);
        g.enableScissor(descPreviewX + 2, descPreviewY + 2, descPreviewX + descPreviewW - 2,
                descPreviewY + descPreviewH - 2);
        if (descRaw != null && !descRaw.isBlank()) {
            List<net.phoenixvine.chronicles.client.rich.RichSpan> spans = net.phoenixvine.chronicles.client.rich.ChronicleTextParser
                    .parse(descRaw);
            net.phoenixvine.chronicles.client.rich.ChronicleRichTextRenderer.render(
                    g, font, spans, descPreviewX + 4, descPreviewY + 4, descPreviewW - 8, 0,
                    descPreviewY + 2, descPreviewY + descPreviewH - 2,
                    net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().getTextScaleMultiplier());
        } else {
            g.drawString(font, "§8Click to add a description…", descPreviewX + 4, descPreviewY + 4,
                    C_TEXT_FAINT, false);
        }
        g.disableScissor();
        cy += descPreviewH + 10;

        // ── Dev notes ─────────────────────────────────────────────────────────
        g.drawString(font, "§8DEV NOTES  §7(internal, not shown to players)", fieldX, cy, C_TEXT_FAINT, false);
        cy += LABEL_GAP + 8;
        drawField(g, fieldX, cy, fieldW, FIELD_H, devNotesBox != null && devNotesBox.isFocused());
        if (devNotesBox != null) {
            devNotesBox.setX(fieldX);
            devNotesBox.setY(cy);
            devNotesBox.setWidth(fieldW);
            devNotesBox.render(g, mx, my, partial);
        }
        cy += FIELD_H + 12;

        // ── Shape picker ──────────────────────────────────────────────────────
        g.drawString(font, "§8SHAPE", fieldX, cy, C_TEXT_FAINT, false);
        cy += LABEL_GAP + 8;
        int sx = fieldX;
        int buttonW = 58;
        for (int i = 0; i < SHAPES.length; i++) {
            boolean sel = SHAPES[i].equals(selectedShape);
            boolean hov = mx >= sx && mx < sx + buttonW && my >= cy && my < cy + 18;
            if (hov) hovShapeIdx = i;
            int bg = sel ? 0xFF1A1A30 : (hov ? 0xFF14141E : 0xFF0F0F18);
            int border = sel ? C_ACTIVE : (hov ? C_BORDER : 0xFF252530);
            g.fill(sx, cy, sx + buttonW, cy + 18, bg);
            drawBorder(g, sx, cy, buttonW, 18);
            if (sel) g.fill(sx, cy + 17, sx + buttonW, cy + 18, C_ACTIVE);
            g.drawCenteredString(font, (sel ? "§b" : "§7") + SHAPE_GLYPHS[i] + " " + titleCase(SHAPES[i]),
                    sx + buttonW / 2, cy + 5, sel ? C_ACTIVE : C_TEXT_DIM);
            sx += buttonW + 3;
        }
        cy += 18 + 10;

        // ── Node size ─────────────────────────────────────────────────────────
        g.drawString(font, "§8NODE SIZE", fieldX, cy, C_TEXT_FAINT, false);
        cy += LABEL_GAP + 8;
        QuestNode.NodeSize[] sizes = QuestNode.NodeSize.values();
        sx = fieldX;
        int sizeW = 56;
        for (QuestNode.NodeSize sz : sizes) {
            boolean sel = sz == selectedSize;
            boolean hov = mx >= sx && mx < sx + sizeW && my >= cy && my < cy + 18;
            int bg = sel ? 0xFF1A1A30 : (hov ? 0xFF14141E : 0xFF0F0F18);
            g.fill(sx, cy, sx + sizeW, cy + 18, bg);
            drawBorder(g, sx, cy, sizeW, 18);
            if (sel) g.fill(sx, cy + 17, sx + sizeW, cy + 18, C_ACTIVE);
            g.drawCenteredString(font, (sel ? "§b" : "§7") + titleCase(sz.name()),
                    sx + sizeW / 2, cy + 5, sel ? C_ACTIVE : C_TEXT_DIM);
            sx += sizeW + 3;
        }
        cy += 18 + 10;

        // ── Footer ────────────────────────────────────────────────────────────
        int footerY = py + panelH - FOOTER_H;
        g.fill(px, footerY, px + PANEL_W, py + panelH, C_HEADER);
        g.fill(px, footerY, px + PANEL_W, footerY + 1, C_BORDER);

        // Feedback message
        if (feedbackMsg != null && System.currentTimeMillis() < feedbackUntil) {
            g.drawCenteredString(font, feedbackMsg, px + PANEL_W / 2, footerY + 9, C_DONE);
        }

        // Save button
        saveX = px + PANEL_W - MARGIN - BTN_W;
        saveY = footerY + (FOOTER_H - BTN_H) / 2;
        hovSaveBtn = (mx >= saveX && mx < saveX + BTN_W && my >= saveY && my < saveY + BTN_H) ? 1 : 0;
        g.fill(saveX, saveY, saveX + BTN_W, saveY + BTN_H, hovSaveBtn == 1 ? 0xFF1A3A1A : 0xFF101C10);
        drawBorder(g, saveX, saveY, BTN_W, BTN_H);
        g.drawCenteredString(font, hovSaveBtn == 1 ? "§a✔ Save" : "§7✔ Save", saveX + BTN_W / 2, saveY + 3,
                hovSaveBtn == 1 ? C_DONE : C_TEXT_DIM);

        // Cancel button
        cancelX = saveX - BTN_W - 6;
        cancelY = saveY;
        hovCancelBtn = (mx >= cancelX && mx < cancelX + BTN_W && my >= cancelY && my < cancelY + BTN_H) ? 1 : 0;
        g.fill(cancelX, cancelY, cancelX + BTN_W, cancelY + BTN_H, hovCancelBtn == 1 ? 0xFF2A1010 : 0xFF14100F);
        drawBorder(g, cancelX, cancelY, BTN_W, BTN_H);
        g.drawCenteredString(font, hovCancelBtn == 1 ? "§c✕ Cancel" : "§7✕ Cancel", cancelX + BTN_W / 2, cancelY + 3,
                hovCancelBtn == 1 ? 0xFFFF6666 : C_TEXT_DIM);

        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        // Click outside the panel → close, same as Escape
        if (mx < panelBoundsX || mx >= panelBoundsX + PANEL_W || my < panelBoundsY ||
                my >= panelBoundsY + panelBoundsH) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }

        // Description preview → open the full rich-text editor
        if (mx >= descPreviewX && mx < descPreviewX + descPreviewW &&
                my >= descPreviewY && my < descPreviewY + descPreviewH) {
            if (minecraft != null) {
                minecraft.setScreen(new QuestTextInputScreen(this, "Description", descRaw, 8192,
                        v -> descRaw = v));
            }
            return true;
        }

        // Shape buttons
        if (hovShapeIdx >= 0 && hovShapeIdx < SHAPES.length) {
            if ("CUSTOM".equals(SHAPES[hovShapeIdx])) {
                if (minecraft != null) minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                    selectedShape = "CUSTOM";
                    selectedShapeTexture = rl;
                }));
            } else {
                selectedShape = SHAPES[hovShapeIdx];
            }
            return true;
        }

        // Size buttons
        int px = (width - PANEL_W) / 2;
        // Recalculate size button bounds from current render position
        // (easiest: just check y range and x position)
        QuestNode.NodeSize[] sizes = QuestNode.NodeSize.values();
        int sizeW = 56;
        int fieldX = px + MARGIN;
        // We need to find sizeY - use the same layout calc as render
        // Since it's complex to recompute cy, instead track it via field positions:
        // Just check if any size button was hovered using the stored hovSaveBtn logic
        // For simplicity, re-derive from widget positions (titleBox.y known)
        if (titleBox != null) {
            int cy = titleBox.getY() + FIELD_H + 10;
            // skip desc label + preview box (fixed height now, unlike the old EditBox+preview combo)
            cy += LABEL_GAP + 8 + DESC_PREVIEW_H + 10;
            // skip dev notes label + box
            cy += LABEL_GAP + 8 + FIELD_H + 12;
            // skip shape label + buttons
            cy += LABEL_GAP + 8 + 18 + 10;
            // now at size label
            cy += LABEL_GAP + 8;
            int sx = fieldX;
            for (QuestNode.NodeSize sz : sizes) {
                if (mx >= sx && mx < sx + sizeW && my >= cy && my < cy + 18) {
                    selectedSize = sz;
                    sizeChanged = true;
                    return true;
                }
                sx += sizeW + 3;
            }
        }

        // Save
        if (hovSaveBtn == 1) {
            applyAndSave();
            return true;
        }
        // Cancel
        if (hovCancelBtn == 1) {
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

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // ── Apply & save ─────────────────────────────────────────────────────────

    private void applyAndSave() {
        // Apply in-memory
        String newTitle = titleBox != null ? titleBox.getValue().trim() : node.getTitleRaw().getString();
        String newDesc = descRaw != null ? descRaw.trim() : node.getDescriptionRaw().getString();
        String newNotes = devNotesBox != null ? devNotesBox.getValue().trim() : node.getDevNotes();

        if (!newTitle.isEmpty())
            node.setTitle(net.minecraft.network.chat.Component.literal(newTitle));
        node.setDescription(net.minecraft.network.chat.Component.literal(newDesc));
        node.setShapeType(selectedShape);
        node.setShapeTexture(selectedShapeTexture);
        if (sizeChanged) node.setNodeSize(selectedSize);
        node.setDevNotes(newNotes);

        // Persist to disk
        QuestFileSaver.saveAllQuestsToDisk();

        feedbackMsg = "§aSaved!";
        feedbackUntil = System.currentTimeMillis() + 2000;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawField(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        g.fill(x, y, x + w, y + h, 0xFF0A0A12);
        int border = focused ? C_ACTIVE : C_BORDER;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        ChroniclesUIKit.drawBorder(g, x, y, w, h, C_BORDER);
    }

    private static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
