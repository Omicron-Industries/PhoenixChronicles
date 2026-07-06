package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Function;

/**
 * Shared chrome for popup/modal-style screens (CategoryThemeScreen, ChroniclesThemeEditorScreen,
 * FluidPickerScreen, ItemPickerScreen, QuestStyleEditorScreen, TaskRewardEditorScreen, etc).
 *
 * This sits next to ChroniclesThemeRenderer (which owns full-screen header/footer/scrollbar/
 * code-block chrome) and ChroniclesThemePalette (which owns resolved theme colors). ChroniclesUIKit
 * owns the "floating panel over a dimmed parent screen" pattern that ~8 screens currently
 * hand-roll with slightly different constants and a copy-pasted drawBorder().
 *
 * Colors are read from ChroniclesThemePalette by default so screens stop hardcoding their own
 * C_PANEL / C_BORDER / C_TEXT constants — but every method also has an overload that takes
 * explicit colors, for the handful of screens (like CategoryThemeScreen) that intentionally use
 * an accent color distinct from the global theme.
 */
public final class ChroniclesUIKit {

    private ChroniclesUIKit() {}

    // ---- Scrim ----------------------------------------------------------

    /** Standard semi-transparent dimming behind a modal panel. */
    public static void drawScrim(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, 0x70000000);
    }

    // ---- Borders ----------------------------------------------------------

    /** The one canonical 1px box border. Replaces the 8 duplicate private drawBorder() methods. */
    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Overload using ChroniclesThemePalette.BORDER. */
    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        drawBorder(g, x, y, w, h, ChroniclesThemePalette.BORDER);
    }

    // ---- Panel + header (the modal chrome pattern) -------------------------

    /**
     * Draws the standard modal sequence: scrim over the parent, panel fill + border,
     * header bar + bottom border + centered title. Returns nothing — caller draws content
     * starting below the header (headerH is the same value passed in).
     */
    public static void drawModalChrome(GuiGraphics g, Font font, int screenW, int screenH,
                                       int panelX, int panelY, int panelW, int panelH, int headerH,
                                       String title, int panelColor, int headerColor, int borderColor,
                                       int textColor) {
        drawScrim(g, screenW, screenH);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, panelColor);
        drawBorder(g, panelX, panelY, panelW, panelH, borderColor);

        g.fill(panelX, panelY, panelX + panelW, panelY + headerH, headerColor);
        g.fill(panelX, panelY + headerH - 1, panelX + panelW, panelY + headerH, borderColor);
        g.drawCenteredString(font, title, panelX + panelW / 2,
                panelY + (headerH / 2) - (font.lineHeight / 2), textColor);
    }

    /** Overload pulling all colors from ChroniclesThemePalette — use this unless the screen needs a custom accent. */
    public static void drawModalChrome(GuiGraphics g, Font font, int screenW, int screenH,
                                       int panelX, int panelY, int panelW, int panelH, int headerH,
                                       String title) {
        drawModalChrome(g, font, screenW, screenH, panelX, panelY, panelW, panelH, headerH, title,
                ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ChroniclesThemePalette.BORDER, ChroniclesThemePalette.TEXT);
    }

    // ---- Dropdown lists ----------------------------------------------------

    /**
     * Renders a generic hover-highlighted dropdown list under an anchor point.
     * Handles the "elevated z + panel + border + per-row hover highlight + selected marker"
     * pattern duplicated in CategoryThemeScreen, QuestStyleEditorScreen and others.
     *
     * @param labelFn maps an item to its display label (selection marker is prepended automatically)
     * @param selectedIndex index currently selected, or -1 for none
     * @return the row index the mouse is currently hovering, or -1
     */
    public static <T> int drawDropdown(GuiGraphics g, Font font, List<T> items, Function<T, String> labelFn,
                                       int selectedIndex, int x, int y, int w, int rowH,
                                       int mouseX, int mouseY) {
        int dropH = items.size() * rowH;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        g.fill(x, y, x + w, y + dropH, ChroniclesThemePalette.PANEL);
        drawBorder(g, x, y, w, dropH, ChroniclesThemePalette.BORDER);

        int hoveredRow = -1;
        for (int i = 0; i < items.size(); i++) {
            int rowY = y + i * rowH;
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + rowH;
            if (hovered) {
                g.fill(x + 1, rowY, x + w - 1, rowY + rowH, 0xFF1E1E2A);
                hoveredRow = i;
            }
            String marker = (i == selectedIndex) ? "§a● §7" : "§8  §7";
            g.drawString(font, marker + labelFn.apply(items.get(i)), x + 6, rowY + (rowH - font.lineHeight) / 2,
                    hovered ? ChroniclesThemePalette.TEXT : ChroniclesThemePalette.TEXT_DIM);
        }

        g.pose().popPose();
        return hoveredRow;
    }

    // ---- Color parsing ------------------------------------------------------

    /**
     * Parses a "#RRGGBB" / "RRGGBB" string into an RGB int, or returns fallback on failure/blank.
     * Replaces the repeated try/catch(Long.parseLong(...)) blocks scattered across color-picker fields.
     */
    public static int parseHexColor(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return (int) Long.parseLong(value.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Formats an RGB int (no alpha) back to "#RRGGBB" for populating an EditBox. */
    public static String formatHexColor(int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }
}