package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;

import net.phoenixvine.chronicles.client.rich.MultilineTextArea;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class QuestTextInputScreen extends Screen {

    private static final int C_ACCENT = 0xFF00AA55;
    private static final int C_BTN = 0xFF1A1A24;
    private static final int C_BTN_HOV = 0xFF22222E;
    private static final int C_GREEN = 0xFF1A2A1A;

    private static final char[] COLOR_CODES = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'r'
    };
    private static final int[] COLOR_VALUES = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF,
            0xFFFFFFFF
    };

    private static final String[] MD_LABELS = { "B", "I", "H", "-", "[]", "T", "R", "PB" };
    private static final String[] MD_INSERTS = {
            "**", "*", "# ", "- ", "[](url)", "[](tip:msg)", "{reset}", "\n---\n"
    };
    private static final String[] MD_TIPS = {
            "Bold (**text**)",
            "Italic (*text*)",
            "Heading (# text)",
            "Unordered List (- text)",
            "Link ([label](url))",
            "Tooltip ([label](tip:msg))",
            "Reset Formatting ({reset})",
            "Page Break (---) - splits into pages in-game instead of scrolling"
    };

    private final Screen parent;
    private final String fieldLabel;
    private final int maxLength;
    private final Consumer<String> onConfirm;
    private final String initial;

    private MultilineTextArea inputBox;
    private EditBox hexBox;

    private int pw, ph, px, py, btnY;

    public QuestTextInputScreen(Screen parent, String fieldLabel, String initial, int maxLength,
                                Consumer<String> onConfirm) {
        super(Component.literal(fieldLabel));
        this.parent = parent;
        this.fieldLabel = fieldLabel;
        this.initial = initial;
        this.maxLength = maxLength;
        this.onConfirm = onConfirm;
        inputBox = null;
        hexBox = null;
    }

    @Override
    protected void init() {
        super.init();

        this.pw = Math.min(900, width - 80);
        this.ph = Math.min(700, height - 80);
        this.px = (width - pw) / 2;
        this.py = (height - ph) / 2;
        this.btnY = py + ph - 24;

        inputBox = addRenderableWidget(new MultilineTextArea(font, px + 8, py + 26, pw - 16, ph - 86, maxLength));
        inputBox.setValue(initial);
        setInitialFocus(inputBox);

        int hexY = btnY - 36;
        hexBox = new EditBox(font, px + 8 + font.width("Hex: "), hexY, 58, 12, Component.empty());
        hexBox.setMaxLength(7);
        hexBox.setHint(Component.literal("§8#RRGGBB"));
        addRenderableWidget(hexBox);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, ChroniclesThemePalette.BG);

        g.fill(px, py, px + pw, py + ph, ChroniclesThemePalette.PANEL);
        ChroniclesUIKit.drawBorder(g, px, py, pw, ph, ChroniclesThemePalette.BORDER);

        g.fill(px + 1, py, px + pw - 1, py + 2, C_ACCENT);

        g.drawCenteredString(font, "§f" + fieldLabel, px + pw / 2, py + 7, ChroniclesThemePalette.TEXT);

        int used = inputBox.getValue().length();
        boolean atCap = used >= maxLength;
        g.drawString(font, (atCap ? "§c" : "§8") + used + " / " + maxLength,
                px + pw - 8 - font.width(used + " / " + maxLength), py + 7, ChroniclesThemePalette.TEXT_DIM, false);

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

        int insX = px + 8 + font.width("Hex: ") + 60;
        drawBtn(g, mx, my, insX, rowY - 1, 40, 14, "§7insert", C_BTN);

        String hexVal = hexBox.getValue().trim();
        if (hexVal.startsWith("#") && hexVal.length() == 7) {
            String hexDigits = hexVal.substring(1);

            if (hexDigits.matches("^[0-9a-fA-F]{6}$")) {
                int col = (int) Long.parseLong(hexDigits, 16) | 0xFF000000;
                g.fill(insX + 44, rowY, insX + 58, rowY + 12, col);
            }
        }
        g.drawString(font, "§8{#RRGGBB} syntax: no & needed", insX + 60, rowY + 2, ChroniclesThemePalette.TEXT_DIM,
                false);
    }

    private void renderFormatButtons(GuiGraphics g, int mx, int my) {
        int btnW = 18, gap = 2;
        int totalW = MD_LABELS.length * (btnW + gap) - gap;
        int startX = px + pw - 8 - totalW;
        int rowY = btnY - 22;
        for (int i = 0; i < MD_LABELS.length; i++) {
            int bx = startX + i * (btnW + gap);
            boolean hov = mx >= bx && mx < bx + btnW && my >= rowY && my < rowY + 12;
            g.fill(bx, rowY, bx + btnW, rowY + 12, hov ? C_BTN_HOV : C_BTN);
            if (hov) {
                g.fill(bx, rowY, bx + btnW, rowY + 1, C_ACCENT);
                g.renderTooltip(font, Component.literal(MD_TIPS[i]), mx, my);
            }

            String displayLabel = MD_LABELS[i];
            if (i == 0) displayLabel = "§l" + displayLabel;
            else if (i == 1) displayLabel = "§o" + displayLabel;

            g.drawCenteredString(font, displayLabel, bx + btnW / 2, rowY + 2, hov ? 0xFFFFFFFF : 0xFFAAAAAA);
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

        if (mx >= px + 6 && mx < px + 6 + half && my >= btnY && my < btnY + 16) {
            confirm();
            return true;
        }

        if (mx >= px + (double) pw / 2 + 3 && mx < px + pw - 3 && my >= btnY && my < btnY + 16) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }

        int hexRowY = btnY - 36;
        int insX = px + 8 + font.width("Hex: ") + 60;
        if (mx >= insX && mx < insX + 40 && my >= hexRowY - 1 && my < hexRowY + 13) {
            String hexVal = hexBox.getValue().trim();
            if (!hexVal.startsWith("#")) hexVal = "#" + hexVal;
            if (hexVal.length() == 7) {
                setInitialFocus(inputBox);
                inputBox.forceInsert("{" + hexVal.toUpperCase() + "}");
            }
            return true;
        }

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

        int fBtnW = 18, fGap = 2;
        int totalW = MD_LABELS.length * (fBtnW + fGap) - fGap;
        int fStartX = px + pw - 8 - totalW;
        int fRowY = btnY - 22;
        for (int i = 0; i < MD_LABELS.length; i++) {
            int bx = fStartX + i * (fBtnW + fGap);
            if (mx >= bx && mx < bx + fBtnW && my >= fRowY && my < fRowY + 12) {
                setInitialFocus(inputBox);
                inputBox.forceInsert(MD_INSERTS[i]);
                return true;
            }
        }

        if (mx < px || mx >= px + pw || my < py || my >= py + ph) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inputBox.scrollBy(delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    private void confirm() {
        try {
            onConfirm.accept(inputBox.getValue());
        } catch (Exception e) {
            PhoenixChronicles.LOGGER.error("[Phoenix Chronicles] Error confirming text edit: ", e);
        } finally {
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
