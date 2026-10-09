package net.phoenixvine.chronicles.client.util;

import net.minecraftforge.fml.ModList;
import net.phoenixvine.chronicles.integration.chromatic.ChromaticCodesBridge;

import java.util.HashMap;
import java.util.Map;

public final class ChromaticCodes {

    private static final int CACHE_LIMIT = 1024;
    private static final Map<String, String> CACHE = new HashMap<>();

    private static Boolean present;

    private ChromaticCodes() {}

    public static boolean present() {
        if (present == null) {
            ModList mods = ModList.get();
            if (mods == null) return false;
            present = mods.isLoaded("phoenix_chromatic_codes");
        }
        return present;
    }

    public static boolean isCode(char code) {
        return present() && ChromaticCodesBridge.isCode(code);
    }

    public static boolean isNamedCode(String name) {
        return present() && ChromaticCodesBridge.isNamedCode(name);
    }

    public static int namedCodeEnd(String text, int index) {
        if (index + 2 >= text.length() || text.charAt(index) != '§' || text.charAt(index + 1) != '[') return -1;
        int close = text.indexOf(']', index + 2);
        return close == -1 ? -1 : close;
    }

    public static String normalize(String text) {
        if (text == null || text.indexOf('&') < 0 || !present()) return text;
        String cached = CACHE.get(text);
        if (cached != null) return cached;
        String result = convert(text);

        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        CACHE.put(text, result);
        return result;
    }

    private static String convert(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '[') {
                    int close = text.indexOf(']', i + 2);
                    if (close != -1 && isNamedCode(text.substring(i + 2, close))) {
                        sb.append('§').append(text, i + 1, close + 1);
                        i = close;
                        continue;
                    }
                } else if (isCode(next)) {
                    sb.append('§').append(next);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
