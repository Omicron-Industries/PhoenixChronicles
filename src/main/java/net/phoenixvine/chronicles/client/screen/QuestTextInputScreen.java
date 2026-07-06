package net.phoenixvine.chronicles.client.screen;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class QuestTextInputScreen extends Screen {

    // Panel/border/text come from ChroniclesThemePalette; widget-local accents stay here.
    private static final int C_ACCENT = 0xFF00AA55;
    private static final int C_BTN = 0xFF1A1A24;
    private static final int C_BTN_HOV = 0xFF22222E;
    private static final int C_GREEN = 0xFF1A2A1A;

    // ── Standard Minecraft color codes ────────────────────────────────────────
    private static final char[] COLOR_CODES = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'r'
    };
    private static final int[] COLOR_VALUES = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF,
            0xFFFFFFFF
    };

    // ── Format codes (bold, italic, underline, strikethrough, obfuscated, reset) ──
    private static final char[] FORMAT_CODES = { 'l', 'o', 'n', 'm', 'k', 'r' };
    private static final String[] FORMAT_LABELS = { "§lB", "§oI", "§nU", "§mS", "§k?", "§rR" };
    private static final String[] FORMAT_TIPS = { "Bold (§l)", "Italic (§o)", "Underline (§n)", "Strikethrough (§m)",
            "Obfuscated (§k)", "Reset (§r)" };

    private final Screen parent;
    private final String fieldLabel;
    private final int maxLength;
    private final Consumer<String> onConfirm;
    private final String initial;

    private CustomTextArea inputBox;
    private EditBox hexBox;  // for {#RRGGBB} hex color input

    private int pw, ph, px, py, btnY;

    public QuestTextInputScreen(Screen parent, String fieldLabel, String initial, int maxLength,
                                Consumer<String> onConfirm) {
        super(Component.literal(fieldLabel));
        this.parent = parent;
        this.fieldLabel = fieldLabel;
        this.initial = initial != null ? initial : "";
        this.maxLength = maxLength;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        super.init();
        this.pw = Math.min(460, width - 40);
        this.ph = 190;
        this.px = (width - pw) / 2;
        this.py = (height - ph) / 2;
        this.btnY = py + ph - 24;

        inputBox = addRenderableWidget(new CustomTextArea(px + 8, py + 26, pw - 16, ph - 86, Component.empty()));
        inputBox.setValue(initial);
        setInitialFocus(inputBox);

        // Hex color box (sits above the color picker row)
        int hexY = btnY - 36;
        hexBox = new EditBox(font, px + 8 + font.width("Hex: "), hexY, 58, 12, Component.empty());
        hexBox.setMaxLength(7);
        hexBox.setHint(Component.literal("§8#RRGGBB"));
        addRenderableWidget(hexBox);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // Render just the quest graph backdrop — no widgets, no scissors.
        // Walk up the parent chain to find ChronicleOverviewScreen; using full
        // parent.render() causes widget bleed because those screens' EditBoxes
        // and buttons draw at their absolute positions into our panel area.
        ChronicleOverviewScreen overview = findOverview();
        if (overview != null) {
            overview.renderBackdrop(g);
        } else {
            g.fill(0, 0, width, height, 0xFF0B0B0F);
        }
        // Dark overlay so the panel reads clearly over the canvas
        g.fill(0, 0, width, height, 0xBB000000);

        // Panel
        g.fill(px, py, px + pw, py + ph, ChroniclesThemePalette.PANEL);
        ChroniclesUIKit.drawBorder(g, px, py, pw, ph, ChroniclesThemePalette.BORDER);
        // Accent top stripe
        g.fill(px + 1, py, px + pw - 1, py + 2, C_ACCENT);

        // Title
        g.drawCenteredString(font, "§f" + fieldLabel, px + pw / 2, py + 7, ChroniclesThemePalette.TEXT);

        super.render(g, mx, my, partial);

        renderHexRow(g, mx, my);
        renderColorPicker(g, mx, my);
        renderFormatButtons(g, mx, my);

        int half = pw / 2 - 6;
        drawBtn(g, mx, my, px + 6, btnY, half, 16, "§a✓ Confirm", C_GREEN);
        drawBtn(g, mx, my, px + pw / 2 + 3, btnY, half, 16, "§c✕ Cancel", C_BTN);
    }

    private void renderHexRow(GuiGraphics g, int mx, int my) {
        int rowY = btnY - 36;
        g.drawString(font, "§8Hex: ", px + 8, rowY + 2, ChroniclesThemePalette.TEXT_DIM, false);
        // Insert button
        int insX = px + 8 + font.width("Hex: ") + 60;
        drawBtn(g, mx, my, insX, rowY - 1, 40, 14, "§7insert", C_BTN);
        // Live preview swatch
        String hexVal = hexBox != null ? hexBox.getValue().trim() : "";
        if (hexVal.startsWith("#") && hexVal.length() == 7) {
            try {
                int col = (int) Long.parseLong(hexVal.substring(1), 16) | 0xFF000000;
                g.fill(insX + 44, rowY, insX + 58, rowY + 12, col);
            } catch (NumberFormatException ignored) {}
        }
        g.drawString(font, "§8{#RRGGBB} syntax — no & needed", insX + 60, rowY + 2, ChroniclesThemePalette.TEXT_DIM, false);
    }

    private void renderFormatButtons(GuiGraphics g, int mx, int my) {
        int btnW = 18, gap = 2;
        int totalW = FORMAT_CODES.length * (btnW + gap) - gap;
        int startX = px + pw - 8 - totalW;
        int rowY = btnY - 22;
        for (int i = 0; i < FORMAT_CODES.length; i++) {
            int bx = startX + i * (btnW + gap);
            boolean hov = mx >= bx && mx < bx + btnW && my >= rowY && my < rowY + 12;
            g.fill(bx, rowY, bx + btnW, rowY + 12, hov ? C_BTN_HOV : C_BTN);
            if (hov) {
                g.fill(bx, rowY, bx + btnW, rowY + 1, C_ACCENT);
                g.renderTooltip(font, Component.literal(FORMAT_TIPS[i]), mx, my);
            }
            g.drawCenteredString(font, FORMAT_LABELS[i], bx + btnW / 2, rowY + 2, hov ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
    }

    private void renderColorPicker(GuiGraphics g, int mx, int my) {
        String label = "Colors: ";
        int labelW = font.width(label);
        int startX = px + 8;
        int pickerY = btnY - 22;

        g.drawString(font, "§8" + label, startX, pickerY + 2, ChroniclesThemePalette.TEXT_DIM, false);

        int boxX = startX + labelW;
        int size = 11;
        int gap = 2;

        for (int i = 0; i < COLOR_CODES.length; i++) {
            int cx = boxX + i * (size + gap);
            boolean hov = mx >= cx && mx < cx + size && my >= pickerY && my < pickerY + size;
            g.fill(cx, pickerY, cx + size, pickerY + size, COLOR_VALUES[i]);
            g.fill(cx, pickerY, cx + size, pickerY + 1, hov ? C_ACCENT : 0xFF333333);
            g.fill(cx, pickerY + size - 1, cx + size, pickerY + size, hov ? C_ACCENT : 0xFF333333);
            g.fill(cx, pickerY, cx + 1, pickerY + size, hov ? C_ACCENT : 0xFF333333);
            g.fill(cx + size - 1, pickerY, cx + size, pickerY + size, hov ? C_ACCENT : 0xFF333333);
            if (hov) {
                g.renderTooltip(font, Component.literal("§" + COLOR_CODES[i] + "§" + COLOR_CODES[i]), mx, my);
            }
        }
    }

    private void drawBtn(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, int bg) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? C_BTN_HOV : bg);
        if (hov) g.fill(x, y, x + w, y + 1, C_ACCENT);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hov ? C_ACCENT : ChroniclesThemePalette.TEXT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;

        int half = pw / 2 - 6;
        // Confirm
        if (mx >= px + 6 && mx < px + 6 + half && my >= btnY && my < btnY + 16) {
            confirm();
            return true;
        }
        // Cancel
        if (mx >= px + pw / 2 + 3 && mx < px + pw - 3 && my >= btnY && my < btnY + 16) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }

        // Hex insert button
        int hexRowY = btnY - 36;
        int insX = px + 8 + font.width("Hex: ") + 60;
        if (mx >= insX && mx < insX + 40 && my >= hexRowY - 1 && my < hexRowY + 13) {
            String hexVal = hexBox != null ? hexBox.getValue().trim() : "";
            if (!hexVal.startsWith("#")) hexVal = "#" + hexVal;
            if (hexVal.length() == 7) {
                setInitialFocus(inputBox);
                inputBox.forceInsert("{" + hexVal.toUpperCase() + "}");
            }
            return true;
        }

        // Color picker
        String label = "Colors: ";
        int labelW = font.width(label);
        int boxX = px + 8 + labelW;
        int pickerY = btnY - 22;
        int size = 11, gap = 2;
        for (int i = 0; i < COLOR_CODES.length; i++) {
            int cx = boxX + i * (size + gap);
            if (mx >= cx && mx < cx + size && my >= pickerY && my < pickerY + size) {
                setInitialFocus(inputBox);
                inputBox.forceInsert("§" + COLOR_CODES[i]);
                return true;
            }
        }

        // Format buttons
        int fBtnW = 18, fGap = 2;
        int totalW = FORMAT_CODES.length * (fBtnW + fGap) - fGap;
        int fStartX = px + pw - 8 - totalW;
        int fRowY = btnY - 22;
        for (int i = 0; i < FORMAT_CODES.length; i++) {
            int bx = fStartX + i * (fBtnW + fGap);
            if (mx >= bx && mx < bx + fBtnW && my >= fRowY && my < fRowY + 12) {
                setInitialFocus(inputBox);
                inputBox.forceInsert("§" + FORMAT_CODES[i]);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if ((kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) && !hasShiftDown()) {
            confirm();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    private void confirm() {
        onConfirm.accept(inputBox != null ? inputBox.getValue() : initial);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Walks the parent chain to find ChronicleOverviewScreen for backdrop rendering. */
    private ChronicleOverviewScreen findOverview() {
        Screen s = parent;
        while (s != null) {
            if (s instanceof ChronicleOverviewScreen) return (ChronicleOverviewScreen) s;
            try {
                java.lang.reflect.Field f = s.getClass().getDeclaredField("parent");
                f.setAccessible(true);
                Object val = f.get(s);
                s = (val instanceof Screen) ? (Screen) val : null;
            } catch (Exception e) {
                break;
            }
        }
        return null;
    }

    // ── Inner text area ───────────────────────────────────────────────────────

    private class CustomTextArea extends AbstractWidget {

        private static final int C_SEL_FILL = 0x552255FF;
        private static final int C_SEL_OUTLINE = 0xFF2255FF;
        private static final int C_HOVER_FILL = 0x33AAAAFF;
        private static final int C_HOVER_OUTLINE = 0x88AAAAFF;

        private final MultilineTextField textField;
        private final List<LinePos> lines = new ArrayList<>();
        private int hoverLineIdx = -1;
        private int hoverWordStart = -1;
        private int hoverWordEnd = -1;

        CustomTextArea(int x, int y, int w, int h, Component msg) {
            super(x, y, w, h, msg);
            this.textField = new MultilineTextField(QuestTextInputScreen.this.font, w - 12);
            this.textField.setCharacterLimit(maxLength);
        }

        void setValue(String v) {
            textField.setValue(v);
        }

        String getValue() {
            return textField.value();
        }

        void forceInsert(String text) {
            String full = textField.value();
            int cursor = textField.cursor();
            int start = cursor, end = cursor;
            if (textField.hasSelection()) {
                String sel = textField.getSelectedText();
                int idx = full.indexOf(sel);
                if (idx != -1) {
                    start = idx;
                    end = idx + sel.length();
                }
            }
            String updated = full.substring(0, start) + text + full.substring(end);
            if (updated.length() <= maxLength) {
                textField.setValue(updated);
                textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, start + text.length());
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF0A0A10);
            ChroniclesUIKit.drawBorder(g, getX(), getY(), width, height,
                    isFocused() ? C_ACCENT : ChroniclesThemePalette.BORDER);

            int textX = getX() + 6;
            int textY = getY() + 6;
            int cursor = textField.cursor();
            String full = textField.value();

            lines.clear();
            if (full.isEmpty()) {
                lines.add(new LinePos(0, 0, ""));
            } else {
                QuestTextInputScreen.this.font.getSplitter().splitLines(full, width - 12, Style.EMPTY, false,
                        (style, s, e) -> lines.add(new LinePos(s, e, full.substring(s, e))));
                if (full.endsWith("\n")) lines.add(new LinePos(full.length(), full.length(), ""));
            }

            // Recompute hovered word from mouse position
            updateHoverWord(mx, my, textX, textY, full);

            // Hover-word highlight (blue tint, no selection active)
            if (!textField.hasSelection() && hoverWordStart >= 0 && hoverWordEnd > hoverWordStart) {
                for (int i = 0; i < lines.size(); i++) {
                    LinePos line = lines.get(i);
                    int lineY = textY + i * 9;
                    if (hoverWordEnd > line.start && hoverWordStart < line.end) {
                        int a = Math.max(hoverWordStart, line.start) - line.start;
                        int b = Math.min(hoverWordEnd, line.end) - line.start;
                        int x1 = textX + QuestTextInputScreen.this.font.width(line.text.substring(0, a));
                        int x2 = textX + QuestTextInputScreen.this.font.width(line.text.substring(0, b));
                        g.fill(x1, lineY, x2, lineY + 9, C_HOVER_FILL);
                        // outline
                        g.fill(x1, lineY, x2, lineY + 1, C_HOVER_OUTLINE);
                        g.fill(x1, lineY + 8, x2, lineY + 9, C_HOVER_OUTLINE);
                        g.fill(x1, lineY, x1 + 1, lineY + 9, C_HOVER_OUTLINE);
                        g.fill(x2 - 1, lineY, x2, lineY + 9, C_HOVER_OUTLINE);
                    }
                }
            }

            // Selection highlight — blue fill + blue outline
            if (textField.hasSelection()) {
                String sel = textField.getSelectedText();
                int selStart = full.indexOf(sel);
                int selEnd = selStart + sel.length();
                for (int i = 0; i < lines.size(); i++) {
                    LinePos line = lines.get(i);
                    int lineY = textY + i * 9;
                    if (selEnd > line.start && selStart < line.end) {
                        int a = Math.max(selStart, line.start) - line.start;
                        int b = Math.min(selEnd, line.end) - line.start;
                        int x1 = textX + QuestTextInputScreen.this.font.width(line.text.substring(0, a));
                        int x2 = textX + QuestTextInputScreen.this.font.width(line.text.substring(0, b));
                        g.fill(x1, lineY, x2, lineY + 9, C_SEL_FILL);
                        g.fill(x1, lineY, x2, lineY + 1, C_SEL_OUTLINE);
                        g.fill(x1, lineY + 8, x2, lineY + 9, C_SEL_OUTLINE);
                        g.fill(x1, lineY, x1 + 1, lineY + 9, C_SEL_OUTLINE);
                        g.fill(x2 - 1, lineY, x2, lineY + 9, C_SEL_OUTLINE);
                    }
                }
            }

            // Text + caret
            for (int i = 0; i < lines.size(); i++) {
                LinePos line = lines.get(i);
                int lineY = textY + i * 9;
                g.drawString(QuestTextInputScreen.this.font, line.text, textX, lineY, ChroniclesThemePalette.TEXT, false);
                if (isFocused() && cursor >= line.start && cursor <= line.end) {
                    if ((System.currentTimeMillis() / 530) % 2 == 0) {
                        int off = cursor - line.start;
                        String sub = line.text.substring(0, Math.min(off, line.text.length()));
                        int cx = textX + QuestTextInputScreen.this.font.width(sub);
                        g.fill(cx, lineY, cx + 1, lineY + 9, C_ACCENT);
                    }
                }
            }
        }

        private void updateHoverWord(int mx, int my, int textX, int textY, String full) {
            hoverWordStart = -1;
            hoverWordEnd = -1;
            hoverLineIdx = -1;
            if (mx < getX() || mx >= getX() + width || my < getY() || my >= getY() + height) return;
            int lineIdx = Math.max(0, Math.min((int) ((my - textY) / 9), lines.size() - 1));
            if (lineIdx < 0 || lineIdx >= lines.size()) return;
            LinePos line = lines.get(lineIdx);
            int localX = mx - textX;
            // find char offset under mouse
            int offset = 0;
            while (offset < line.text.length()) {
                char ch = line.text.charAt(offset);
                if (ch == 167 && offset + 1 < line.text.length()) {
                    offset += 2;
                    continue;
                }
                if (QuestTextInputScreen.this.font.width(line.text.substring(0, offset + 1)) > localX) break;
                offset++;
            }
            int absPos = line.start + offset;
            if (absPos >= full.length()) return;
            // expand left to word boundary
            int ws = absPos;
            while (ws > 0 && !Character.isWhitespace(full.charAt(ws - 1))) ws--;
            int we = absPos;
            while (we < full.length() && !Character.isWhitespace(full.charAt(we))) we++;
            if (we > ws) {
                hoverLineIdx = lineIdx;
                hoverWordStart = ws;
                hoverWordEnd = we;
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height) {
                setFocused(true);
                if (!lines.isEmpty()) {
                    int lineIdx = Math.max(0, Math.min((int) ((my - (getY() + 6)) / 9), lines.size() - 1));
                    LinePos line = lines.get(lineIdx);
                    int localX = (int) (mx - (getX() + 6));
                    int rawOffset = 0;
                    while (rawOffset < line.text.length()) {
                        // skip formatting codes without comparing the § char directly
                        char ch = line.text.charAt(rawOffset);
                        if (ch == 167 && rawOffset + 1 < line.text.length()) { // 167 == section sign §
                            rawOffset += 2;
                            continue;
                        }
                        if (QuestTextInputScreen.this.font.width(line.text.substring(0, rawOffset + 1)) > localX) break;
                        rawOffset++;
                    }
                    textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, line.start + rawOffset);
                }
                return true;
            }
            setFocused(false);
            return false;
        }

        @Override
        public boolean keyPressed(int kc, int sc, int mod) {
            if (!isFocused()) return false;
            if ((kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) && hasShiftDown()) {
                textField.insertText("\n");
                return true;
            }
            return textField.keyPressed(kc) || super.keyPressed(kc, sc, mod);
        }

        @Override
        public boolean charTyped(char ch, int mods) {
            if (isFocused() && SharedConstants.isAllowedChatCharacter(ch)) {
                textField.insertText(Character.toString(ch));
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}

        private static class LinePos {

            final int start, end;
            final String text;

            LinePos(int start, int end, String text) {
                this.start = start;
                this.end = end;
                this.text = text;
            }
        }
    }
}
