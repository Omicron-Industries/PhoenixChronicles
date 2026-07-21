package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Function;

/**
 * Shared chrome for popup/modal-style screens (CategoryThemeScreen, ChroniclesThemeEditorScreen,
 * FluidPickerScreen, ItemPickerScreen, TaskRewardEditorScreen, etc).
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

    /**
     * Standard opaque backdrop behind a modal panel. Used to be a 0x70000000 translucent dim over
     * the still-rendered parent screen, which repeatedly read as "bleeding from parent" against a
     * busy quest canvas (same complaint independently made about the item/fluid pickers) - fully
     * opaque instead, matching the pickers' own fix.
     */
    public static void drawScrim(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, ChroniclesThemePalette.BG);
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
        // Same missing-flush bug as drawDropdown()/renderCtxMenu() had: every caller pushes an
        // elevated z-translate before calling this, but that translate isn't actually "in effect"
        // for the depth test until something flushes - without it, quest node icons underneath
        // (z=100 via g.renderItem()) could still win against this panel's own fills and bleed
        // through what's supposed to be a fully opaque modal on top of them.
        g.flush();
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
     * pattern duplicated in CategoryThemeScreen and others.
     *
     * @param labelFn       maps an item to its display label (selection marker is prepended automatically)
     * @param selectedIndex index currently selected, or -1 for none
     * @return the row index the mouse is currently hovering, or -1
     */
    public static <T> int drawDropdown(GuiGraphics g, Font font, List<T> items, Function<T, String> labelFn,
                                       int selectedIndex, int x, int y, int w, int rowH,
                                       int mouseX, int mouseY) {
        int dropH = items.size() * rowH;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        // Callers already flush before invoking this, but that only guarantees whatever THEY
        // queued is submitted - it says nothing about this method's own translate actually being
        // in effect before ITS first draw call. Every other elevated-z overlay in this codebase
        // pairs its translate with an explicit flush immediately after for exactly that reason
        // (this pack's GuiGraphics batching has repeatedly been shown to need one at every layer
        // boundary, not just upstream ones) - this shared dropdown was missing its own, which
        // read as "the dropdown looks transparent" whenever a quest node icon (written to the
        // depth buffer at z=100 via g.renderItem()) sat underneath it.
        g.flush();

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

        g.flush();
        g.pose().popPose();
        return hoveredRow;
    }

    // ---- Fluid icons ----------------------------------------------------------

    /**
     * Draws a fluid's actual still-texture icon, tinted per its own IClientFluidTypeExtensions -
     * every call site in this pack used to just fill a flat square with the tint color instead,
     * which happened to look right for water (whose tint IS a meaningful blue) but wrong for lava
     * and plenty of modded fluids: their tint is white/no-op (0xFFFFFFFF) because their actual
     * color comes from the texture itself, not a tint - a flat white square instead of orange/red
     * lava. Falls back to a flat tint-colored square only if the texture can't be resolved at all.
     */
    public static void drawFluidIcon(GuiGraphics g, net.minecraft.world.level.material.Fluid fluid, int x, int y,
                                     int size) {
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return;
        net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions ext = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
                .of(fluid);
        int tint = ext.getTintColor();
        net.minecraft.resources.ResourceLocation stillTexture = ext.getStillTexture();
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = stillTexture == null ? null :
                net.minecraft.client.Minecraft.getInstance()
                        .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                        .apply(stillTexture);
        if (sprite == null) {
            g.fill(x, y, x + size, y + size, tint | 0xFF000000);
            return;
        }
        float a = ((tint >>> 24) & 0xFF) / 255f;
        float r = ((tint >> 16) & 0xFF) / 255f;
        float gr = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, gr, b, a == 0f ? 1f : a);
        g.blit(x, y, 0, size, size, sprite);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // ---- Scaled text --------------------------------------------------------

    /**
     * Draws text at an arbitrary scale around its top-left corner - backs the "Text Scale"
     * setting (QuestChroniclesSettings#getTextScaleMultiplier). scale == 1.0f is a plain
     * g.drawString() call (no pose-stack cost for the common case). x/y are floats since a
     * scaled draw's origin isn't necessarily an integer pixel once other scaled text has been
     * laid out before it.
     */
    public static void drawScaledString(GuiGraphics g, Font font, String text, float x, float y, int color,
                                        float scale) {
        if (scale == 1.0f) {
            g.drawString(font, text, (int) x, (int) y, color, false);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Same as {@link #drawScaledString} but centered horizontally on centerX, like g.drawCenteredString(). */
    public static void drawScaledCenteredString(GuiGraphics g, Font font, String text, float centerX, float y,
                                                int color, float scale) {
        if (scale == 1.0f) {
            g.drawCenteredString(font, text, (int) centerX, (int) y, color);
            return;
        }
        float w = font.width(text) * scale;
        g.pose().pushPose();
        g.pose().translate(centerX - w / 2f, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
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
