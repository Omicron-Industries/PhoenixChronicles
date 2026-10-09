package net.phoenixvine.chronicles.client.util;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EffectText {

    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9_]*)[^<>\\n]*>");

    private EffectText() {}

    public static String clean(String text) {
        if (text == null || text.indexOf('<') < 0) return text;
        return TAG.matcher(text).replaceAll(m -> Matcher.quoteReplacement(
                m.group().replaceAll("\\s*=\\s*", "=")));
    }

    public static List<String> wrap(Font font, String text, int maxW) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        text = clean(text);
        for (String paragraph : text.split("\n", -1)) wrapParagraph(font, paragraph, maxW, lines);
        return lines;
    }

    private static void wrapParagraph(Font font, String paragraph, int maxW, List<String> lines) {
        List<String> openTags = new ArrayList<>();
        List<String> openNames = new ArrayList<>();
        String[] active = { "" };
        StringBuilder line = new StringBuilder();
        int lineW = 0;
        int spaceW = Math.max(1, font.width(" "));
        for (String word : splitWords(paragraph)) {
            int w = font.width(strip(word));
            if (lineW > 0 && lineW + spaceW + w > maxW) {
                lines.add(finishLine(line, openNames));
                line = new StringBuilder(reopen(openTags, active[0]));
                lineW = 0;
            }
            if (lineW > 0) {
                line.append(' ');
                lineW += spaceW;
            }
            line.append(word);
            lineW += w;
            scanState(word, openTags, openNames, active);
        }
        if (line.length() > 0 || lines.isEmpty()) lines.add(finishLine(line, openNames));
    }

    private static List<String> splitWords(String paragraph) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        int i = 0;
        while (i < paragraph.length()) {
            char c = paragraph.charAt(i);
            if (c == '<') {
                Matcher m = TAG.matcher(paragraph);
                m.region(i, paragraph.length());
                if (m.lookingAt()) {
                    word.append(paragraph, i, m.end());
                    i = m.end();
                    continue;
                }
            }
            if (c == ' ') {
                words.add(word.toString());
                word.setLength(0);
            } else {
                word.append(c);
            }
            i++;
        }
        words.add(word.toString());
        return words;
    }

    private static String finishLine(StringBuilder line, List<String> openNames) {
        StringBuilder out = new StringBuilder(line);
        for (int t = openNames.size() - 1; t >= 0; t--) out.append("</").append(openNames.get(t)).append('>');
        return out.toString();
    }

    private static String reopen(List<String> openTags, String active) {
        StringBuilder sb = new StringBuilder();
        for (String tag : openTags) sb.append(tag);
        return sb.append(active).toString();
    }

    private static void scanState(String word, List<String> openTags, List<String> openNames, String[] active) {
        int i = 0;
        while (i < word.length()) {
            char c = word.charAt(i);
            if (c == '<') {
                Matcher m = TAG.matcher(word);
                m.region(i, word.length());
                if (m.lookingAt()) {
                    String name = m.group(2);
                    if (m.group(1).isEmpty()) {
                        openTags.add(m.group());
                        openNames.add(name);
                    } else {
                        for (int k = openNames.size() - 1; k >= 0; k--) {
                            if (openNames.get(k).equals(name)) {
                                openNames.remove(k);
                                openTags.remove(k);
                                break;
                            }
                        }
                    }
                    i = m.end();
                    continue;
                }
            }
            if (c == '§' && i + 1 < word.length()) {
                char code = Character.toLowerCase(word.charAt(i + 1));
                int named = ChromaticCodes.namedCodeEnd(word, i);
                if (named != -1) {
                    active[0] = word.substring(i, named + 1);
                    i = named + 1;
                    continue;
                }
                if (ChromaticCodes.isCode(code)) active[0] = "§" + code;
                else if (code == 'r') active[0] = "";
                else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) active[0] = "§" + code;
                else if (code >= 'k' && code <= 'o') active[0] = active[0] + "§" + code;
                i += 2;
                continue;
            }
            i++;
        }
    }

    public static String strip(String text) {
        return text.indexOf('<') < 0 ? text : TAG.matcher(text).replaceAll("");
    }

    public static int width(Font font, String text) {
        return font.width(strip(text));
    }

    public static String cut(Font font, String text, int maxW) {
        if (text.indexOf('<') < 0) return font.plainSubstrByWidth(text, maxW);
        StringBuilder out = new StringBuilder();
        List<String> openTags = new ArrayList<>();
        int used = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '<') {
                Matcher m = TAG.matcher(text);
                m.region(i, text.length());
                if (m.lookingAt()) {
                    String piece = text.substring(i, m.end());
                    out.append(piece);
                    track(m.group(1), m.group(2), openTags);
                    i = m.end();
                    continue;
                }
            }
            if (c == '§' && i + 1 < text.length()) {
                int named = ChromaticCodes.namedCodeEnd(text, i);
                int end = named != -1 ? named + 1 : i + 2;
                out.append(text, i, end);
                i = end;
                continue;
            }
            int len = Character.charCount(text.codePointAt(i));
            String ch = text.substring(i, i + len);
            int w = font.width(ch);
            if (used + w > maxW) break;
            out.append(ch);
            used += w;
            i += len;
        }
        for (int t = openTags.size() - 1; t >= 0; t--) out.append("</").append(openTags.get(t)).append('>');
        return out.toString();
    }

    private static void track(String closing, String name, List<String> openTags) {
        if (!closing.isEmpty()) {
            for (int i = openTags.size() - 1; i >= 0; i--) {
                if (openTags.get(i).equals(name)) {
                    openTags.remove(i);
                    break;
                }
            }
        } else {
            openTags.add(name);
        }
    }
}
