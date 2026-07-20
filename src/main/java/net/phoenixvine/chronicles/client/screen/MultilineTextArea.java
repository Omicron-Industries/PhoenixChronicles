package net.phoenixvine.chronicles.client.screen;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Real multi-line text editing widget: Enter inserts an actual newline (vanilla EditBox has no
 * concept of one), word-wraps as you type, and preserves blank lines as genuine paragraph breaks
 * instead of collapsing them. Extracted out of QuestTextInputScreen's former private inner class
 * so LangEditorScreen's description fields (previously a single-line EditBox with no way to type
 * a newline at all) can share the exact same behavior instead of a second, divergent copy.
 */
public class MultilineTextArea extends AbstractWidget {

    private static final int C_ACCENT = 0xFF00AA55;
    private static final int C_SEL_FILL = 0x552255FF;
    private static final int C_SEL_OUTLINE = 0xFF2255FF;
    private static final int C_HOVER_FILL = 0x33AAAAFF;
    private static final int C_HOVER_OUTLINE = 0x88AAAAFF;

    private final Font font;
    private final MultilineTextField textField;
    private final int maxLength;
    private final List<LinePos> lines = new ArrayList<>();
    private int hoverWordStart = -1;
    private int hoverWordEnd = -1;
    private int scrollLines = 0;
    private int lastCursorForScroll = -1;
    private Consumer<String> responder;

    public MultilineTextArea(Font font, int x, int y, int w, int h, int maxLength) {
        super(x, y, w, h, Component.empty());
        this.font = font;
        this.maxLength = maxLength;
        this.textField = new MultilineTextField(font, w - 12);
        this.textField.setCharacterLimit(maxLength);
    }

    public void setValue(String v) {
        if (v != null) {
            v = v.replace("\r\n", "\n").replace("\r", "\n");
        }
        textField.setValue(v == null ? "" : v);
    }

    public String getValue() {
        return textField.value();
    }

    /** Called with the new value on every edit (insert/delete/newline) - mirrors EditBox's setResponder. */
    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    private void fireChanged() {
        if (responder != null) responder.accept(getValue());
    }

    /** Inserts text at the cursor (or over the current selection), bypassing chat-character filtering. */
    public void forceInsert(String text) {
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
            textField.seekCursor(Whence.ABSOLUTE, start + text.length());
            fireChanged();
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
        String disp = full.replace('§', '&');

        lines.clear();
        if (disp.isEmpty()) {
            lines.add(new LinePos(0, 0, ""));
        } else {
            int currentIndex = 0;
            String[] rawLines = disp.split("\n", -1);

            for (String rawLine : rawLines) {
                if (rawLine.isEmpty()) {
                    lines.add(new LinePos(currentIndex, currentIndex, ""));
                } else {
                    final int lineStart = currentIndex;
                    font.getSplitter().splitLines(rawLine, width - 12, Style.EMPTY, false,
                            (style, s, e) -> {
                                int globalStart = lineStart + s;
                                int globalEnd = lineStart + e;
                                lines.add(new LinePos(globalStart, globalEnd, disp.substring(globalStart, globalEnd)));
                            });
                }
                currentIndex += rawLine.length() + 1; // +1 accounts for the split \n character
            }
        }

        // -6 accounts for the top padding (textY = getY() + 6) - without it, this claimed one
        // more line fit than the scissor actually had room for, so the scroll-into-view math
        // let the cursor sit on a line that was only ever half-visible above the clip edge.
        int visibleLines = Math.max(1, (height - 6) / 9);
        int cursorLine = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (cursor >= lines.get(i).start && cursor <= lines.get(i).end) {
                cursorLine = i;
                break;
            }
        }
        // Only snap the view back to the cursor when the cursor itself just moved (typing,
        // clicking to reposition) - this used to re-run on EVERY render frame regardless of
        // cause, so scrolling away from wherever the cursor happened to be (e.g. up to the top,
        // with the cursor still sitting at the end from the last edit) got fought back to the
        // cursor's line before the very next frame, making manual scroll effectively impossible
        // once you'd typed anything.
        if (cursor != lastCursorForScroll) {
            if (cursorLine < scrollLines) scrollLines = cursorLine;
            if (cursorLine >= scrollLines + visibleLines) scrollLines = cursorLine - visibleLines + 1;
            lastCursorForScroll = cursor;
        }
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        scrollLines = Math.max(0, Math.min(scrollLines, maxScroll));

        updateHoverWord(mx, my, textX, textY, disp);

        g.enableScissor(getX(), getY(), getX() + width, getY() + height);

        // Hover Highlight Rendering
        if (!textField.hasSelection() && hoverWordStart >= 0 && hoverWordEnd > hoverWordStart) {
            for (int i = 0; i < lines.size(); i++) {
                LinePos line = lines.get(i);
                int lineY = textY + (i - scrollLines) * 9;
                if (hoverWordEnd > line.start && hoverWordStart < line.end) {
                    int a = Math.max(hoverWordStart, line.start) - line.start;
                    int b = Math.min(hoverWordEnd, line.end) - line.start;
                    int x1 = textX + font.width(line.text.substring(0, a));
                    int x2 = textX + font.width(line.text.substring(0, b));
                    g.fill(x1, lineY, x2, lineY + 9, C_HOVER_FILL);
                    g.fill(x1, lineY, x2, lineY + 1, C_HOVER_OUTLINE);
                    g.fill(x1, lineY + 8, x2, lineY + 9, C_HOVER_OUTLINE);
                    g.fill(x1, lineY, x1 + 1, lineY + 9, C_HOVER_OUTLINE);
                    g.fill(x2 - 1, lineY, x2, lineY + 9, C_HOVER_OUTLINE);
                }
            }
        }

