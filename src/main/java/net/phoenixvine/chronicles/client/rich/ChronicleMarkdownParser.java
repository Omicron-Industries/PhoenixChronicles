package net.phoenixvine.chronicles.client.rich;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.condition.ConditionExprParser;
import net.phoenixvine.chronicles.common.condition.ConditionNode;
import net.phoenixvine.chronicles.common.condition.ConditionSyntaxException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChronicleMarkdownParser {

    private ChronicleMarkdownParser() {}

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern UNORDERED = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern CHECKBOX = Pattern.compile("^\\[([ xX])]\\s+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^(\\d+)\\.\\s+(.*)$");
    private static final Pattern RULE = Pattern.compile("^(-{3,}|\\*{3,}|_{3,})$");
    private static final Pattern FENCE = Pattern.compile("^```\\s*(\\S*)\\s*$");
    private static final Pattern QUOTE = Pattern.compile("^>\\s?(.*)$");
    private static final Pattern CONTAINER_OPEN = Pattern.compile("^:::(\\S+)\\s*(.*)$");
    private static final Pattern CONTAINER_CLOSE = Pattern.compile("^:::\\s*$");
    private static final Pattern CONTAINER_ELSE = Pattern.compile("^:::else\\s*$");
    private static final Pattern COLLAPSE_MARKER = Pattern.compile("(?i)\\s*\\{collapse}\\s*$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|?.*\\|.*\\|?$");
    private static final Pattern TABLE_SEP = Pattern.compile("^\\|?[\\s:-]*-[\\s:-]*\\|[\\s:|-]*$");
    
    private static final Pattern FOOTNOTE_DEF = Pattern.compile("^\\[\\^([^\\]?]+)(?:\\?([^\\]]+))?]:\\s*(.*)$");
    private static final Pattern SCALE_DIRECTIVE = Pattern.compile("^\\{scale:(\\d+(?:\\.\\d+)?)}$");

    private static final int KBD_COLOR = 0xFFD0D0D8;
    private static final int HIGHLIGHT_BG = 0x66E0C24A;
    private static final int CODE_BG = 0x501A1622;
    private static final int KBD_BG = 0xFF2B2B33;

    public static List<RichBlock> parse(String input) {
        if (input == null || input.isBlank()) return List.of();

        String[] rawLines = input.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        Map<String, List<RichSpan.TipCandidate>> footnotes = new LinkedHashMap<>();
        List<String> filtered = new ArrayList<>(rawLines.length);
        for (String line : rawLines) {
            Matcher fn = FOOTNOTE_DEF.matcher(line.trim());
            if (fn.matches()) {
                String condExpr = fn.group(2);
                ConditionNode condition = null;
                if (condExpr != null && !condExpr.isBlank()) {
                    try {
                        condition = ConditionExprParser.parse(condExpr);
                    } catch (ConditionSyntaxException ignored) {

                    }
                }
                footnotes.computeIfAbsent(fn.group(1), k -> new ArrayList<>())
                        .add(new RichSpan.TipCandidate(condition, fn.group(3)));
            } else {
                filtered.add(line);
            }
        }

        return groupCollapsibleHeadings(parseLines(filtered.toArray(String[]::new), footnotes));
    }

    private static List<RichBlock> groupCollapsibleHeadings(List<RichBlock> blocks) {
        List<RichBlock> out = new ArrayList<>();
        int i = 0;
        while (i < blocks.size()) {
            RichBlock b = blocks.get(i);
            if (b instanceof RichBlock.Heading h && h.collapsible()) {
                int j = i + 1;
                while (j < blocks.size()) {
                    RichBlock next = blocks.get(j);
                    if (next instanceof RichBlock.Heading nh && nh.level() <= h.level()) break;
                    j++;
                }
                List<RichBlock> children = groupCollapsibleHeadings(new ArrayList<>(blocks.subList(i + 1, j)));
                String key = plainTextOf(h.spans()) + "#" + i;
                out.add(new RichBlock.CollapsibleSection(h.level(), h.spans(), key, children));
                i = j;
            } else if (b instanceof RichBlock.Callout c) {
                out.add(new RichBlock.Callout(c.type(), c.title(), groupCollapsibleHeadings(c.children())));
                i++;
            } else if (b instanceof RichBlock.Details d) {
                out.add(new RichBlock.Details(d.expandKey(), d.title(), groupCollapsibleHeadings(d.children())));
                i++;
            } else if (b instanceof RichBlock.ConditionalSection cs) {
                out.add(new RichBlock.ConditionalSection(cs.condition(),
                        groupCollapsibleHeadings(cs.thenChildren()), groupCollapsibleHeadings(cs.elseChildren())));
                i++;
            } else {
                out.add(b);
                i++;
            }
        }
        return out;
    }

    private static String plainTextOf(List<RichSpan> spans) {
        StringBuilder sb = new StringBuilder();
        for (RichSpan s : spans) {
            if (s instanceof RichSpan.Text t) sb.append(t.text());
            else if (s instanceof RichSpan.Link l) sb.append(l.label());
            else if (s instanceof RichSpan.Tip t) sb.append(t.label());
            else if (s instanceof RichSpan.ConditionalTip t) sb.append(t.label());
        }
        return sb.toString();
    }

    private static List<RichBlock> parseLines(String[] lines, Map<String, List<RichSpan.TipCandidate>> footnotes) {
        List<RichBlock> blocks = new ArrayList<>();
        int i = 0;
        boolean lastWasBlank = true;

        while (i < lines.length) {
            String raw = lines[i];
            String trimmed = raw.trim();

            if (trimmed.isEmpty()) {
                if (!lastWasBlank) blocks.add(new RichBlock.Blank());
                lastWasBlank = true;
                i++;
                continue;
            }

            Matcher sd = SCALE_DIRECTIVE.matcher(trimmed);
            if (sd.matches()) {
                try {
                    blocks.add(new RichBlock.ScaleDirective(Float.parseFloat(sd.group(1))));
                } catch (NumberFormatException ignored) {}
                lastWasBlank = false;
                i++;
                continue;
            }

            Matcher hm = HEADING.matcher(trimmed);
            if (hm.matches()) {
                int level = hm.group(1).length();
                String headingText = hm.group(2);

                Matcher cm = COLLAPSE_MARKER.matcher(headingText);
                boolean collapsible = cm.find();
                if (collapsible) headingText = cm.replaceFirst("");
                blocks.add(new RichBlock.Heading(level, parseInline(headingText, footnotes), collapsible));
                lastWasBlank = false;
                i++;
                continue;
            }

            Matcher containerOpen = CONTAINER_OPEN.matcher(trimmed);
            if (containerOpen.matches()) {
                String type = containerOpen.group(1).toLowerCase();
                String title = containerOpen.group(2).trim();
                int depth = 1;
                int start = i + 1;
                int j = start;

                int elseIdx = -1;
                while (j < lines.length && depth > 0) {
                    String t = lines[j].trim();
                    if (CONTAINER_CLOSE.matcher(t).matches()) {
                        depth--;
                        if (depth == 0) break;
                    } else if (type.equals("if") && depth == 1 && elseIdx < 0 &&
                            CONTAINER_ELSE.matcher(t).matches()) {
                                elseIdx = j;
                            } else
                        if (CONTAINER_OPEN.matcher(t).matches()) {
                            depth++;
                        }
                    j++;
                }
                String[] inner = java.util.Arrays.copyOfRange(lines, start, Math.min(j, lines.length));
                if (type.equals("if")) {
                    String[] thenLines, elseLines;
                    if (elseIdx >= 0) {
                        thenLines = java.util.Arrays.copyOfRange(lines, start, elseIdx);
                        elseLines = java.util.Arrays.copyOfRange(lines, elseIdx + 1, Math.min(j, lines.length));
                    } else {
                        thenLines = inner;
                        elseLines = new String[0];
                    }
                    try {
                        ConditionNode condition = ConditionExprParser
                                .parse(title);
                        blocks.add(new RichBlock.ConditionalSection(condition,
                                parseLines(thenLines, footnotes), parseLines(elseLines, footnotes)));
                    } catch (ConditionSyntaxException ex) {
                        blocks.add(new RichBlock.Callout("warning", "Bad :::if condition", List.of(
                                new RichBlock.Paragraph(List.of(new RichSpan.Text("§c" + ex.getMessage(),
                                        Style.EMPTY))))));
                    }
                    lastWasBlank = false;
                    i = j + 1;
                    continue;
                }
                List<RichBlock> children = parseLines(inner, footnotes);
                if (type.equals("spoiler") || type.equals("details")) {
                    String key = (title.isEmpty() ? "section" : title) + "#" + start;
                    blocks.add(new RichBlock.Details(key, title.isEmpty() ? "Details" : title, children));
                } else {
                    blocks.add(new RichBlock.Callout(type, title, children));
                }
                lastWasBlank = false;
                i = j + 1;
                continue;
            }

            Matcher um = UNORDERED.matcher(raw);
            if (um.matches()) {
                int nestLevel = um.group(1).length() / 2;
                Matcher cb = CHECKBOX.matcher(um.group(2));
                if (cb.matches()) {
                    boolean checked = !cb.group(1).equals(" ");
                    String key = "cl#" + i;
                    blocks.add(new RichBlock.Checklist(key, checked, 10 + nestLevel * 10,
                            parseInline(cb.group(2), footnotes)));
                } else {
                    blocks.add(new RichBlock.ListItem("•", 10 + nestLevel * 10,
                            parseInline(um.group(2), footnotes)));
                }
                lastWasBlank = false;
                i++;
                continue;
            }

            Matcher om = ORDERED.matcher(trimmed);
            if (om.matches()) {
                String marker = om.group(1) + ".";
                blocks.add(new RichBlock.ListItem(marker, Math.max(14, font_est(marker)),
                        parseInline(om.group(2), footnotes)));
                lastWasBlank = false;
                i++;
                continue;
            }

            if (RULE.matcher(trimmed).matches()) {
                blocks.add(new RichBlock.Rule());
                lastWasBlank = false;
                i++;
                continue;
            }

            Matcher fm = FENCE.matcher(trimmed);
            if (fm.matches()) {
                String lang = fm.group(1);
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !FENCE.matcher(lines[i].trim()).matches()) {
                    if (code.length() > 0) code.append('\n');
                    code.append(lines[i]);
                    i++;
                }
                if (i < lines.length) i++;
                blocks.add(new RichBlock.CodeBlock(lang, code.toString()));
                lastWasBlank = false;
                continue;
            }

            if (isTableStart(lines, i)) {
                i = parseTable(lines, i, blocks, footnotes);
                lastWasBlank = false;
                continue;
            }

            Matcher qm = QUOTE.matcher(trimmed);
            if (qm.matches()) {
                StringBuilder quote = new StringBuilder();
                while (i < lines.length) {
                    Matcher q = QUOTE.matcher(lines[i].trim());
                    if (!q.matches()) break;
                    if (quote.length() > 0) quote.append(' ');
                    quote.append(q.group(1));
                    i++;
                }
                blocks.add(new RichBlock.Quote(parseInline(quote.toString(), footnotes)));
                lastWasBlank = false;
                continue;
            }

            StringBuilder para = new StringBuilder();
            while (i < lines.length) {
                String t = lines[i].trim();
                if (t.isEmpty() || HEADING.matcher(t).matches() || UNORDERED.matcher(t).matches() ||
                        ORDERED.matcher(t).matches() || RULE.matcher(t).matches() || FENCE.matcher(t).matches() ||
                        QUOTE.matcher(t).matches() || CONTAINER_OPEN.matcher(t).matches() ||
                        CONTAINER_CLOSE.matcher(t).matches() || isTableStart(lines, i)) {
                    break;
                }
                if (para.length() > 0) para.append(' ');
                para.append(t);
                i++;
            }
            blocks.add(new RichBlock.Paragraph(parseInline(para.toString(), footnotes)));
            lastWasBlank = false;
        }

        return blocks;
    }

    private static boolean isTableStart(String[] lines, int i) {
        if (i + 1 >= lines.length) return false;
        String row = lines[i].trim();
        String sep = lines[i + 1].trim();
        return row.contains("|") && TABLE_ROW.matcher(row).matches() && TABLE_SEP.matcher(sep).matches();
    }

    private static int parseTable(String[] lines, int i, List<RichBlock> blocks,
                                  Map<String, List<RichSpan.TipCandidate>> footnotes) {
        List<List<RichSpan>> header = new ArrayList<>();
        for (String cell : splitRow(lines[i])) header.add(parseInline(cell.trim(), footnotes));
        i += 2;

        List<List<List<RichSpan>>> rows = new ArrayList<>();
        while (i < lines.length) {
            String t = lines[i].trim();
            if (t.isEmpty() || !t.contains("|") || !TABLE_ROW.matcher(t).matches()) break;
            List<List<RichSpan>> row = new ArrayList<>();
            for (String cell : splitRow(lines[i])) row.add(parseInline(cell.trim(), footnotes));
            rows.add(row);
            i++;
        }

        blocks.add(new RichBlock.Table(header, rows));
        return i;
    }

    private static String[] splitRow(String line) {
        String t = line.trim();
        if (t.startsWith("|")) t = t.substring(1);
        if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
        return t.split("\\|", -1);
    }

    private static int font_est(String marker) {
        return 6 * marker.length() + 6;
    }

    private static List<RichSpan> parseInline(String input, Map<String, List<RichSpan.TipCandidate>> footnotes) {
        List<RichSpan> out = new ArrayList<>();
        if (input == null || input.isEmpty()) return out;

        input = smartQuotes(input);
        int len = input.length();
        int i = 0;
        StringBuilder buf = new StringBuilder();
        Style currentStyle = Style.EMPTY;
        int currentBackground = 0;
        float currentScale = 1f;

        while (i < len) {
            char c = input.charAt(i);

            if (c == '{') {
                int end = input.indexOf('}', i + 1);
                if (end > i) {
                    String token = input.substring(i + 1, end);
                    if (token.startsWith("#") && token.length() == 7 && isHex6(token, 1)) {
                        flush(buf, currentStyle, currentBackground, currentScale, out);
                        currentStyle = currentStyle.withColor(
                                TextColor.fromRgb((int) Long.parseLong(token.substring(1), 16)));
                        i = end + 1;
                        continue;
                    } else if (token.equalsIgnoreCase("reset")) {
                        flush(buf, currentStyle, currentBackground, currentScale, out);
                        currentStyle = Style.EMPTY;
                        currentScale = 1f;
                        i = end + 1;
                        continue;
                    } else if (token.toLowerCase(java.util.Locale.ROOT).startsWith("scale:")) {
                        try {
                            float parsed = Float.parseFloat(token.substring(6));
                            flush(buf, currentStyle, currentBackground, currentScale, out);
                            currentScale = parsed;
                            i = end + 1;
                            continue;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (c == '[' && i + 1 < len && input.charAt(i + 1) == '^') {
                int end = input.indexOf(']', i + 2);
                if (end > i) {
                    String id = input.substring(i + 2, end);
                    List<RichSpan.TipCandidate> candidates = footnotes.get(id);
                    flush(buf, currentStyle, currentBackground, currentScale, out);
                    Style tipStyle = currentStyle.withColor(TextColor.fromRgb(0xFFAAFFAA));
                    if (candidates == null) {
                        out.add(new RichSpan.Text("[^" + id + "]", currentStyle));
                    } else if (candidates.size() == 1 && candidates.get(0).condition() == null) {

                        out.add(new RichSpan.Tip("[" + id + "]", tipStyle, candidates.get(0).tooltip()));
                    } else {
                        out.add(new RichSpan.ConditionalTip("[" + id + "]", tipStyle, candidates));
                    }
                    i = end + 1;
                    continue;
                }
            }

            if (c == '[') {
                int labelEnd = input.indexOf(']', i + 1);
                if (labelEnd > i && labelEnd + 1 < len && input.charAt(labelEnd + 1) == '(') {
                    int targetEnd = input.indexOf(')', labelEnd + 2);
                    if (targetEnd > labelEnd + 1) {
                        String label = input.substring(i + 1, labelEnd);
                        String target = input.substring(labelEnd + 2, targetEnd);

                        if (label.startsWith("img:")) {
                            flush(buf, currentStyle, currentBackground, currentScale, out);
                            addImage(out, label.substring(4));
                            i = targetEnd + 1;
                            continue;
                        }
                        if (target.startsWith("http://") || target.startsWith("https://") ||
                                target.startsWith("wiki:")) {
                            flush(buf, currentStyle, currentBackground, currentScale, out);
                            out.add(new RichSpan.Link(label, currentStyle, target));
                            i = targetEnd + 1;
                            continue;
                        }
                        if (target.startsWith("tip:")) {
                            flush(buf, currentStyle, currentBackground, currentScale, out);
                            out.add(new RichSpan.Tip(label, currentStyle, target.substring(4)));
                            i = targetEnd + 1;
                            continue;
                        }
                    }
                }

                int bracketEnd = input.indexOf(']', i + 1);
                if (bracketEnd > i) {
                    String inner = input.substring(i + 1, bracketEnd);
                    if (inner.startsWith("img:")) {
                        flush(buf, currentStyle, currentBackground, currentScale, out);
                        addImage(out, inner.substring(4));
                        i = bracketEnd + 1;
                        continue;
                    }
                    if (inner.startsWith("item:")) {
                        flush(buf, currentStyle, currentBackground, currentScale, out);
                        String itemPart = inner.substring(5);
                        int bar = itemPart.indexOf('|');
                        String idPart = bar >= 0 ? itemPart.substring(0, bar) : itemPart;
                        String tooltip = bar >= 0 ? itemPart.substring(bar + 1).trim() : null;
                        if (tooltip != null && tooltip.isEmpty()) tooltip = null;
                        try {
                            out.add(new RichSpan.ItemIcon(ResourceLocation.parse(idPart.trim()), tooltip));
                        } catch (Exception ignored) {}
                        i = bracketEnd + 1;
                        continue;
                    }
                }
            }

            if (c == '`') {
                int end = input.indexOf('`', i + 1);
                if (end > i) {
                    flush(buf, currentStyle, currentBackground, currentScale, out);
                    String codeText = input.substring(i + 1, end);
                    out.add(new RichSpan.Text(codeText,
                            currentStyle.withColor(TextColor.fromRgb(0xFFD37A)), CODE_BG, codeText));
                    i = end + 1;
                    continue;
                }
            }

            if (input.startsWith("<kbd>", i)) {
                int end = input.indexOf("</kbd>", i + 5);
                if (end > i) {
                    flush(buf, currentStyle, currentBackground, currentScale, out);
                    out.add(new RichSpan.Text(" " + input.substring(i + 5, end) + " ",
                            currentStyle.withColor(TextColor.fromRgb(KBD_COLOR)).withBold(true), KBD_BG));
                    i = end + 6;
                    continue;
                }
            }

            if (c == '~' && i + 1 < len && input.charAt(i + 1) == '~') {
                flush(buf, currentStyle, currentBackground, currentScale, out);
                currentStyle = currentStyle.withStrikethrough(!currentStyle.isStrikethrough());
                i += 2;
                continue;
            }

            if (c == '=' && i + 1 < len && input.charAt(i + 1) == '=') {
                flush(buf, currentStyle, currentBackground, currentScale, out);
                currentBackground = currentBackground == HIGHLIGHT_BG ? 0 : HIGHLIGHT_BG;
                i += 2;
                continue;
            }

            if (c == '*' && i + 1 < len && input.charAt(i + 1) == '*') {
                flush(buf, currentStyle, currentBackground, currentScale, out);
                currentStyle = currentStyle.withBold(!currentStyle.isBold());
                i += 2;
                continue;
            }

            if (c == '*') {
                flush(buf, currentStyle, currentBackground, currentScale, out);
                currentStyle = currentStyle.withItalic(!currentStyle.isItalic());
                i += 1;
                continue;
            }

            buf.append(c);
            i++;
        }

        flush(buf, currentStyle, currentBackground, currentScale, out);
        return out;
    }

    private static String smartQuotes(String input) {
        if (input.indexOf('"') < 0) return input;
        StringBuilder out = new StringBuilder(input.length());
        boolean inCode = false;
        boolean openNext = true;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '`') {
                inCode = !inCode;
                out.append(c);
            } else if (c == '"' && !inCode) {
                out.append(openNext ? '“' : '”');
                openNext = !openNext;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static void addImage(List<RichSpan> out, String rlPart) {
        int w = 48, h = 48;
        String[] parts = rlPart.split(",", 3);
        rlPart = parts[0].trim();
        if (parts.length >= 3) {
            try {
                w = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {}
            try {
                h = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException ignored) {}
        }
        try {
            out.add(new RichSpan.Image(ResourceLocation.parse(rlPart), w, h));
        } catch (Exception ignored) {}
    }

    private static void flush(StringBuilder buf, Style style, int background, float scale, List<RichSpan> out) {
        if (buf.isEmpty()) return;
        out.add(new RichSpan.Text(buf.toString(), style, background, null, scale));
        buf.setLength(0);
    }

    private static boolean isHex6(String s, int offset) {
        if (offset + 6 > s.length()) return false;
        for (int i = offset; i < offset + 6; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }
}
