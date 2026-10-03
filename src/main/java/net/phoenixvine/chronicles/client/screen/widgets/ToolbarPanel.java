package net.phoenixvine.chronicles.client.screen.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.screen.utils.GraphEditorState.EditorTool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ToolbarPanel {

    private static final String[] FILTER_KEYS = { "ALL", "AVAILABLE", "ACTIVE", "COMPLETE", "LOCKED" };
    private static final String[] FILTER_GLYPHS = { "◉", "○", "◑", "✔", "🔒" };
    private static final String[] FILTER_TIPS = {
            "Show all quests", "Show available quests", "Show quests in progress", "Show completed quests",
            "Show locked quests"
    };

    private static final String G_FIT = "⊞", G_CONNECT = "🔗", G_PLACE = "✛", G_SELECT = "⬚", G_SETTINGS = "⚙",
            G_WIKI = "?", G_HIDE = "⊘", G_MAP = "⊡", G_STATS = "∑";
    private static final int MIN_BTN_W = 18;
    private static final int[] FILTER_COLORS = {
            0xFFAAAAAA,
            0xFF55BBFF,
            0xFFFFBB33,
            0xFF44CC88,
            0xFF666688,
    };

    public record Colors(int panelDark, int border, int text, int textDim) {}

    private final Map<String, int[]> btnBounds = new HashMap<>();

    public void render(@NotNull GuiGraphics g, @NotNull Font font, int mx, int my, int width, int cl, int cr,
                       int toolbarY, int toolbarH,
                       @NotNull Colors colors, @NotNull String stateFilter, boolean hideCompleted, boolean minimapOpen,
                       boolean statsOpen, boolean devMode, @NotNull EditorTool activeTool,
                       @NotNull Consumer<Runnable> deferDraw) {
        int ty = toolbarY;
        g.fill(0, ty, width, ty + toolbarH, colors.panelDark());
        g.fill(0, ty + toolbarH - 1, width, ty + toolbarH, colors.border());

        int rightClusterW = rightClusterWidth(font, devMode);
        drawFilterPills(g, font, mx, my, cl, toolbarY, toolbarH, stateFilter, cr - rightClusterW, deferDraw);

        int rx = cr - 4;
        rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, G_FIT, "fit", "Fit all quests to view", deferDraw);
        rx -= 2;
        if (devMode) {
            rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors,
                    (activeTool == EditorTool.CONNECT ? "§a" : "§8") + G_CONNECT, "toolConnect",
                    "Connect mode: drag between two quests to link them, no Alt needed", deferDraw);
            rx -= 2;
            rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors,
                    (activeTool == EditorTool.PLACE ? "§a" : "§8") + G_PLACE, "toolPlace",
                    "Place mode: click empty canvas to drop a bare quest, stays active for more", deferDraw);
            rx -= 2;
            rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors,
                    (activeTool == EditorTool.SELECT ? "§a" : "§8") + G_SELECT, "toolSelect",
                    "Select mode: the normal click/drag/multi-select behavior", deferDraw);
            rx -= 2;
        }
        rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, G_SETTINGS, "settings", "Settings", deferDraw);
        if (devMode) {
            rx -= 2;
            rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, G_WIKI, "wiki", "Open dev wiki", deferDraw);
        }
        rx -= 2;

        rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, (hideCompleted ? "§a" : "§8") + G_HIDE, "hideDone",
                hideCompleted ? "Completed quests hidden (click to show)" : "Hide completed quests from the canvas",
                deferDraw);
        rx -= 2;

        rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, (minimapOpen ? "§a" : "§8") + G_MAP, "map",
                "Toggle minimap", deferDraw);
        rx -= 2;

        rx = drawBtnR(g, font, mx, my, rx, ty, toolbarH, colors, (statsOpen ? "§a" : "§8") + G_STATS, "stats",
                "Quest completion by chapter", deferDraw);
        rx -= 2;

        if (devMode) {
            String devLabel = "DEV";
            int dbx = rx - font.width(devLabel) - 12;
            g.fill(dbx, ty + 4, dbx + font.width(devLabel) + 8, ty + toolbarH - 4, 0x221a0d26);
            g.fill(dbx, ty + toolbarH - 4, dbx + font.width(devLabel) + 8, ty + toolbarH - 3, 0xFF9955CC);
            g.drawString(font, "§5" + devLabel, dbx + 4, ty + 4, 0xFF9955CC, false);
        }
    }

    private static int btnW(Font font, String glyph) {
        return Math.max(MIN_BTN_W, font.width(glyph) + 10);
    }

    private int rightClusterWidth(@NotNull Font font, boolean devMode) {
        int w = 4;
        w += btnW(font, G_FIT) + 2;
        if (devMode) {
            w += btnW(font, G_CONNECT) + 2;
            w += btnW(font, G_PLACE) + 2;
            w += btnW(font, G_SELECT) + 2;
        }
        w += btnW(font, G_SETTINGS) + 2;
        if (devMode) w += btnW(font, G_WIKI) + 2;
        w += btnW(font, G_HIDE) + 2;
        w += btnW(font, G_MAP) + 2;
        w += btnW(font, G_STATS) + 2;
        if (devMode) w += font.width("DEV") + 8 + 12;
        return w;
    }

    private static int pillW(Font font, String glyph) {
        return Math.max(MIN_BTN_W, font.width(glyph) + 8);
    }

    private void drawFilterPills(@NotNull GuiGraphics g, @NotNull Font font, int mx, int my, int cl, int toolbarY,
                                 int toolbarH,
                                 @NotNull String stateFilter, int maxX, @NotNull Consumer<Runnable> deferDraw) {
        int px = cl + 4;
        int py = toolbarY + 2;
        int ph = toolbarH - 4;

        for (int i = 0; i < FILTER_KEYS.length; i++) {
            String label = FILTER_GLYPHS[i];
            int pw = pillW(font, label);
            if (px + pw > maxX) break;

            boolean sel = stateFilter.equals(FILTER_KEYS[i]);
            boolean hov = mx >= px && mx < px + pw && my >= py && my < py + ph;

            int bg = sel ? (FILTER_COLORS[i] & 0x00FFFFFF | 0x33000000) : (hov ? 0x22FFFFFF : 0x00000000);
            if (bg != 0) g.fill(px, py, px + pw, py + ph, bg);

            if (sel) g.fill(px, py + ph - 1, px + pw, py + ph, FILTER_COLORS[i]);

            int col = sel ? FILTER_COLORS[i] : (hov ? 0xFFCCCCCC : 0xFF666677);
            g.drawString(font, label, px + (pw - font.width(label)) / 2, py + 2, col, false);

            if (hov) {
                final String tip = FILTER_TIPS[i];
                deferDraw.accept(() -> g.renderTooltip(font, Component.literal("§7" + tip), mx, my));
            }

            px += pw + 4;
        }
    }

    public int[][] filterPillBounds(int cl, int cr, int toolbarY, int toolbarH, @NotNull Font font, boolean devMode) {
        int maxX = cr - rightClusterWidth(font, devMode);
        int px = cl + 4;
        int py = toolbarY + 2, ph = toolbarH - 4;
        int[][] bounds = new int[FILTER_KEYS.length][4];
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            String label = FILTER_GLYPHS[i];
            int pw = pillW(font, label);
            if (px + pw > maxX) {
                bounds[i] = new int[] { 0, 0, 0, 0 };
                continue;
            }
            bounds[i] = new int[] { px, py, px + pw, py + ph };
            px += pw + 4;
        }
        return bounds;
    }

    public @NotNull String filterKey(int index) {
        return FILTER_KEYS[index];
    }

    public int filterKeyCount() {
        return FILTER_KEYS.length;
    }

    private int drawBtnR(@NotNull GuiGraphics g, @NotNull Font font, int mx, int my, int rx, int ty, int toolbarH,
                         @NotNull Colors colors,
                         @NotNull String label, String key, @Nullable String tooltip,
                         @NotNull Consumer<Runnable> deferDraw) {
        int labelW = font.width(label.replaceAll("§.", ""));
        int tw = Math.max(MIN_BTN_W, labelW + 10);

        int th = toolbarH - 4;
        int bx = rx - tw, by = ty + 2;
        boolean hov = mx >= bx && mx < bx + tw && my >= by && my < by + th;
        if (hov) g.fill(bx, by, bx + tw, by + th, 0x22FFFFFF);
        g.drawString(font, label, bx + (tw - labelW) / 2, by + 2, hov ? colors.text() : colors.textDim(), false);
        btnBounds.put(key, new int[] { bx, by, bx + tw, by + th });
        if (hov && tooltip != null) {
            deferDraw.accept(() -> g.renderTooltip(font, Component.literal("§7" + tooltip), mx, my));
        }
        return bx - 2;
    }

    public boolean hits(String key, double mx, double my) {
        int[] b = btnBounds.get(key);
        return b != null && mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
    }
}