        // Selection Highlight Rendering
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
                    int x1 = textX + font.width(line.text.substring(0, a));
                    int x2 = textX + font.width(line.text.substring(0, b));
                    g.fill(x1, lineY, x2, lineY + 9, C_SEL_FILL);
                    g.fill(x1, lineY, x2, lineY + 1, C_SEL_OUTLINE);
                    g.fill(x1, lineY + 8, x2, lineY + 9, C_SEL_OUTLINE);
                    g.fill(x1, lineY, x1 + 1, lineY + 9, C_SEL_OUTLINE);
                    g.fill(x2 - 1, lineY, x2, lineY + 9, C_SEL_OUTLINE);
                }
            }
        }

        // Text & Caret Rendering
        for (int i = 0; i < lines.size(); i++) {
            LinePos line = lines.get(i);
            int lineY = textY + (i - scrollLines) * 9;
            // Skip lines that would only be PARTIALLY visible (bottom edge past the scissor) -
            // previously this let a half-cut line render at the bottom whenever height wasn't an
            // exact multiple of the 9px line height, which read as clipped/broken rather than as
            // "scroll down for more".
            if (lineY < getY() || lineY + 9 > getY() + height) continue;
            g.drawString(font, line.text, textX, lineY, 0xFFFFFFFF, false);
            if (isFocused() && cursor >= line.start && cursor <= line.end) {
                if ((System.currentTimeMillis() / 530) % 2 == 0) {
                    int off = cursor - line.start;
                    String sub = line.text.substring(0, Math.min(off, line.text.length()));
                    int cx = textX + font.width(sub);
                    g.fill(cx, lineY, cx + 1, lineY + 9, C_ACCENT);
                }
            }
        }
        g.disableScissor();

        // Scrollbar - mouse-wheel scrolling already worked with no visual indicator at all, which
        // reads as "this box doesn't scroll" until you happen to try the wheel over it. Only
        // drawn once there's actually more content than fits.
        int sbVisLines = Math.max(1, (height - 6) / 9);
        int sbMaxScroll = Math.max(0, lines.size() - sbVisLines);
        if (sbMaxScroll > 0) {
            int trackX = getX() + width - 3;
            int trackTop = getY() + 2, trackBot = getY() + height - 2, trackH = trackBot - trackTop;
            g.fill(trackX, trackTop, trackX + 2, trackBot, 0x33FFFFFF);
            int thumbH = Math.max(10, trackH * sbVisLines / (sbVisLines + sbMaxScroll));
            int thumbY = trackTop + (int) ((long) scrollLines * (trackH - thumbH) / sbMaxScroll);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x99CCCCCC);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < getX() || mx >= getX() + width || my < getY() || my >= getY() + height) return false;
        return scrollBy(delta);
    }

    /**
     * Scrolls regardless of mouse position - for host screens (like QuestTextInputScreen) where
     * this box is the only meaningfully scrollable thing on the whole screen, so the wheel should
     * work no matter where the cursor happens to be hovering, not just directly over the box.
     */
    public boolean scrollBy(double delta) {
        int visibleLines = Math.max(1, (height - 6) / 9);
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        scrollLines = Math.max(0, Math.min(maxScroll, scrollLines - (int) Math.signum(delta)));
        return true;
    }

    private void updateHoverWord(int mx, int my, int textX, int textY, String disp) {
        hoverWordStart = -1;
        hoverWordEnd = -1;
        if (mx < getX() || mx >= getX() + width || my < getY() || my >= getY() + height) return;
        int lineIdx = Math.max(0, Math.min((int) ((my - textY) / 9) + scrollLines, lines.size() - 1));
        if (lineIdx < 0 || lineIdx >= lines.size()) return;
        LinePos line = lines.get(lineIdx);
        int localX = mx - textX;
        int offset = 0;
        while (offset < line.text.length()) {
            if (font.width(line.text.substring(0, offset + 1)) > localX) break;
            offset++;
        }
        int absPos = line.start + offset;
        if (absPos >= disp.length()) return;
        int ws = absPos;
        while (ws > 0 && !Character.isWhitespace(disp.charAt(ws - 1))) ws--;
        int we = absPos;
        while (we < disp.length() && !Character.isWhitespace(disp.charAt(we))) we++;
        if (we > ws) {
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
                int rawOffset = 0;
                while (rawOffset < line.text.length()) {
                    if (font.width(line.text.substring(0, rawOffset + 1)) > localX) break;
                    rawOffset++;
                }
                textField.seekCursor(Whence.ABSOLUTE, line.start + rawOffset);
            }
            return true;
        }
        setFocused(false);
        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (!isFocused()) return false;

        // Bypasses vanilla's chat character filter to successfully insert a newline
        if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
            this.forceInsert("\n");
            return true;
        }
        if (textField.keyPressed(kc)) {
            fireChanged();
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (isFocused() && SharedConstants.isAllowedChatCharacter(ch)) {
            textField.insertText(Character.toString(ch));
            fireChanged();
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
