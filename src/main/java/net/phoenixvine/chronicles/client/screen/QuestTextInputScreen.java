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
        // Fully opaque background of its own - a dim over the still-rendered quest canvas kept
        // reading as "bleeding from parent" (same complaint independently made about the item/
        // fluid pickers, fixed the same way there).
        g.fill(0, 0, width, height, ChroniclesThemePalette.BG);

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
        g.drawString(font, "§8{#RRGGBB} syntax — no & needed", insX + 60, rowY + 2, ChroniclesThemePalette.TEXT_DIM,
                false);
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

        // Click outside the panel → cancel, same as Escape (discards unsaved edits)
        if (mx < px || mx >= px + pw || my < py || my >= py + ph) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        // Enter used to confirm/close here (when Shift wasn't held), which intercepted the key
        // before it ever reached the focused CustomTextArea - that's why plain Enter closed the
        // screen instead of inserting a newline, and Shift+Enter was needed just to type one.
        // Confirming is the Confirm button's job only; Enter is now left alone so it falls
        // through to super.keyPressed() -> the focused widget (CustomTextArea inserts a newline,
        // hexBox does whatever EditBox normally does with Enter).
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
        /**
         * First visible line index - there used to be no scrolling at all here (every line drew
         * unconditionally, unclipped, straight past the box's bottom edge with no way to reach
         * anything past the fixed height), which is what "only shows some of the text" on a long
         * description meant in practice: everything past the box's height was simply unreachable.
         */
        private int scrollLines = 0;

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
            // This box is a raw/source view, not the rendered quest text - format codes should
            // show up as literal visible characters (e.g. "&c") in plain white, not get
            // interpreted into actual colors/bold/etc. Only the real quest viewer
            // (ChronicleRichTextRenderer) should ever turn these into formatting. Swapping the
            // real section sign for '&' is a 1-for-1 character replace, so every index computed
            // against this string below still lines up with the underlying EditBox's real cursor
            // positions.
            String full = textField.value();
            String disp = full.replace('§', '&');

            lines.clear();
            if (disp.isEmpty()) {
                lines.add(new LinePos(0, 0, ""));
            } else {
                QuestTextInputScreen.this.font.getSplitter().splitLines(disp, width - 12, Style.EMPTY, false,
                        (style, s, e) -> lines.add(new LinePos(s, e, disp.substring(s, e))));
                if (disp.endsWith("\n")) lines.add(new LinePos(disp.length(), disp.length(), ""));
            }

            // Auto-scroll to keep the cursor's line in view, then clamp so scrolling can't go
            // past either end - this used to not exist at all: every line drew unconditionally
            // with no clipping, so anything past the box's fixed height was simply unreachable,
            // which read as "only shows some of the text" on anything longer than ~8 lines.
            int visibleLines = Math.max(1, height / 9);
            int cursorLine = 0;
            for (int i = 0; i < lines.size(); i++) {
                if (cursor >= lines.get(i).start && cursor <= lines.get(i).end) {
                    cursorLine = i;
                    break;
                }
            }
            if (cursorLine < scrollLines) scrollLines = cursorLine;
            if (cursorLine >= scrollLines + visibleLines) scrollLines = cursorLine - visibleLines + 1;
            int maxScroll = Math.max(0, lines.size() - visibleLines);
            scrollLines = Math.max(0, Math.min(scrollLines, maxScroll));

            // Recompute hovered word from mouse position
            updateHoverWord(mx, my, textX, textY, disp);

            g.enableScissor(getX(), getY(), getX() + width, getY() + height);

            // Hover-word highlight (blue tint, no selection active)
            if (!textField.hasSelection() && hoverWordStart >= 0 && hoverWordEnd > hoverWordStart) {
                for (int i = 0; i < lines.size(); i++) {
                    LinePos line = lines.get(i);
                    int lineY = textY + (i - scrollLines) * 9;
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
                String sel = textField.getSelectedText().replace('§', '&');
                int selStart = disp.indexOf(sel);
                int selEnd = selStart + sel.length();
                for (int i = 0; i < lines.size(); i++) {
                    LinePos line = lines.get(i);
                    int lineY = textY + (i - scrollLines) * 9;
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
                int lineY = textY + (i - scrollLines) * 9;
                if (lineY + 9 < getY() || lineY > getY() + height) continue; // scissor-culled anyway, skip the draw
                // call
                // Plain literal draw, no formatting interpretation - line.text already has its
                // real § codes swapped for visible '&' above, so there's nothing left for
                // Minecraft's font renderer to interpret; it just draws as one flat white color.
                g.drawString(QuestTextInputScreen.this.font, line.text, textX, lineY, 0xFFFFFFFF, false);
                if (isFocused() && cursor >= line.start && cursor <= line.end) {
                    if ((System.currentTimeMillis() / 530) % 2 == 0) {
                        int off = cursor - line.start;
                        String sub = line.text.substring(0, Math.min(off, line.text.length()));
                        int cx = textX + QuestTextInputScreen.this.font.width(sub);
                        g.fill(cx, lineY, cx + 1, lineY + 9, C_ACCENT);
                    }
                }
            }
            g.disableScissor();
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            if (mx < getX() || mx >= getX() + width || my < getY() || my >= getY() + height) return false;
            int visibleLines = Math.max(1, height / 9);
            int maxScroll = Math.max(0, lines.size() - visibleLines);
            scrollLines = Math.max(0, Math.min(maxScroll, scrollLines - (int) Math.signum(delta)));
            return true;
        }

        private void updateHoverWord(int mx, int my, int textX, int textY, String disp) {
            hoverWordStart = -1;
            hoverWordEnd = -1;
            hoverLineIdx = -1;
            if (mx < getX() || mx >= getX() + width || my < getY() || my >= getY() + height) return;
            int lineIdx = Math.max(0, Math.min((int) ((my - textY) / 9) + scrollLines, lines.size() - 1));
            if (lineIdx < 0 || lineIdx >= lines.size()) return;
            LinePos line = lines.get(lineIdx);
            int localX = mx - textX;
            // find char offset under mouse - line.text is the display string (real § already
            // swapped for a plain visible '&'), so every character is measured as a normal glyph.
            int offset = 0;
            while (offset < line.text.length()) {
                if (QuestTextInputScreen.this.font.width(line.text.substring(0, offset + 1)) > localX) break;
                offset++;
            }
            int absPos = line.start + offset;
            if (absPos >= disp.length()) return;
            // expand left to word boundary
            int ws = absPos;
            while (ws > 0 && !Character.isWhitespace(disp.charAt(ws - 1))) ws--;
            int we = absPos;
            while (we < disp.length() && !Character.isWhitespace(disp.charAt(we))) we++;
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
                    int lineIdx = Math.max(0,
                            Math.min((int) ((my - (getY() + 6)) / 9) + scrollLines, lines.size() - 1));
                    LinePos line = lines.get(lineIdx);
                    int localX = (int) (mx - (getX() + 6));
                    // line.text is the display string (real § already swapped for a plain visible
                    // '&'), so every character is measured as a normal glyph - no more control
                    // codes to skip over.
                    int rawOffset = 0;
                    while (rawOffset < line.text.length()) {
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
            if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
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
