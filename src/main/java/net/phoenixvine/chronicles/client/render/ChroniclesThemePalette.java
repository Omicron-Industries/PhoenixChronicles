package net.phoenixvine.chronicles.client.render;

import net.phoenixvine.chronicles.registry.ChroniclesTheme;

public class ChroniclesThemePalette {

    public static int BG, PANEL, PANEL_DARK, HEADER, BORDER, BORDER_LIT, SEL_TAB, SEL_ACCENT;

    public static int TEXT, TEXT_DIM, TEXT_FAINT, TEXT_DONE, TEXT_ACT, PROG_FILL;

    public static int NODE_LOCKED, NODE_UNLOCKED, NODE_ACTIVE, NODE_DONE;
    public static int NBORD_LOCKED, NBORD_UNLOCKED, NBORD_ACTIVE, NBORD_DONE, NBORD_DEV;

    public static void refresh(ChroniclesTheme t) {
        BG = t.bg.getColor();
        PANEL = t.panel.getColor();
        PANEL_DARK = t.header.getColor();
        HEADER = t.header.getColor();
        BORDER = t.border.getColor();
        BORDER_LIT = t.accent.getColor();
        SEL_TAB = t.panel.getColor();
        SEL_ACCENT = t.accent.getColor();

        TEXT = t.text.getColor();
        TEXT_DIM = t.textDim.getColor();
        TEXT_FAINT = t.textFaint.getColor();
        TEXT_DONE = t.done.getColor();
        TEXT_ACT = t.activeColor.getColor();
        PROG_FILL = t.accent.getColor();

        int bg = t.bg.getColor();
        NODE_LOCKED = blendColor(bg, t.locked.getColor(), 0.18f);
        NODE_UNLOCKED = blendColor(bg, t.border.getColor(), 0.35f);
        NODE_ACTIVE = blendColor(bg, t.activeColor.getColor(), 0.22f);
        NODE_DONE = blendColor(bg, t.done.getColor(), 0.18f);

        NBORD_LOCKED = blendColor(t.locked.getColor(), 0xFF000000, 0.25f);
        NBORD_UNLOCKED = blendColor(t.border.getColor(), 0xFFFFFFFF, 0.15f);
        NBORD_ACTIVE = t.activeColor.getColor();
        NBORD_DONE = t.done.getColor();
        NBORD_DEV = blendColor(t.accent.getColor(), 0xFFCC44FF, 0.5f);
    }

    private static int blendColor(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

