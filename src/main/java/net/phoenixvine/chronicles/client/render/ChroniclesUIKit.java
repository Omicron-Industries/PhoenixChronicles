package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;

public final class ChroniclesUIKit {

    private ChroniclesUIKit() {}

    public static Component lit(String raw) {
        return Component.literal(ChroniclesThemePalette.adapt(raw));
    }

    private static void drawText(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        if (text.indexOf('<') >= 0) {
            logTaggedText(text);
            g.drawString(font, taggedComponent(text), x, y, color, shadow);
        } else {
            g.drawString(font, text, x, y, color, shadow);
        }
    }

    private static Component taggedComponent(String text) {
        net.minecraft.network.chat.Style lead = net.minecraft.network.chat.Style.EMPTY;
        int i = 0;
        while (i + 1 < text.length() && text.charAt(i) == '§') {

            if (text.charAt(i + 1) == '[' || net.phoenixvine.chronicles.client.util.ChromaticCodes
                    .isCode(text.charAt(i + 1)))
                break;
            net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(text.charAt(i + 1));
            if (fmt == null) break;
            lead = fmt == net.minecraft.ChatFormatting.RESET ? net.minecraft.network.chat.Style.EMPTY :
                    lead.applyLegacyFormat(fmt);
            i += 2;
        }
        return i == 0 ? Component.literal(text) : Component.literal(text.substring(i)).withStyle(lead);
    }

    private static final java.util.Set<String> LOGGED_TAGGED = new java.util.HashSet<>();

    private static void logTaggedText(String text) {
        net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
        String where = screen != null ? screen.getClass().getSimpleName() : "no screen";
        if (LOGGED_TAGGED.size() < 200 && LOGGED_TAGGED.add(where + "|" + text)) {
            net.phoenixvine.chronicles.PhoenixChronicles.LOGGER.info("[EffectText] {} draws tagged text: {}", where,
                    text.replace('§', '&'));
        }
    }

    public static void drawString(GuiGraphics g, Font font, String text, int x, int y, int color) {
        drawText(g, font, ChroniclesThemePalette.adapt(text), x, y, color, true);
    }

    public static void drawString(GuiGraphics g, Font font, String text, int x, int y, int color,
                                  boolean dropShadow) {
        drawText(g, font, ChroniclesThemePalette.adapt(text), x, y, color, dropShadow);
    }

    public static void drawString(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color);
    }

    public static void drawString(GuiGraphics g, Font font, Component text, int x, int y, int color,
                                  boolean dropShadow) {
        g.drawString(font, text, x, y, color, dropShadow);
    }

    public static void drawCenteredString(GuiGraphics g, Font font, String text, int x, int y, int color) {
        String adapted = ChroniclesThemePalette.adapt(text);
        drawText(g, font, adapted, x - net.phoenixvine.chronicles.client.util.EffectText.width(font, adapted) / 2, y,
                color, true);
    }

    public static void drawCenteredString(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawCenteredString(font, text, x, y, color);
    }

    public static void drawString(GuiGraphics g, Font font, net.minecraft.util.FormattedCharSequence text, int x,
                                  int y, int color) {
        g.drawString(font, text, x, y, color);
    }

    public static void drawString(GuiGraphics g, Font font, net.minecraft.util.FormattedCharSequence text, int x,
                                  int y, int color, boolean dropShadow) {
        g.drawString(font, text, x, y, color, dropShadow);
    }

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

    public static String fitText(Font font, String text, int maxW) {
        return net.phoenixvine.chronicles.client.util.EffectText.width(font, text) > maxW ?
                net.phoenixvine.chronicles.client.util.EffectText.cut(font, text, Math.max(0, maxW - 4)) + "…" : text;
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
        String fitTitle = fitText(font, title, panelW - 12);
        g.drawCenteredString(font, fitTitle, panelX + panelW / 2,
                panelY + (headerH / 2) - (font.lineHeight / 2), textColor);
    }

    public static void drawModalChrome(GuiGraphics g, Font font, int screenW, int screenH,
                                       int panelX, int panelY, int panelW, int panelH, int headerH,
                                       String title) {
        drawModalChrome(g, font, screenW, screenH, panelX, panelY, panelW, panelH, headerH, title,
                ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ChroniclesThemePalette.BORDER, ChroniclesThemePalette.TEXT);
    }

    private static final int SECTION_HEADER_BG = 0xFF1A1A26;
    private static final int SECTION_HEADER_BG_HOV = 0xFF20202E;

    public static void drawSectionHeader(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
                                         boolean collapsed, String summary, int mouseX, int mouseY,
                                         int textColor, int textDimColor) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hov ? SECTION_HEADER_BG_HOV : SECTION_HEADER_BG);
        String chevron = collapsed ? "▶" : "▼";
        drawString(g, font, "§f" + chevron + " §f" + label, x + 4, y + (h - 8) / 2, textColor, false);
        if (collapsed && summary != null && !summary.isEmpty()) {
            int sw = font.width(summary);
            g.drawString(font, summary, x + w - 4 - sw, y + (h - 8) / 2, textDimColor, false);
        }
    }

    public static void drawShaderWarning(GuiGraphics g, Font font, net.minecraft.client.gui.components.EditBox box,
                                         boolean show) {
        if (!show) return;
        int wx = box.getX() + box.getWidth() - 10;
        int wy = box.getY() + (box.getHeight() - 8) / 2;
        g.drawString(font, "§c⚠", wx, wy, 0xFFFF5555, false);
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
            drawString(g, font, marker + labelFn.apply(items.get(i)), x + 6, rowY + (rowH - font.lineHeight) / 2,
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
        text = net.phoenixvine.chronicles.client.util.EffectText.clean(text);
        if (scale == 1.0f) {
            drawText(g, font, text, (int) x, (int) y, color, false);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        drawText(g, font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    public static void drawScaledCenteredString(GuiGraphics g, Font font, String text, float centerX, float y,
                                                int color, float scale) {
        text = net.phoenixvine.chronicles.client.util.EffectText.clean(text);
        if (scale == 1.0f) {
            int w1 = net.phoenixvine.chronicles.client.util.EffectText.width(font, text);
            drawText(g, font, text, (int) centerX - w1 / 2, (int) y, color, true);
            return;
        }
        float w = net.phoenixvine.chronicles.client.util.EffectText.width(font, text) * scale;
        g.pose().pushPose();
        g.pose().translate(centerX - w / 2f, y, 0);
        g.pose().scale(scale, scale, 1f);
        drawText(g, font, text, 0, 0, color, false);
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
