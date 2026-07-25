package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Function;

public final class ChroniclesUIKit {

    private ChroniclesUIKit() {}

    public static void drawScrim(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, ChroniclesThemePalette.BG);
    }

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        drawBorder(g, x, y, w, h, ChroniclesThemePalette.BORDER);
    }

    public static void drawModalChrome(GuiGraphics g, Font font, int screenW, int screenH,
                                       int panelX, int panelY, int panelW, int panelH, int headerH,
                                       String title, int panelColor, int headerColor, int borderColor,
                                       int textColor) {
        g.flush();
        drawScrim(g, screenW, screenH);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, panelColor);
        drawBorder(g, panelX, panelY, panelW, panelH, borderColor);

        g.fill(panelX, panelY, panelX + panelW, panelY + headerH, headerColor);
        g.fill(panelX, panelY + headerH - 1, panelX + panelW, panelY + headerH, borderColor);
        g.drawCenteredString(font, title, panelX + panelW / 2,
                panelY + (headerH / 2) - (font.lineHeight / 2), textColor);
    }

    public static void drawModalChrome(GuiGraphics g, Font font, int screenW, int screenH,
                                       int panelX, int panelY, int panelW, int panelH, int headerH,
                                       String title) {
        drawModalChrome(g, font, screenW, screenH, panelX, panelY, panelW, panelH, headerH, title,
                ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ChroniclesThemePalette.BORDER, ChroniclesThemePalette.TEXT);
    }

    public static <T> int drawDropdown(GuiGraphics g, Font font, List<T> items, Function<T, String> labelFn,
                                       int selectedIndex, int x, int y, int w, int rowH,
                                       int mouseX, int mouseY) {
        int dropH = items.size() * rowH;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

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
        try {
            g.blit(x, y, 0, size, size, sprite);
        } finally {

            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

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

    public static int parseHexColor(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return (int) Long.parseLong(value.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String formatHexColor(int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }
}
