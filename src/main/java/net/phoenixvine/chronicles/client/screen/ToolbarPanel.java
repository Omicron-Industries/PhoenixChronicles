package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

class ToolbarPanel {

    private static final String[] FILTER_KEYS = { "ALL", "AVAILABLE", "ACTIVE", "COMPLETE", "LOCKED" };
    private static final String[] FILTER_GLYPHS = { "◉", "○", "◑", "✔", "🔒" };
    private static final int[] FILTER_COLORS = {
            0xFFAAAAAA,
            0xFF55BBFF,
            0xFFFFBB33,
            0xFF44CC88,
            0xFF666688,
    };

    record Colors(int panelDark, int border, int text, int textDim) {}

    private final Map<String, int[]> btnBounds = new HashMap<>();

    void render(GuiGraphics g, Font font, int mx, int my, int width, int cl, int cr, int toolbarY, int toolbarH,
                Colors colors, String stateFilter, boolean hideCompleted, boolean minimapOpen, boolean devMode) {
        int ty = toolbarY;
        g.fill(0, ty, width, ty + toolbarH, colors.panelDark());
        g.fill(0, ty + toolbarH - 1, width, ty + toolbarH, colors.border());

        drawFilterPills(g, font, mx, my, cl, toolbarY, toolbarH, stateFilter);

        int rx = cr - 4;
        rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, "⊞ Fit", "fit");
        rx -= 2;
        rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, "⚙", "settings");
        if (devMode) {
            rx -= 2;
            rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, "?", "wiki");
        }
        rx -= 2;

        String hideLabel = hideCompleted ? "§a✔ Hide done" : "§8✔ Hide done";
        rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, hideLabel, "hideDone");
        rx -= 2;

        String mmLabel = minimapOpen ? "§a⊡ Map" : "§8⊡ Map";
        rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, mmLabel, "map");
        rx -= 2;

        if (devMode) {
            String devLabel = "DEV";
            int dbx = rx - font.width(devLabel) - 12;
            g.fill(dbx, ty + 4, dbx + font.width(devLabel) + 8, ty + toolbarH - 4, 0x221a0d26);
            g.fill(dbx, ty + toolbarH - 4, dbx + font.width(devLabel) + 8, ty + toolbarH - 3, 0xFF9955CC);
            g.drawString(font, "§5" + devLabel, dbx + 4, ty + 4, 0xFF9955CC, false);
        }
    }

    private void drawFilterPills(GuiGraphics g, Font font, int mx, int my, int cl, int toolbarY, int toolbarH,
                                 String stateFilter) {
        int px = cl + 4;
        int py = toolbarY + 2;
        int ph = toolbarH - 4;

        for (int i = 0; i < FILTER_KEYS.length; i++) {
            boolean sel = stateFilter.equals(FILTER_KEYS[i]);
            String label = FILTER_GLYPHS[i] + " " +
                    (FILTER_KEYS[i].charAt(0) + FILTER_KEYS[i].substring(1).toLowerCase());
            int pw = font.width(label) + 8;
            boolean hov = mx >= px && mx < px + pw && my >= py && my < py + ph;

            int bg = sel ? (FILTER_COLORS[i] & 0x00FFFFFF | 0x33000000) : (hov ? 0x22FFFFFF : 0x00000000);
            if (bg != 0) g.fill(px, py, px + pw, py + ph, bg);

            if (sel) g.fill(px, py + ph - 1, px + pw, py + ph, FILTER_COLORS[i]);

            int col = sel ? FILTER_COLORS[i] : (hov ? 0xFFCCCCCC : 0xFF666677);
            g.drawString(font, label, px + 4, py + 2, col, false);

            px += pw + 4;
        }
    }

    int[][] filterPillBounds(int cl, int toolbarY, int toolbarH, Font font) {
        int px = cl + 4;
        int py = toolbarY + 2, ph = toolbarH - 4;
        int[][] bounds = new int[FILTER_KEYS.length][4];
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            String label = FILTER_GLYPHS[i] + " " +
                    (FILTER_KEYS[i].charAt(0) + FILTER_KEYS[i].substring(1).toLowerCase());
            int pw = font.width(label) + 8;
            bounds[i] = new int[] { px, py, px + pw, py + ph };
            px += pw + 4;
        }
        return bounds;
    }

    String filterKey(int index) {
        return FILTER_KEYS[index];
    }

    int filterKeyCount() {
        return FILTER_KEYS.length;
    }

    private int drawBtnR(GuiGraphics g, Font font, int mx, int my, int rx, int ty, int toolbarH, Colors colors,
                         String label, String key) {
        int tw = font.width(label.replaceAll("§.", "")) + 10;
        int th = toolbarH - 8;
        int bx = rx - tw, by = ty + 4;
        boolean hov = mx >= bx && mx < bx + tw && my >= by && my < by + th;
        if (hov) g.fill(bx, by, bx + tw, by + th, 0x22FFFFFF);
        g.drawString(font, label, bx + 5, by + 3, hov ? colors.text() : colors.textDim(), false);
        btnBounds.put(key, new int[] { bx, by, bx + tw, by + th });
        return bx - 2;
    }

    boolean hits(String key, double mx, double my) {
        int[] b = btnBounds.get(key);
        return b != null && mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
    }
}
