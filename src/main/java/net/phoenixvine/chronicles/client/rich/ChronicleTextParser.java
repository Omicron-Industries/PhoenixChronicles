package net.phoenixvine.chronicles.client.rich;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class ChronicleTextParser {

    private ChronicleTextParser() {}

    public static List<RichSpan> parse(String input) {
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
                        currentStyle = Style.EMPTY.withColor(
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
                            String rlPart = label.substring(4);
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
                        String rlPart = inner.substring(4);
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
                        i = imgEnd + 1;
                        continue;
                    }
                }
            }

            buf.append(c);
            i++;
        }

        flush(buf, currentStyle, out);
        return out;
    }

    public static String toPlain(String input) {
        if (input == null) return "";
        return input
                .replaceAll("\\{#[0-9a-fA-F]{6}}", "")
                .replaceAll("(?i)\\{reset}", "")
                .replaceAll("\\[img:[^]]*](?:\\([^)]*\\))?", "")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1");
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
