package net.phoenixvine.chronicles.client.rich;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ChronicleRichTextRenderer {

    public static final int LINE_H = 10;
    private static final int LINK_COLOR = 0xFF55AAFF;
    private static final int TIP_COLOR = 0xFFAAFFAA;
    private static final int HEADING_COLOR = 0xFFF0F0FF;
    private static final int SUBHEADING_COLOR = 0xFFC0C0CC;
    private static final int H3_COLOR = 0xFFA89CC8;
    private static final int DEFAULT_ACCENT = 0xFF9966FF;

    private static final int GAP_PARAGRAPH = 4;
    private static final int GAP_LIST_ITEM = 2;
    private static final int GAP_HEADING_BEFORE = 8;
    private static final int GAP_HEADING_AFTER = 2;
    private static final int GAP_BLANK = 4;

    public static java.util.function.UnaryOperator<ResourceLocation> imageResolver = java.util.function.UnaryOperator
            .identity();

    private ChronicleRichTextRenderer() {}

    public static List<RichSpan.Region> render(
                                               GuiGraphics g, Font font,
                                               List<RichSpan> spans,
                                               int x, int y, int maxW,
                                               int scrollY, int clipTop, int clipBot) {
        return render(g, font, spans, x, y, maxW, scrollY, clipTop, clipBot, 1.0f);
    }

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
        return measureHeight(font, spans, maxW, 1.0f);
    }

    private static final int HEIGHT_CACHE_SLOTS = 6;
    private static final List<RichSpan>[] hcSpans = new List[HEIGHT_CACHE_SLOTS];
    private static final int[] hcWidth = new int[HEIGHT_CACHE_SLOTS];
    private static final float[] hcScale = new float[HEIGHT_CACHE_SLOTS];
    private static final int[] hcHeight = new int[HEIGHT_CACHE_SLOTS];
    private static int hcNext = 0;

    public static int measureHeight(Font font, List<RichSpan> spans, int maxW, float scale) {
        for (int i = 0; i < HEIGHT_CACHE_SLOTS; i++) {
            if (hcSpans[i] == spans && hcWidth[i] == maxW && hcScale[i] == scale) {
                return hcHeight[i];
            }
        }
        int height = measureSpanList(font, spans, maxW, scale);
        hcSpans[hcNext] = spans;
        hcWidth[hcNext] = maxW;
        hcScale[hcNext] = scale;
        hcHeight[hcNext] = height;
        hcNext = (hcNext + 1) % HEIGHT_CACHE_SLOTS;
        return height;
    }

    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot) {
        return renderBlocks(g, font, blocks, x, y, maxW, scrollY, clipTop, clipBot, 1.0f, DEFAULT_ACCENT, Set.of());
    }

    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot, int accentColor) {
        return renderBlocks(g, font, blocks, x, y, maxW, scrollY, clipTop, clipBot, 1.0f, accentColor, Set.of());
    }

    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot, int accentColor,
                                                     Set<String> expandedKeys) {
        return renderBlocks(g, font, blocks, x, y, maxW, scrollY, clipTop, clipBot, 1.0f, accentColor, expandedKeys);
    }

    public static List<RichSpan.Region> renderBlocks(
                                                     GuiGraphics g, Font font,
                                                     List<RichBlock> blocks,
                                                     int x, int y, int maxW,
                                                     int scrollY, int clipTop, int clipBot, float scale,
                                                     int accentColor, Set<String> expandedKeys) {
        List<RichSpan.Region> regions = new ArrayList<>();
        renderBlockList(g, font, blocks, x, y - scrollY, maxW, clipTop, clipBot, regions, scale, accentColor,
                expandedKeys);
        return regions;
    }

    public static int measureBlocksHeight(Font font, List<RichBlock> blocks, int maxW) {
        return measureBlocksHeight(font, blocks, maxW, 1.0f, Set.of());
    }

    public static int measureBlocksHeight(Font font, List<RichBlock> blocks, int maxW, Set<String> expandedKeys) {
        return measureBlocksHeight(font, blocks, maxW, 1.0f, expandedKeys);
    }

    public static int measureBlocksHeight(Font font, List<RichBlock> blocks, int maxW, float scale,
                                          Set<String> expandedKeys) {
        return measureBlockList(font, blocks, maxW, 0, scale, expandedKeys);
    }

    public record HeadingInfo(int level, String text, int y) {}

    public static List<HeadingInfo> computeHeadingOffsets(Font font, List<RichBlock> blocks, int maxW) {
        List<HeadingInfo> out = new ArrayList<>();
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
                out.add(new HeadingInfo(h.level(), plainText(h.spans()), y));
            }
            y = measureOneBlock(font, block, maxW, y, 1.0f, Set.of());
        }
        return out;
    }

    private static String plainText(List<RichSpan> spans) {
        StringBuilder sb = new StringBuilder();
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) sb.append(t.text());
            else if (s instanceof RichSpan.Link l) sb.append(l.label());
            else if (s instanceof RichSpan.Tip t) sb.append(t.label());
        }
        return sb.toString();
    }

    private static int renderBlockList(GuiGraphics g, Font font, List<RichBlock> blocks, int x, int y, int maxW,
                                       int clipTop, int clipBot, List<RichSpan.Region> regions, float scale,
                                       int accentColor, Set<String> expandedKeys) {
        int[] curY = { y };
        boolean first = true;
        for (RichBlock block : blocks) {
            if (block instanceof RichBlock.Blank) {
                curY[0] += GAP_BLANK;
                first = false;
                continue;
            }
            if (!first) curY[0] += gapBefore(block);
            first = false;
            renderOneBlock(g, font, block, x, curY, maxW, clipTop, clipBot, regions, scale, accentColor,
                    expandedKeys);
        }
        return curY[0];
    }

    private static void renderOneBlock(GuiGraphics g, Font font, RichBlock block, int x, int[] curY, int maxW,
                                       int clipTop, int clipBot, List<RichSpan.Region> regions, float scale,
                                       int accentColor, Set<String> expandedKeys) {
        if (block instanceof RichBlock.Heading h) {
            int headY = curY[0];
            List<RichSpan> styled = switch (Math.min(h.level(), 3)) {
                case 1 -> withHeadingStyle(h.spans());
                case 2 -> withSubheadingStyle(h.spans());
                default -> withH3Style(h.spans());
            };
            renderSpanList(g, font, styled, x, curY, x, maxW, clipTop, clipBot, regions, scale);
            if (h.level() <= 1 && headY + 14 >= clipTop && headY <= clipBot) {
                g.fill(x, headY + 13, x + maxW, headY + 14, accentColor);
            }
            curY[0] += GAP_HEADING_AFTER;
        } else if (block instanceof RichBlock.ListItem li) {
            if (curY[0] >= clipTop && curY[0] + 8 <= clipBot) {
                g.drawString(font, li.marker(), x, curY[0], 0xFFAAAAAA, false);
            }
            renderSpanList(g, font, li.spans(), x + li.indent(), curY, x + li.indent(),
                    maxW - li.indent(), clipTop, clipBot, regions, scale);
        } else if (block instanceof RichBlock.Checklist cl) {
            boolean checked = expandedKeys.contains("CL1:" + cl.checkKey()) ? true :
                    !expandedKeys.contains("CL0:" + cl.checkKey()) && cl.checkedDefault();
            String glyph = checked ? "☑" : "☐";
            if (curY[0] >= clipTop && curY[0] + 8 <= clipBot) {
                g.drawString(font, glyph, x, curY[0], checked ? 0xFF6FCF6F : 0xFFAAAAAA, false);
            }
            regions.add(new RichSpan.Region(x, curY[0], x + cl.indent(), curY[0] + 10,
                    new RichSpan.ChecklistToggle(cl.checkKey(), cl.checkedDefault())));
            List<RichSpan> spans = checked ? withStrikethroughStyle(cl.spans()) : cl.spans();
            renderSpanList(g, font, spans, x + cl.indent(), curY, x + cl.indent(),
                    maxW - cl.indent(), clipTop, clipBot, regions, scale);
        } else if (block instanceof RichBlock.Paragraph p) {
            renderSpanList(g, font, p.spans(), x, curY, x, maxW, clipTop, clipBot, regions, scale);
        } else if (block instanceof RichBlock.Rule) {
            if (curY[0] + 1 >= clipTop && curY[0] <= clipBot) {
                g.fill(x, curY[0] + 3, x + maxW, curY[0] + 4, 0xFF3A3040);
            }
            curY[0] += 8;
        } else if (block instanceof RichBlock.CodeBlock cb) {
            curY[0] = renderCodeBlock(g, font, cb.lang(), cb.code(), x, curY[0], maxW, clipTop, clipBot, regions);
        } else if (block instanceof RichBlock.Quote q) {
            int quoteY = curY[0];
            int barX = x + 2;
            int textX = x + 10;
            renderSpanList(g, font, q.spans(), textX, curY, textX, maxW - 10, clipTop, clipBot, regions, scale);
            if (curY[0] > quoteY && quoteY <= clipBot && curY[0] >= clipTop) {
                g.fill(barX, quoteY, barX + 2, curY[0] - 1, 0xFF5A5A6E);
            }
        } else if (block instanceof RichBlock.Table t) {
            curY[0] = renderTable(g, font, t, x, curY[0], maxW, clipTop, clipBot, regions, scale);
        } else if (block instanceof RichBlock.Callout c) {
            curY[0] = renderCallout(g, font, c, x, curY[0], maxW, clipTop, clipBot, regions, scale, expandedKeys);
        } else if (block instanceof RichBlock.Details d) {
            curY[0] = renderDetails(g, font, d, x, curY[0], maxW, clipTop, clipBot, regions, scale, accentColor,
                    expandedKeys);
        }
    }

    private static int renderCallout(GuiGraphics g, Font font, RichBlock.Callout c, int x, int y, int maxW,
                                     int clipTop, int clipBot, List<RichSpan.Region> regions, float scale,
                                     Set<String> expandedKeys) {
        int color = calloutColor(c.type());
        String icon = calloutIcon(c.type());
        String title = c.title().isEmpty() ? capitalize(c.type()) : c.title();

        int innerX = x + 10;
        int innerMaxW = maxW - 16;
        int headY = y + 6;
        int bodyY = measureBlockList(font, c.children(), innerMaxW, headY + 12, scale, expandedKeys);
        int boxH = (bodyY - y) + 6;

        if (y + boxH >= clipTop && y <= clipBot) {
            g.fill(x, y, x + maxW, y + boxH, (color & 0xFFFFFF) | 0x18000000);
            g.fill(x, y, x + 3, y + boxH, color);
            g.drawString(font, icon + " §l" + title, innerX, headY, color, false);
        }
        renderBlockList(g, font, c.children(), innerX, headY + 12, innerMaxW, clipTop, clipBot, regions, scale,
                color, expandedKeys);
        return y + boxH;
    }

    private static int renderDetails(GuiGraphics g, Font font, RichBlock.Details d, int x, int y, int maxW,
                                     int clipTop, int clipBot, List<RichSpan.Region> regions, float scale,
                                     int accentColor, Set<String> expandedKeys) {
        boolean expanded = expandedKeys.contains(d.expandKey());
        int headH = 14;

        if (y >= clipTop && y - headH <= clipBot) {
            g.fill(x, y, x + maxW, y + headH, 0xFF16121C);
            g.drawString(font, (expanded ? "§f▾ " : "§7▸ ") + "§l" + d.title(), x + 4, y + 3, 0xFFE0D8F0, false);
        }
        regions.add(new RichSpan.Region(x, y, x + maxW, y + headH, new RichSpan.DetailsToggle(d.expandKey())));
        int curY = y + headH + (expanded ? 3 : 0);

        if (expanded) {
            curY = renderBlockList(g, font, d.children(), x + 10, curY, maxW - 10, clipTop, clipBot, regions, scale,
                    accentColor, expandedKeys);
            curY += 3;
        }
        return curY;
    }

    private static int renderTable(GuiGraphics g, Font font, RichBlock.Table t, int x, int y, int maxW,
                                   int clipTop, int clipBot, List<RichSpan.Region> regions, float scale) {
        int cols = t.header().size();
        for (List<List<RichSpan>> row : t.rows()) cols = Math.max(cols, row.size());
        if (cols == 0) return y;
        int colW = maxW / cols;

        int headerH = tableRowHeight(font, t.header(), colW, scale);
        if (y >= clipTop && y + headerH <= clipBot) {
            g.fill(x, y, x + maxW, y + headerH, 0xFF1E1830);
        }
        for (int c = 0; c < t.header().size(); c++) {
            int[] cellY = { y + 3 };
            renderSpanList(g, font, t.header().get(c), x + c * colW + 4, cellY, x + c * colW + 4, colW - 8,
                    clipTop, clipBot, regions, scale);
        }
        int rowY = y + headerH;
        if (rowY >= clipTop && rowY <= clipBot) {
            g.fill(x, rowY, x + maxW, rowY + 1, 0xFF3A3040);
        }
        rowY += 1;

        for (int r = 0; r < t.rows().size(); r++) {
            List<List<RichSpan>> row = t.rows().get(r);
            int rowH = tableRowHeight(font, row, colW, scale);
            if (r % 2 == 1 && rowY >= clipTop && rowY + rowH <= clipBot) {
                g.fill(x, rowY, x + maxW, rowY + rowH, 0x14FFFFFF);
            }
            for (int c = 0; c < row.size(); c++) {
                int[] cellY = { rowY + 3 };
                renderSpanList(g, font, row.get(c), x + c * colW + 4, cellY, x + c * colW + 4, colW - 8,
                        clipTop, clipBot, regions, scale);
            }
            rowY += rowH;
        }
        return rowY;
    }

    private static int tableRowHeight(Font font, List<List<RichSpan>> cells, int colW, float scale) {
        int maxH = Math.round(LINE_H * scale);
        for (List<RichSpan> cell : cells) {
            int h = measureSpanListFrom(font, cell, Math.max(1, colW - 8), 0, scale);
            maxH = Math.max(maxH, h);
        }
        return maxH + 6;
    }

    private static int calloutColor(String type) {
        return switch (type) {
            case "warning", "warn" -> 0xFFE0A030;
            case "danger", "error" -> 0xFFE05050;
            case "tip", "success" -> 0xFF50C878;
            case "note", "info" -> 0xFF55AAFF;
            default -> 0xFF9966FF;
        };
    }

    private static String calloutIcon(String type) {
        return switch (type) {
            case "warning", "warn" -> "⚠";
            case "danger", "error" -> "⛔";
            case "tip", "success" -> "💡";
            case "note", "info" -> "ℹ";
            default -> "●";
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private record HToken(String text, int color) {}

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "public", "private", "protected", "class", "interface", "extends", "implements", "static", "final",
            "void", "new", "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
            "try", "catch", "finally", "throw", "throws", "import", "package", "this", "super", "true", "false",
            "null", "default", "enum", "record", "sealed", "permits", "yield", "abstract", "instanceof",
            "int", "long", "double", "float", "boolean", "char", "byte", "short", "synchronized", "volatile",
            "transient", "native");

    private static final Set<String> JS_KEYWORDS = Set.of(
            "function", "const", "let", "var", "typeof", "instanceof", "in", "of", "new", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
            "import", "export", "from", "default", "class", "extends", "super", "this", "true", "false", "null",
            "undefined", "async", "await", "yield", "static", "get", "set", "delete", "void");

    private static final Set<String> TS_KEYWORDS = Set.of(
            "function", "const", "let", "var", "typeof", "instanceof", "in", "of", "new", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
            "import", "export", "from", "default", "class", "extends", "implements", "interface", "type",
            "super", "this", "true", "false", "null", "undefined", "async", "await", "yield", "static",
            "public", "private", "protected", "readonly", "enum", "namespace", "declare", "as");

    private static final Set<String> KOTLIN_KEYWORDS = Set.of(
            "fun", "val", "var", "class", "interface", "object", "companion", "override", "private", "public",
            "protected", "internal", "return", "if", "else", "for", "while", "do", "when", "is", "in", "true",
            "false", "null", "this", "super", "import", "package", "data", "sealed", "abstract", "open", "final",
            "suspend", "inline", "reified", "vararg", "lateinit");

    private static final Set<String> JSON_KEYWORDS = Set.of("true", "false", "null");

    private static Set<String> keywordsFor(String lang) {
        return switch (lang == null ? "" : lang.toLowerCase()) {
            case "java" -> JAVA_KEYWORDS;
            case "js", "javascript" -> JS_KEYWORDS;
            case "ts", "typescript" -> TS_KEYWORDS;
            case "kotlin" -> KOTLIN_KEYWORDS;
            case "json" -> JSON_KEYWORDS;
            default -> Set.of();
        };
    }

    private static boolean usesTemplateLiterals(String lang) {
        String l = lang == null ? "" : lang.toLowerCase();
        return l.equals("js") || l.equals("javascript") || l.equals("ts") || l.equals("typescript");
    }

    private static boolean usesAnnotations(String lang) {
        String l = lang == null ? "" : lang.toLowerCase();
        return l.equals("java") || l.equals("kotlin");
    }

    private static final Set<String> HIGHLIGHT_LANGS = Set.of("java", "js", "javascript", "json", "kotlin", "ts",
            "typescript");

    private static List<HToken> highlightLine(String lang, String line) {
        List<HToken> out = new ArrayList<>();
        if (lang == null || !HIGHLIGHT_LANGS.contains(lang.toLowerCase())) {
            out.add(new HToken(line, 0xFFE0E0E0));
            return out;
        }

        Set<String> keywords = keywordsFor(lang);
        boolean templateLiterals = usesTemplateLiterals(lang);
        boolean annotations = usesAnnotations(lang);

        int i = 0, len = line.length();
        StringBuilder buf = new StringBuilder();
        while (i < len) {
            char c = line.charAt(i);
            if (c == '/' && i + 1 < len && line.charAt(i + 1) == '/') {
                flushPlain(buf, out);
                out.add(new HToken(line.substring(i), 0xFF6A9955));
                break;
            }
            if (c == '"' || c == '\'' || (c == '`' && templateLiterals)) {
                flushPlain(buf, out);
                int end = i + 1;
                while (end < len && line.charAt(end) != c) end++;
                end = Math.min(end + 1, len);
                out.add(new HToken(line.substring(i, end), 0xFFCE9178));
                i = end;
                continue;
            }
            if (c == '@' && annotations && i + 1 < len &&
                    (Character.isLetter(line.charAt(i + 1)) || line.charAt(i + 1) == '_')) {
                flushPlain(buf, out);
                int start = i;
                i++;
                while (i < len && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) i++;
                out.add(new HToken(line.substring(start, i), 0xFFDCDCAA));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) i++;
                String word = line.substring(start, i);
                if (keywords.contains(word)) {
                    flushPlain(buf, out);
                    out.add(new HToken(word, 0xFF569CD6));
                } else {
                    buf.append(word);
                }
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(line.charAt(i)) || line.charAt(i) == '.')) i++;
                flushPlain(buf, out);
                out.add(new HToken(line.substring(start, i), 0xFFB5CEA8));
                continue;
            }
            buf.append(c);
            i++;
        }
        flushPlain(buf, out);
        return out;
    }

    private static void flushPlain(StringBuilder buf, List<HToken> out) {
        if (!buf.isEmpty()) {
            out.add(new HToken(buf.toString(), 0xFFE0E0E0));
            buf.setLength(0);
        }
    }

    private static int renderCodeBlock(GuiGraphics g, Font font, String lang, String code, int x, int y, int maxW,
                                       int clipTop, int clipBot, List<RichSpan.Region> regions) {
        List<List<HToken>> visualLines = wrapCodeLines(font, lang, code, maxW);
        int lineH = 10;
        int boxH = visualLines.size() * lineH + 6;
        int btnW = font.width("⎘") + 8;

        if (y + boxH >= clipTop && y <= clipBot) {
            g.fill(x, y, x + maxW, y + boxH, 0xFF0A0A12);
            g.fill(x, y, x + 1, y + boxH, 0xFF3A3040);
            g.fill(x + maxW - btnW - 2, y + 1, x + maxW - 2, y + 1 + font.lineHeight + 2, 0x22FFFFFF);
            g.drawString(font, "§7⎘", x + maxW - btnW - 2 + 4, y + 3, 0xFFFFFFFF, false);
            for (int li = 0; li < visualLines.size(); li++) {
                int ly = y + 3 + li * lineH;
                if (ly >= clipTop && ly + 8 <= clipBot) {
                    int cx = x + 4;
                    for (HToken tok : visualLines.get(li)) {
                        g.drawString(font, tok.text(), cx, ly, tok.color(), false);
                        cx += font.width(tok.text());
                    }
                }
            }
        }
        regions.add(new RichSpan.Region(x + maxW - btnW - 2, y + 1, x + maxW - 2, y + 1 + font.lineHeight + 2,
                new RichSpan.CodeCopy(code)));
        return y + boxH;
    }

    private static List<List<HToken>> wrapCodeLines(Font font, String lang, String code, int maxW) {
        int innerW = Math.max(8, maxW - 8);

        int btnReserve = font.width("⎘") + 8 + 10;
        int firstLineW = Math.max(8, innerW - btnReserve);
        List<List<HToken>> visualLines = new ArrayList<>();
        String[] rawLines = code.split("\n", -1);
        for (int r = 0; r < rawLines.length; r++) {
            int flw = (r == 0) ? firstLineW : -1;
            visualLines.addAll(wrapHighlightedLine(font, highlightLine(lang, rawLines[r]), innerW, flw));
        }
        return visualLines;
    }

    private static List<List<HToken>> wrapHighlightedLine(Font font, List<HToken> tokens, int maxW,
                                                          int firstLineMaxW) {
        List<List<HToken>> lines = new ArrayList<>();
        List<HToken> current = new ArrayList<>();
        int curW = 0;
        int curMaxW = firstLineMaxW > 0 ? firstLineMaxW : maxW;
        for (HToken tok : tokens) {
            String remaining = tok.text();
            while (!remaining.isEmpty()) {
                int w = font.width(remaining);
                if (curW + w <= curMaxW) {
                    current.add(new HToken(remaining, tok.color()));
                    curW += w;
                    remaining = "";
                } else if (curW == 0) {
                    int fitLen = Math.max(1, maxFitLength(font, remaining, curMaxW));
                    current.add(new HToken(remaining.substring(0, fitLen), tok.color()));
                    lines.add(current);
                    current = new ArrayList<>();
                    curW = 0;
                    curMaxW = maxW;
                    remaining = remaining.substring(fitLen);
                } else {
                    lines.add(current);
                    current = new ArrayList<>();
                    curW = 0;
                    curMaxW = maxW;
                }
            }
        }
        lines.add(current);
        return lines;
    }

    private static int maxFitLength(Font font, String text, int maxW) {
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(text.substring(0, mid)) <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    private static int measureBlockList(Font font, List<RichBlock> blocks, int maxW, int y, float scale,
                                        Set<String> expandedKeys) {
        boolean first = true;
        for (RichBlock block : blocks) {
            if (block instanceof RichBlock.Blank) {
                y += GAP_BLANK;
                first = false;
                continue;
            }
            if (!first) y += gapBefore(block);
            first = false;
            y = measureOneBlock(font, block, maxW, y, scale, expandedKeys);
        }
        return y;
    }

    private static int measureOneBlock(Font font, RichBlock block, int maxW, int y, float scale,
                                       Set<String> expandedKeys) {
        if (block instanceof RichBlock.Heading h) {
            List<RichSpan> styled = switch (Math.min(h.level(), 3)) {
                case 1 -> withHeadingStyle(h.spans());
                case 2 -> withSubheadingStyle(h.spans());
                default -> withH3Style(h.spans());
            };
            y = measureSpanListFrom(font, styled, maxW, y, scale);
            y += GAP_HEADING_AFTER;
        } else if (block instanceof RichBlock.ListItem li) {
            y = measureSpanListFrom(font, li.spans(), maxW - li.indent(), y, scale);
        } else if (block instanceof RichBlock.Checklist cl) {
            y = measureSpanListFrom(font, cl.spans(), maxW - cl.indent(), y, scale);
        } else if (block instanceof RichBlock.Paragraph p) {
            y = measureSpanListFrom(font, p.spans(), maxW, y, scale);
        } else if (block instanceof RichBlock.Rule) {
            y += 8;
        } else if (block instanceof RichBlock.CodeBlock cb) {
            y += wrapCodeLines(font, cb.lang(), cb.code(), maxW).size() * 10 + 6;
        } else if (block instanceof RichBlock.Quote q) {
            y = measureSpanListFrom(font, q.spans(), maxW - 10, y, scale);
        } else if (block instanceof RichBlock.Table t) {
            int cols = t.header().size();
            for (List<List<RichSpan>> row : t.rows()) cols = Math.max(cols, row.size());
            int colW = cols > 0 ? Math.max(1, maxW / cols) : maxW;
            y += tableRowHeight(font, t.header(), colW, scale) + 1;
            for (List<List<RichSpan>> row : t.rows()) {
                y += tableRowHeight(font, row, colW, scale);
            }
        } else if (block instanceof RichBlock.Callout c) {
            int inner = measureBlockList(font, c.children(), maxW - 16, y + 12, scale, expandedKeys);
            y = inner + 6;
        } else if (block instanceof RichBlock.Details d) {
            y += 14;
            if (expandedKeys.contains(d.expandKey())) {
                y += 3;
                y = measureBlockList(font, d.children(), maxW - 10, y, scale, expandedKeys);
                y += 3;
            }
        }
        return y;
    }

    private static int gapBefore(RichBlock block) {
        if (block instanceof RichBlock.Heading h) return h.level() <= 2 ? GAP_HEADING_BEFORE : GAP_HEADING_BEFORE - 3;
        if (block instanceof RichBlock.ListItem) return GAP_LIST_ITEM;
        if (block instanceof RichBlock.Rule) return 0;
        return GAP_PARAGRAPH;
    }

    private static List<RichSpan> withHeadingStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(true).withColor(
                        net.minecraft.network.chat.TextColor.fromRgb(HEADING_COLOR & 0xFFFFFF)), t.background()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    private static List<RichSpan> withSubheadingStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(true).withColor(
                        net.minecraft.network.chat.TextColor.fromRgb(SUBHEADING_COLOR & 0xFFFFFF)), t.background()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    private static List<RichSpan> withStrikethroughStyle(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withStrikethrough(true)
                        .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFF888888)), t.background()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    private static List<RichSpan> withH3Style(List<RichSpan> spans) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) {
                out.add(new RichSpan.Text(t.text(), t.style().withBold(false).withColor(
                        net.minecraft.network.chat.TextColor.fromRgb(H3_COLOR & 0xFFFFFF)), t.background()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

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
                    g.blit(imageResolver.apply(img.texture()),
                            curX, curY[0], 0, 0, img.w(), img.h(), img.w(), img.h());
                regions.add(new RichSpan.Region(curX, curY[0], curX + img.w(), curY[0] + img.h(), img));
                curY[0] += img.h() + 2;
                curX = originX;
            } else if (span instanceof RichSpan.ItemIcon icon) {
                if (curX + 18 > originX + maxW && curX > originX) {
                    curX = originX;
                    curY[0] += lineH;
                }
                int iconY = curY[0] - (16 - lineH) / 2;
                if (iconY >= clipTop && iconY + 16 <= clipBot) {
                    Item item = ForgeRegistries.ITEMS.getValue(icon.itemId());
                    if (item != null) {
                        try {
                            g.renderItem(new ItemStack(item), curX, iconY);
                        } catch (Exception ignored) {}
                    }
                }
                regions.add(new RichSpan.Region(curX, iconY, curX + 16, iconY + 16, icon));
                curX += 18;
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
        if (curX > originX) curY[0] += lineH;
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
            } else if (span instanceof RichSpan.ItemIcon) {
                if (curX + 18 > maxW && curX > 0) {
                    curX = 0;
                    curY += lineH;
                }
                curX += 18;
            } else if (span instanceof RichSpan.Text t) {
                int[] p = measureWords(font, t.text(), t.style(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Link l) {
                int[] p = measureWords(font, l.label(), l.style(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            } else if (span instanceof RichSpan.Tip t) {
                int[] p = measureWords(font, t.label(), t.style(), curX, curY, 0, maxW, scale);
                curX = p[0];
                curY = p[1];
            }
        }
        return curY + (curX > 0 ? lineH : 0);
    }

    private static int[] renderWords(
                                     GuiGraphics g, Font font,
                                     String text, Style style, int fallbackColor,
                                     int curX, int curY,
                                     int originX, int maxW,
                                     int clipTop, int clipBot,
                                     List<RichSpan.Region> regions, RichSpan source, float scale) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };

        int lineH = Math.round(LINE_H * scale);
        String inlineCodeText = source instanceof RichSpan.Text t ? t.copyText() : null;
        boolean interactive = source instanceof RichSpan.Link || source instanceof RichSpan.Tip ||
                inlineCodeText != null;
        RichSpan regionPayload = inlineCodeText != null ? new RichSpan.CodeCopy(inlineCodeText) : source;
        int background = source instanceof RichSpan.Text t ? t.background() : 0;

        String[] lines = text.split("\n", -1);
        Style running = style;
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += lineH;
            }
            String line = lines[li];
            String[] tokens = tokenize(line);

            StringBuilder run = new StringBuilder();
            Style runStyle = running;
            int runStartX = curX;

            for (String token : tokens) {
                if (token.isBlank() && curX == originX) continue;
                Style newStyle = applyLegacyCodes(running, token);
                int tokW = Math.round(font.width(Component.literal(token).withStyle(newStyle)) * scale);
                if (curX + tokW > originX + maxW && curX > originX) {
                    flushRun(g, font, run, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot, scale,
                            background);
                    curX = originX;
                    curY += lineH;
                    runStartX = curX;
                }

                if (!newStyle.equals(runStyle) && !run.isEmpty()) {
                    flushRun(g, font, run, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot, scale,
                            background);
                    runStartX = curX;
                }
                running = newStyle;
                runStyle = newStyle;
                run.append(token);

                if (interactive && !token.isBlank()) {
                    regions.add(new RichSpan.Region(curX, curY, curX + tokW, curY + lineH, regionPayload));
                }
                curX += tokW;
            }
            flushRun(g, font, run, runStyle, fallbackColor, runStartX, curY, clipTop, clipBot, scale, background);
        }
        return new int[] { curX, curY };
    }

    private static void flushRun(GuiGraphics g, Font font, StringBuilder run, Style runStyle, int fallbackColor,
                                 int runStartX, int curY, int clipTop, int clipBot, float scale, int background) {
        if (run.isEmpty()) return;
        if (curY >= clipTop && curY + 8 <= clipBot) {
            MutableComponent comp = Component.literal(run.toString()).withStyle(runStyle);
            int color = runStyle.getColor() != null ? (0xFF000000 | runStyle.getColor().getValue()) : fallbackColor;
            int w = Math.round(font.width(comp) * scale);
            if (background != 0) {
                g.fill(runStartX - 1, curY - 1, runStartX + w + 1, curY + 9, background);
            }
            if (scale == 1.0f) {
                g.drawString(font, comp, runStartX, curY, color, false);
            } else {
                g.pose().pushPose();
                g.pose().translate(runStartX, curY, 0);
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, comp, 0, 0, color, false);
                g.pose().popPose();
            }
        }
        run.setLength(0);
    }

    private static int[] measureWords(Font font, String text, Style style, int curX, int curY, int originX, int maxW,
                                      float scale) {
        if (text == null || text.isEmpty()) return new int[] { curX, curY };
        int lineH = Math.round(LINE_H * scale);
        String[] lines = text.split("\n", -1);
        Style running = style;
        for (int li = 0; li < lines.length; li++) {
            if (li > 0) {
                curX = originX;
                curY += lineH;
            }
            for (String token : tokenize(lines[li])) {
                if (token.isBlank() && curX == originX) continue;
                running = applyLegacyCodes(running, token);
                int tokW = Math.round(font.width(Component.literal(token).withStyle(running)) * scale);
                if (curX + tokW > originX + maxW && curX > originX) {
                    curX = originX;
                    curY += lineH;
                }
                curX += tokW;
            }
        }
        return new int[] { curX, curY };
    }

    private static Style applyLegacyCodes(Style base, String token) {
        Style style = base;
        int len = token.length();
        for (int i = 0; i < len - 1; i++) {
            if (token.charAt(i) != '§') continue;
            net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(token.charAt(i + 1));
            if (fmt == null) continue;
            style = switch (fmt) {
                case RESET -> Style.EMPTY;
                case BOLD -> style.withBold(true);
                case ITALIC -> style.withItalic(true);
                case UNDERLINE -> style.withUnderlined(true);
                case STRIKETHROUGH -> style.withStrikethrough(true);
                case OBFUSCATED -> style.withObfuscated(true);
                default -> style.withColor(fmt);
            };
        }
        return style;
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
