package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;

public class ChroniclesThemeRenderer {

    public static void drawHeader(GuiGraphics g, int width, int heightLimit, String titleText, ChroniclesTheme theme) {
        Font font = Minecraft.getInstance().font;
        int headerColor = theme.header.getColor();
        int borderColor = theme.border.getColor();
        int textColor = theme.text.getColor();

        g.fill(0, 0, width, heightLimit, headerColor);
        g.fill(0, heightLimit - 1, width, heightLimit, borderColor);
        g.drawCenteredString(font, titleText, width / 2, (heightLimit / 2) - (font.lineHeight / 2), textColor);
    }

    public static void drawFooter(GuiGraphics g, int width, int screenHeight, int footerHeight, int borderColor,
                                  int headerColor) {
        int topY = screenHeight - footerHeight;
        g.fill(0, topY, width, screenHeight, headerColor);
        g.fill(0, topY, width, topY + 1, borderColor);
    }

    public static void drawCodeBlock(GuiGraphics g, Font font, String text, int x, int y, int width, int height,
                                     int mx, int my, int borderColor, int accentColor, boolean isHovered) {
        
        g.fill(x, y, x + width, y + height, 0xFF0A0A12);
        g.fill(x, y, x + 1, y + height, borderColor);

        int btnW = font.width("âŽ˜") + 8;
        int btnX = x + width - btnW - 2;
        int btnY = y + 1;
        int btnH = height - 2;

        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, isHovered ? 0x44FFFFFF : 0x22FFFFFF);
        g.drawCenteredString(font, isHovered ? "§fâŽ˜" : "§7âŽ˜", btnX + btnW / 2,
                btnY + (height / 2) - (font.lineHeight / 2), 0xFFFFFFFF);

        int maxTextWidth = btnX - x - 6;
        String renderedText = font.width(text) <= maxTextWidth ? text :
                font.plainSubstrByWidth(text, maxTextWidth - 4) + "â€¦";
        g.drawString(font, renderedText, x + 4, y + (height / 2) - (font.lineHeight / 2), 0xFFFFFFFF, false);
    }

    public static void drawScrollbar(GuiGraphics g, int rightMarginX, int topBoundsY, int bottomBoundsY,
                                     int currentScrollY, int totalContentHeight) {
        int visibleHeight = bottomBoundsY - topBoundsY;
        if (totalContentHeight <= visibleHeight) return;

        int trackWidth = 2;
        int scrollTrackLeft = rightMarginX - trackWidth;

        int thumbHeight = Math.max(16, (visibleHeight * visibleHeight) / totalContentHeight);
        long maxScrollableDistance = totalContentHeight - visibleHeight;

        int thumbY = topBoundsY + (int) ((long) currentScrollY * (visibleHeight - thumbHeight) / maxScrollableDistance);

        g.fill(scrollTrackLeft, topBoundsY, rightMarginX, bottomBoundsY, 0x22FFFFFF);
        g.fill(scrollTrackLeft, thumbY, rightMarginX, thumbY + thumbHeight, 0x88FFFFFF);
    }

    public static void drawHeading(GuiGraphics g, Font font, String text, int x, int y, int w, int accentColor,
                                   int textColor) {
        g.fill(x, y + 13, x + w, y + 14, accentColor); 
        g.drawString(font, "§f" + text, x, y + 2, textColor, false);
    }

    public static void drawSubheading(GuiGraphics g, Font font, String text, int x, int y, int textColorDim) {
        g.drawString(font, text, x, y + 2, textColorDim, false);
    }

    public static void drawKeyValue(GuiGraphics g, Font font, String key, String value, int x, int y, int w,
                                    int accentColor, int textColorDim) {
        int kw = font.width(key + "  ");
        g.drawString(font, key, x, y, accentColor, false);

        int maxVW = w - kw;
        if (font.width(value) <= maxVW) {
            g.drawString(font, value, x + kw, y, textColorDim, false);
        } else {
            String clipped = font.plainSubstrByWidth(value, maxVW - 6) + "â€¦";
            g.drawString(font, clipped, x + kw, y, textColorDim, false);
        }
    }

    public static void drawText(GuiGraphics g, Font font, String text, int x, int y, int textColorDim) {
        g.drawString(font, text, x, y, textColorDim, false);
    }

    public static void drawBulletPoint(GuiGraphics g, Font font, String text, int x, int y, int textColorDim) {
        g.drawString(font, "â€¢ " + text, x + 8, y, textColorDim, false);
    }

    public static void drawDivider(GuiGraphics g, int x, int y, int w, int borderColor) {
        g.fill(x, y + 3, x + w, y + 4, borderColor);
    }
}

