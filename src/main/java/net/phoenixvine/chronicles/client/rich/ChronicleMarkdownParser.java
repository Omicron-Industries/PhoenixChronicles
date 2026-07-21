package net.phoenixvine.chronicles.client.rich;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChronicleMarkdownParser {

    private ChronicleMarkdownParser() {}

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern UNORDERED = Pattern.compile("^[-*+]\\s+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^(\\d+)\\.\\s+(.*)$");

    public static List<RichBlock> parse(String input) {
        List<RichBlock> blocks = new ArrayList<>();
        if (input == null || input.isBlank()) return blocks;

        String[] lines = input.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
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
                blocks.add(new RichBlock.Heading(level, parseInline(hm.group(2))));
                lastWasBlank = false;
                i++;
                continue;
            }

            Matcher um = UNORDERED.matcher(trimmed);
            if (um.matches()) {
                blocks.add(new RichBlock.ListItem("â€¢", 10, parseInline(um.group(1))));
                lastWasBlank = false;
                i++;
                continue;
            }

            Matcher om = ORDERED.matcher(trimmed);
            if (om.matches()) {
                String marker = om.group(1) + ".";
                blocks.add(new RichBlock.ListItem(marker, Math.max(14, font_est(marker)), parseInline(om.group(2))));
                lastWasBlank = false;
                i++;
                continue;
            }

            StringBuilder para = new StringBuilder();
            while (i < lines.length) {
                String t = lines[i].trim();
                if (t.isEmpty() || HEADING.matcher(t).matches() || UNORDERED.matcher(t).matches() ||
                        ORDERED.matcher(t).matches()) {
                    break;
                }
                if (para.length() > 0) para.append(' ');
                para.append(t);
                i++;
            }
            blocks.add(new RichBlock.Paragraph(parseInline(para.toString())));
            lastWasBlank = false;
        }

        return blocks;
    }

    private static int font_est(String marker) {
        return 6 * marker.length() + 6;
    }

    private static List<RichSpan> parseInline(String input) {
        List<RichSpan> out = new ArrayList<>();
        if (input == null || input.isEmpty()) return out;

        int len = input.length();
        int i = 0;
        StringBuilder buf = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        while (i < len) {
            char c = input.charAt(i);

            if (c == '{') {
                int end = input.indexOf('}', i + 1);
                if (end > i) {
                    String token = input.substring(i + 1, end);
                    if (token.startsWith("#") && token.length() == 7 && isHex6(token, 1)) {
                        flush(buf, currentStyle, out);
                        currentStyle = currentStyle.withColor(
                                TextColor.fromRgb((int) Long.parseLong(token.substring(1), 16)));
                        i = end + 1;
                        continue;
                    } else if (token.equalsIgnoreCase("reset")) {
                        flush(buf, currentStyle, out);
                        currentStyle = Style.EMPTY;
                        i = end + 1;
                        continue;
                    }
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
                            flush(buf, currentStyle, out);
                            addImage(out, label.substring(4));
                            i = targetEnd + 1;
                            continue;
                        }
                        if (target.startsWith("http://") || target.startsWith("https://")) {
                            flush(buf, currentStyle, out);
                            out.add(new RichSpan.Link(label, currentStyle, target));
                            i = targetEnd + 1;
                            continue;
                        }
                        if (target.startsWith("tip:")) {
                            flush(buf, currentStyle, out);
                            out.add(new RichSpan.Tip(label, currentStyle, target.substring(4)));
                            i = targetEnd + 1;
                            continue;
                        }
                    }
                }

                int imgEnd = input.indexOf(']', i + 1);
                if (imgEnd > i) {
                    String inner = input.substring(i + 1, imgEnd);
                    if (inner.startsWith("img:")) {
                        flush(buf, currentStyle, out);
                        addImage(out, inner.substring(4));
                        i = imgEnd + 1;
                        continue;
                    }
                }
            }

            if (c == '*' && i + 1 < len && input.charAt(i + 1) == '*') {
                flush(buf, currentStyle, out);
                currentStyle = currentStyle.withBold(!currentStyle.isBold());
                i += 2;
                continue;
            }

            if (c == '*' || c == '_') {
                flush(buf, currentStyle, out);
                currentStyle = currentStyle.withItalic(!currentStyle.isItalic());
                i += 1;
                continue;
            }

            buf.append(c);
            i++;
        }

        flush(buf, currentStyle, out);
        return out;
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
            out.add(new RichSpan.Image(new ResourceLocation(rlPart), w, h));
        } catch (Exception ignored) {}
    }

    private static void flush(StringBuilder buf, Style style, List<RichSpan> out) {
        if (buf.isEmpty()) return;
        out.add(new RichSpan.Text(buf.toString(), style));
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

