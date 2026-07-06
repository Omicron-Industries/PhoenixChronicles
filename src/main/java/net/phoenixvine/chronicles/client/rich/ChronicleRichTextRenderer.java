package net.phoenixvine.chronicles.client.rich;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

public final class ChronicleRichTextRenderer {

    public static final int LINE_H = 10;
    private static final int LINK_COLOR = 0xFF55AAFF;
    private static final int TIP_COLOR = 0xFFAAFFAA;
    private static final Style LINK_STYLE = Style.EMPTY.withUnderlined(true);

    private ChronicleRichTextRenderer() {}

    public static List<RichSpan.Region> render(
                                               GuiGraphics g, Font font,
                                               List<RichSpan> spans,
                                               int x, int y, int maxW,
                                               int scrollY, int clipTop, int clipBot) {
        List<RichSpan.Region> regions = new ArrayList<>();
        int curX = x;
        int curY = y - scrollY;

        for (RichSpan span : spans) {
            // Replaced switch with Java 17 compliant instanceof pattern matching
            if (span instanceof RichSpan.Image img) {
                // Images always go on their own line
                if (curX > x) {
                    curX = x;
                    curY += LINE_H;
                }
                if (curY + img.h() >= clipTop && curY < clipBot)
                    g.blit(img.texture(), curX, curY, 0, 0, img.w(), img.h(), img.w(), img.h());
                regions.add(new RichSpan.Region(curX, curY, curX + img.w(), curY + img.h(), img));
                curY += img.h() + 2;
                curX = x;
            } else if (span instanceof RichSpan.Text t) {
                int[] pos = renderWords(g, font, t.text(), t.style(), 0xFFFFFFFF,
                        curX, curY, x, maxW, scrollY, clipTop, clipBot, regions, t);
                curX = pos[0];
                curY = pos[1];
            } else if (span instanceof RichSpan.Link l) {
                Style ls = l.style().withColor(LINK_COLOR).withUnderlined(true);
                int[] pos = renderWords(g, font, l.label(), ls, LINK_COLOR,
                        curX, curY, x, maxW, scrollY, clipTop, clipBot, regions, l);
                curX = pos[0];
                curY = pos[1];
            } else if (span instanceof RichSpan.Tip t) {
                Style ts = t.style().withColor(TIP_COLOR).withUnderlined(true);
                int[] pos = renderWords(g, font, t.label(), ts, TIP_COLOR,
                        curX, curY, x, maxW, scrollY, clipTop, clipBot, regions, t);
                curX = pos[0];
                curY = pos[1];
            }
        }

        return regions;
    }

    public static int measureHeight(Font font, List<RichSpan> spans, int maxW) {
        int curX = 0, curY = 0;
        for (RichSpan span : spans) {
            // Replaced switch with Java 17 compliant instanceof pattern matching
            if (span instanceof RichSpan.Image img) {
                if (curX > 0) curY += LINE_H;
                curY += img.h() + 2;
                curX = 0;
            } else if (span instanceof RichSpan.Text t) {
                int[] p = measureWords(font, t.text(), curX, curY, 0, maxW);
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Link l) {
                int[] p = measureWords(font, l.label(), curX, curY, 0, maxW);
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Tip t) {
                int[] p = measureWords(font, t.label(), curX, curY, 0, maxW);
                curX = p[0];
                curY = p[1];
            }
        }
        return curY + (curX > 0 ? LINE_H : 0);
    }

    // ── Internals (Unchanged) ─────────────────────────────────────────────────

    private static int[] renderWords(
                                     GuiGraphics g, Font font,
                                     String text, Style style, int fallbackColor,
                                     int curX, int curY,
                                     int originX, int maxW,
                                     int scrollY, int clipTop, int clipBot,
                                     List<RichSpan.Region> regions, RichSpan source) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };

        String[] lines = text.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += LINE_H;
            }
            String line = lines[li];
            String[] tokens = tokenize(line);
            for (String token : tokens) {
                if (token.isBlank() && curX == originX) continue;
                int tokW = font.width(token);
                if (curX + tokW > originX + maxW && curX > originX) {
                    curX = originX;
                    curY += LINE_H;
                }
                if (curY >= clipTop && curY < clipBot) {
                    MutableComponent comp = Component.literal(token).withStyle(style);
                    int color = style.getColor() != null ? (0xFF000000 | style.getColor().getValue()) : fallbackColor;
                    g.drawString(font, comp, curX, curY, color, false);
                }
                boolean interactive = source instanceof RichSpan.Link || source instanceof RichSpan.Tip;
                if (interactive && !token.isBlank()) {
                    regions.add(new RichSpan.Region(curX, curY, curX + tokW, curY + LINE_H, source));
                }
                curX += tokW;
            }
        }
        return new int[] { curX, curY };
    }

    private static int[] measureWords(Font font, String text, int curX, int curY, int originX, int maxW) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };
        String[] lines = text.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += LINE_H;
            }
            for (String token : tokenize(lines[li])) {
                if (token.isBlank() && curX == originX) continue;
                int tokW = font.width(token);
                if (curX + tokW > originX + maxW && curX > originX) {
                    curX = originX;
                    curY += LINE_H;
                }
                curX += tokW;
            }
        }
        return new int[] { curX, curY };
    }

    private static String[] tokenize(String s) {
        if (s.isEmpty()) return new String[] { "" };
        List<String> tokens = new ArrayList<>();
        int i = 0, len = s.length();
        while (i < len) {
            int start = i;
            while (i < len && s.charAt(i) != ' ') i++;
            while (i < len && s.charAt(i) == ' ') i++;
            tokens.add(s.substring(start, i));
        }
        return tokens.toArray(String[]::new);
    }
}
