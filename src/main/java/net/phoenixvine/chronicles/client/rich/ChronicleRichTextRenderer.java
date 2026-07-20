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
    private static final int HEADING_COLOR = 0xFFF0F0FF;
    private static final Style LINK_STYLE = Style.EMPTY.withUnderlined(true);

    // Extra vertical gap inserted *before* each block type (beyond the normal line advance
    // already produced by the previous block's last line). Headings get breathing room on both
    // sides so they read as section breaks; list items and paragraphs get a smaller gap so
    // consecutive list items don't look like separate paragraphs.
    private static final int GAP_PARAGRAPH = 4;
    private static final int GAP_LIST_ITEM = 2;
    private static final int GAP_HEADING_BEFORE = 8;
    private static final int GAP_HEADING_AFTER = 2;
    private static final int GAP_BLANK = 4;

    private ChronicleRichTextRenderer() {}

    // ── Flat span-list API (unchanged behavior, still used anywhere that only has a raw
    // List<RichSpan> with no block structure — e.g. anything still calling ChronicleTextParser
    // directly) ─────────────────────────────────────────────────────────────────────────────

    public static List<RichSpan.Region> render(
                                               GuiGraphics g, Font font,
                                               List<RichSpan> spans,
                                               int x, int y, int maxW,
                                               int scrollY, int clipTop, int clipBot) {
        return render(g, font, spans, x, y, maxW, scrollY, clipTop, clipBot, 1.0f);
    }

    /**
     * Same as {@link #render(GuiGraphics, Font, List, int, int, int, int, int, int)} but with a
     * text scale multiplier (see QuestChroniclesSettings#getTextScaleMultiplier) - x/y/maxW/
     * scrollY/clipTop/clipBot all stay in real screen-pixel units regardless of scale (each
     * token's effective width and the line-advance height are what scale internally), so callers
     * don't need to pre-divide/multiply anything themselves, and the returned regions are
     * directly usable for hover/click hit-testing against raw mouse coordinates.
     */
    public static List<RichSpan.Region> render(
                                               GuiGraphics g, Font font,
                                               List<RichSpan> spans,
                                               int x, int y, int maxW,
                                               int scrollY, int clipTop, int clipBot, float scale) {
        List<RichSpan.Region> regions = new ArrayList<>();
        int[] endY = { y - scrollY };
        renderSpanList(g, font, spans, x, endY, x, maxW, clipTop, clipBot, regions, scale);
        return regions;
    }

    public static int measureHeight(Font font, List<RichSpan> spans, int maxW) {
        return measureSpanList(font, spans, maxW, 1.0f);
    }

    /** Same as {@link #measureHeight(Font, List, int)} but at the given text scale multiplier. */
    public static int measureHeight(Font font, List<RichSpan> spans, int maxW, float scale) {
        return measureSpanList(font, spans, maxW, scale);
    }

    // ── Block-level API — the single rendering path both the compact card and fullscreen
    // panel should use going forward, for both .snbt-sourced and .md-sourced descriptions. ────

    /**
     * Renders a full list of {@link RichBlock}s (headings, paragraphs, list items, blank
     * spacers) starting at (x, y), word-wrapped to maxW, clipped to [clipTop, clipBot) and
     * offset by scrollY. Returns the interactive regions (links/tooltips) for hover/click
     * handling, same contract as {@link #render}.
     */
    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot) {
        return renderBlocks(g, font, blocks, x, y, maxW, scrollY, clipTop, clipBot, 1.0f);
    }

    /** Same as {@link #renderBlocks} but at the given text scale multiplier - see {@link #render}. */
    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot, float scale) {
        List<RichSpan.Region> regions = new ArrayList<>();
        int[] curY = { y - scrollY };
        boolean first = true;

        for (RichBlock block : blocks) {
            if (block instanceof RichBlock.Blank) {
                curY[0] += GAP_BLANK;
                first = false;
                continue;
            }

            if (!first) curY[0] += gapBefore(block);
            first = false;

            if (block instanceof RichBlock.Heading h) {
                List<RichSpan> styled = withHeadingStyle(h.spans());
                renderSpanList(g, font, styled, x, curY, x, maxW, clipTop, clipBot, regions, scale);
                curY[0] += GAP_HEADING_AFTER;
            } else if (block instanceof RichBlock.ListItem li) {
                if (curY[0] >= clipTop && curY[0] + 8 <= clipBot) {
                    g.drawString(font, li.marker(), x, curY[0], 0xFFAAAAAA, false);
                }
                renderSpanList(g, font, li.spans(), x + li.indent(), curY, x + li.indent(),
                        maxW - li.indent(), clipTop, clipBot, regions, scale);
            } else if (block instanceof RichBlock.Paragraph p) {
                renderSpanList(g, font, p.spans(), x, curY, x, maxW, clipTop, clipBot, regions, scale);
            }
        }

        return regions;
    }

    /** Total pixel height {@link #renderBlocks} would occupy — needed for scroll clamping. */
    public static int measureBlocksHeight(Font font, List<RichBlock> blocks, int maxW) {
        return measureBlocksHeight(font, blocks, maxW, 1.0f);
    }

    /** Same as {@link #measureBlocksHeight} but at the given text scale multiplier. */
    public static int measureBlocksHeight(Font font, List<RichBlock> blocks, int maxW, float scale) {
        int y = 0;
        boolean first = true;
        for (RichBlock block : blocks) {
            if (block instanceof RichBlock.Blank) {
                y += GAP_BLANK;
                first = false;
                continue;
            }
            if (!first) y += gapBefore(block);
            first = false;

            if (block instanceof RichBlock.Heading h) {
                y = measureSpanListFrom(font, withHeadingStyle(h.spans()), maxW, y, scale);
                y += GAP_HEADING_AFTER;
            } else if (block instanceof RichBlock.ListItem li) {
                y = measureSpanListFrom(font, li.spans(), maxW - li.indent(), y, scale);
            } else if (block instanceof RichBlock.Paragraph p) {
                y = measureSpanListFrom(font, p.spans(), maxW, y, scale);
            }
        }
        return y;
    }

    private static int gapBefore(RichBlock block) {
        if (block instanceof RichBlock.Heading) return GAP_HEADING_BEFORE;
        if (block instanceof RichBlock.ListItem) return GAP_LIST_ITEM;
        return GAP_PARAGRAPH;
    }

    private static List<RichSpan> withHeadingStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(true).withColor(
                        net.minecraft.network.chat.TextColor.fromRgb(HEADING_COLOR & 0xFFFFFF))));
            } else {
                out.add(s); // links/tips/images inside a heading keep their own semantics
            }
        }
        return out;
    }

    // ── Shared span-list engine (used by both the flat and block-level APIs) ──────────────────

    private static void renderSpanList(GuiGraphics g, Font font, List<RichSpan> spans,
                                       int x, int[] curY, int originX, int maxW,
                                       int clipTop, int clipBot, List<RichSpan.Region> regions, float scale) {
        int lineH = Math.round(LINE_H * scale);
        int curX = x;
        for (RichSpan span : spans) {
            if (span instanceof RichSpan.Image img) {
                if (curX > originX) {
                    curX = originX;
                    curY[0] += lineH;
                }
                if (curY[0] >= clipTop && curY[0] + img.h() <= clipBot)
                    g.blit(net.phoenixvine.chronicles.client.CustomTextureCache.resolve(img.texture()),
                            curX, curY[0], 0, 0, img.w(), img.h(), img.w(), img.h());
                regions.add(new RichSpan.Region(curX, curY[0], curX + img.w(), curY[0] + img.h(), img));
                curY[0] += img.h() + 2;
                curX = originX;
            } else if (span instanceof RichSpan.Text t) {
                int[] pos = renderWords(g, font, t.text(), t.style(), 0xFFFFFFFF,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, t, scale);
                curX = pos[0];
                curY[0] = pos[1];
            } else if (span instanceof RichSpan.Link l) {
                Style ls = l.style().withColor(LINK_COLOR).withUnderlined(true);
                int[] pos = renderWords(g, font, l.label(), ls, LINK_COLOR,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, l, scale);
                curX = pos[0];
                curY[0] = pos[1];
            } else if (span instanceof RichSpan.Tip t) {
                Style ts = t.style().withColor(TIP_COLOR).withUnderlined(true);
                int[] pos = renderWords(g, font, t.label(), ts, TIP_COLOR,
                        curX, curY[0], originX, maxW, clipTop, clipBot, regions, t, scale);
                curX = pos[0];
                curY[0] = pos[1];
            }
        }
    }

    private static int measureSpanList(Font font, List<RichSpan> spans, int maxW, float scale) {
        return measureSpanListFrom(font, spans, maxW, 0, scale);
    }

    private static int measureSpanListFrom(Font font, List<RichSpan> spans, int maxW, int startY, float scale) {
        int lineH = Math.round(LINE_H * scale);
        int curX = 0, curY = startY;
        for (RichSpan span : spans) {
            if (span instanceof RichSpan.Image img) {
                if (curX > 0) curY += lineH;
                curY += img.h() + 2;
                curX = 0;
            } else if (span instanceof RichSpan.Text t) {
                int[] p = measureWords(font, t.text(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Link l) {
                int[] p = measureWords(font, l.label(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Tip t) {
                int[] p = measureWords(font, t.label(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            }
        }
        return curY + (curX > 0 ? lineH : 0);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private static int[] renderWords(
                                     GuiGraphics g, Font font,
                                     String text, Style style, int fallbackColor,
                                     int curX, int curY,
                                     int originX, int maxW,
                                     int clipTop, int clipBot,
                                     List<RichSpan.Region> regions, RichSpan source, float scale) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };

        // Wrap/advance math stays in real screen-pixel units throughout (curX/curY/maxW are never
        // pre-divided by scale) - only each token's EFFECTIVE width/line-height account for the
        // scale multiplier, so the returned regions are directly usable for hover/click hit-
        // testing against raw mouse coordinates with no separate coordinate-space conversion.
        int lineH = Math.round(LINE_H * scale);

        // Still honor any literal "\n" that survives into a single inline text run (e.g. from
        // raw .snbt descriptions that aren't run through the block parser at all) — same forced
        // line break the old renderer supported.
        String[] lines = text.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += lineH;
            }
            String line = lines[li];
            String[] tokens = tokenize(line);
            for (String token : tokens) {
                if (token.isBlank() && curX == originX) continue;
                int tokW = Math.round(font.width(token) * scale);
                if (curX + tokW > originX + maxW && curX > originX) {
                    curX = originX;
                    curY += lineH;
                }
                // Was `curY < clipBot` alone - only checking the glyph's TOP, so a line landing
                // right at the clip edge still drew its full ~8px height, bleeding several pixels
                // past clipBot into whatever's rendered just below (the pager pill, the footer).
                // Require the WHOLE line to fit, same fix as MultilineTextArea/the inspector
                // tabs' lineFullyVisible().
                if (curY >= clipTop && curY + 8 <= clipBot) {
                    MutableComponent comp = Component.literal(token).withStyle(style);
                    int color = style.getColor() != null ? (0xFF000000 | style.getColor().getValue()) : fallbackColor;
                    if (scale == 1.0f) {
                        g.drawString(font, comp, curX, curY, color, false);
                    } else {
                        g.pose().pushPose();
                        g.pose().translate(curX, curY, 0);
                        g.pose().scale(scale, scale, 1f);
                        g.drawString(font, comp, 0, 0, color, false);
                        g.pose().popPose();
                    }
                }
                boolean interactive = source instanceof RichSpan.Link || source instanceof RichSpan.Tip;
                if (interactive && !token.isBlank()) {
                    regions.add(new RichSpan.Region(curX, curY, curX + tokW, curY + lineH, source));
                }
                curX += tokW;
            }
        }
        return new int[] { curX, curY };
    }

    private static int[] measureWords(Font font, String text, int curX, int curY, int originX, int maxW,
                                      float scale) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };
        int lineH = Math.round(LINE_H * scale);
        String[] lines = text.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += lineH;
            }
            for (String token : tokenize(lines[li])) {
                if (token.isBlank() && curX == originX) continue;
                int tokW = Math.round(font.width(token) * scale);
                if (curX + tokW > originX + maxW && curX > originX) {
                    curX = originX;
                    curY += lineH;
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
