package net.phoenixvine.chronicles.client.rich;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Chronicles description text (from either the .snbt "description" field or a .md
 * companion file) into a list of {@link RichBlock}s — proper block-level Markdown structure,
 * not just a flat list of styled spans.
 *
 * This exists because two different render paths (the compact card and the fullscreen panel)
 * used to disagree about what a description even *was*: one called vanilla Font.split() on the
 * raw string (which doesn't treat "\n" as a forced break), the other manually pre-split on "\n"
 * before word-wrapping. Both approaches only ever supported one flat run of inline styling with
 * no real headings/lists. This parser is now the single source of truth for structure; both
 * render paths should go through {@link ChronicleRichTextRenderer#renderBlocks} instead.
 *
 * BLOCK SYNTAX:
 * - Blank line(s) → paragraph break (collapsed to one {@link RichBlock.Blank})
 * - "# " .. "###### " → {@link RichBlock.Heading}, level = number of leading #'s
 * - "- ", "* ", "+ " → unordered {@link RichBlock.ListItem}
 * - "1. ", "2. ", ... → ordered {@link RichBlock.ListItem}
 * - anything else → {@link RichBlock.Paragraph}; consecutive non-blank,
 * non-heading, non-list lines are joined with a single space
 * (Markdown "soft break" — matches standard MD semantics)
 *
 * INLINE SYNTAX (within any block's text, same as {@link ChronicleTextParser} plus emphasis):
 * {#RRGGBB} hex color, {reset} reset, **bold**, *italic* / _italic_,
 * [label](url) link, [label](tip:msg) tooltip, [img:rl] / [img:rl,w,h] inline image.
 *
 * DESIGN INVARIANT — & is never touched; § codes pass straight through to Minecraft's renderer,
 * same as {@link ChronicleTextParser}.
 */
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
        boolean lastWasBlank = true; // suppress a leading Blank block

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
                blocks.add(new RichBlock.ListItem("•", 10, parseInline(um.group(1))));
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

            // Paragraph: gobble consecutive plain lines, joined with a single space.
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

    /** Rough indent width estimate for ordered-list markers without needing a Font reference here. */
    private static int font_est(String marker) {
        return 6 * marker.length() + 6;
    }

    // ── Inline parser (ChronicleTextParser's syntax, plus **bold** / *italic* / _italic_) ─────

    private static List<RichSpan> parseInline(String input) {
        List<RichSpan> out = new ArrayList<>();
        if (input == null || input.isEmpty()) return out;

        int len = input.length();
        int i = 0;
        StringBuilder buf = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        while (i < len) {
            char c = input.charAt(i);

            // ── {#RRGGBB} hex color or {reset} ───────────────────────────────
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

            // ── [label](target) link, tooltip, or [img:rl] ───────────────────
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

            // ── **bold** ──────────────────────────────────────────────────────
            if (c == '*' && i + 1 < len && input.charAt(i + 1) == '*') {
                flush(buf, currentStyle, out);
                currentStyle = currentStyle.withBold(!currentStyle.isBold());
                i += 2;
                continue;
            }

            // ── *italic* / _italic_ ─────────────────────────────────────────
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
