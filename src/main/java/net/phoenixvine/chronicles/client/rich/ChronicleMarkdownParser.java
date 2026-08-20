package net.phoenixvine.chronicles.client.rich;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

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
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|?.*\\|.*\\|?$");
    private static final Pattern TABLE_SEP = Pattern.compile("^\\|?[\\s:-]*-[\\s:-]*\\|[\\s:|-]*$");
    private static final Pattern FOOTNOTE_DEF = Pattern.compile("^\\[\\^([^\\]]+)]:\\s*(.*)$");

    private static final int KBD_COLOR = 0xFFD0D0D8;
    private static final int HIGHLIGHT_BG = 0x66E0C24A;
    private static final int CODE_BG = 0x501A1622;
    private static final int KBD_BG = 0xFF2B2B33;

    public static List<RichBlock> parse(String input) {
        if (input == null || input.isBlank()) return List.of();

        String[] rawLines = input.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        Map<String, String> footnotes = new LinkedHashMap<>();
        List<String> filtered = new ArrayList<>(rawLines.length);
        for (String line : rawLines) {
            Matcher fn = FOOTNOTE_DEF.matcher(line.trim());
            if (fn.matches()) {
                footnotes.put(fn.group(1), fn.group(2));
            } else {
                filtered.add(line);
            }
        }

        return parseLines(filtered.toArray(String[]::new), footnotes);
    }

    private static List<RichBlock> parseLines(String[] lines, Map<String, String> footnotes) {
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

            Matcher hm = HEADING.matcher(trimmed);
            if (hm.matches()) {
                int level = hm.group(1).length();
                blocks.add(new RichBlock.Heading(level, parseInline(hm.group(2), footnotes)));
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
                while (j < lines.length && depth > 0) {
                    String t = lines[j].trim();
                    if (CONTAINER_CLOSE.matcher(t).matches()) {
                        depth--;
                        if (depth == 0) break;
                    } else if (CONTAINER_OPEN.matcher(t).matches()) {
                        depth++;
                    }
                    j++;
                }
                String[] inner = java.util.Arrays.copyOfRange(lines, start, Math.min(j, lines.length));
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

    private static int parseTable(String[] lines, int i, List<RichBlock> blocks, Map<String, String> footnotes) {
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

    private static List<RichSpan> parseInline(String input, Map<String, String> footnotes) {
        List<RichSpan> out = new ArrayList<>();
        if (input == null || input.isEmpty()) return out;

        input = smartQuotes(input);
        int len = input.length();
        int i = 0;
        StringBuilder buf = new StringBuilder();
        Style currentStyle = Style.EMPTY;
        int currentBackground = 0;

        while (i < len) {
            char c = input.charAt(i);

            if (c == '{') {
                int end = input.indexOf('}', i + 1);
                if (end > i) {
                    String token = input.substring(i + 1, end);
                    if (token.startsWith("#") && token.length() == 7 && isHex6(token, 1)) {
                        flush(buf, currentStyle, currentBackground, out);
                        currentStyle = currentStyle.withColor(
                                TextColor.fromRgb((int) Long.parseLong(token.substring(1), 16)));
                        i = end + 1;
                        continue;
                    } else if (token.equalsIgnoreCase("reset")) {
                        flush(buf, currentStyle, currentBackground, out);
                        currentStyle = Style.EMPTY;
                        i = end + 1;
                        continue;
                    }
                }
            }

            if (c == '[' && i + 1 < len && input.charAt(i + 1) == '^') {
                int end = input.indexOf(']', i + 2);
                if (end > i) {
                    String id = input.substring(i + 2, end);
                    String def = footnotes.get(id);
                    flush(buf, currentStyle, currentBackground, out);
                    if (def != null) {
                        out.add(new RichSpan.Tip("[" + id + "]",
                                currentStyle.withColor(TextColor.fromRgb(0xFFAAFFAA)), def));
                    } else {
                        out.add(new RichSpan.Text("[^" + id + "]", currentStyle));
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
                            flush(buf, currentStyle, currentBackground, out);
                            addImage(out, label.substring(4));
                            i = targetEnd + 1;
                            continue;
                        }
                        if (target.startsWith("http://") || target.startsWith("https://") ||
                                target.startsWith("wiki:")) {
                            flush(buf, currentStyle, currentBackground, out);
                            out.add(new RichSpan.Link(label, currentStyle, target));
                            i = targetEnd + 1;
                            continue;
                        }
                        if (target.startsWith("tip:")) {
                            flush(buf, currentStyle, currentBackground, out);
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
                        flush(buf, currentStyle, currentBackground, out);
                        addImage(out, inner.substring(4));
                        i = bracketEnd + 1;
                        continue;
                    }
                    if (inner.startsWith("item:")) {
                        flush(buf, currentStyle, currentBackground, out);
                        try {
                            out.add(new RichSpan.ItemIcon(ResourceLocation.parse(inner.substring(5).trim())));
                        } catch (Exception ignored) {}
                        i = bracketEnd + 1;
                        continue;
                    }
                }
            }

            if (c == '`') {
                int end = input.indexOf('`', i + 1);
                if (end > i) {
                    flush(buf, currentStyle, currentBackground, out);
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
                    flush(buf, currentStyle, currentBackground, out);
                    out.add(new RichSpan.Text(" " + input.substring(i + 5, end) + " ",
                            currentStyle.withColor(TextColor.fromRgb(KBD_COLOR)).withBold(true), KBD_BG));
                    i = end + 6;
                    continue;
                }
            }

            if (c == '~' && i + 1 < len && input.charAt(i + 1) == '~') {
                flush(buf, currentStyle, currentBackground, out);
                currentStyle = currentStyle.withStrikethrough(!currentStyle.isStrikethrough());
                i += 2;
                continue;
            }

            if (c == '=' && i + 1 < len && input.charAt(i + 1) == '=') {
                flush(buf, currentStyle, currentBackground, out);
                currentBackground = currentBackground == HIGHLIGHT_BG ? 0 : HIGHLIGHT_BG;
                i += 2;
                continue;
            }

            if (c == '*' && i + 1 < len && input.charAt(i + 1) == '*') {
                flush(buf, currentStyle, currentBackground, out);
                currentStyle = currentStyle.withBold(!currentStyle.isBold());
                i += 2;
                continue;
            }

            if (c == '*') {
                flush(buf, currentStyle, currentBackground, out);
                currentStyle = currentStyle.withItalic(!currentStyle.isItalic());
                i += 1;
                continue;
            }

            buf.append(c);
            i++;
        }

        flush(buf, currentStyle, currentBackground, out);
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

    private static void flush(StringBuilder buf, Style style, int background, List<RichSpan> out) {
        if (buf.isEmpty()) return;
        out.add(new RichSpan.Text(buf.toString(), style, background));
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
