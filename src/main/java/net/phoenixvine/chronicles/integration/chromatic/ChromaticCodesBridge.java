package net.phoenixvine.chronicles.integration.chromatic;

import net.phoenix.chromatic_codes.ChromaticAPI;
import net.phoenix.chromatic_codes.api.ChromaticColors;

public final class ChromaticCodesBridge {

    private ChromaticCodesBridge() {}

    public static boolean isCode(char code) {
        char lower = Character.toLowerCase(code);
        return ChromaticAPI.isRegistered(lower) || ChromaticAPI.isOutlineCode(lower) ||
                ChromaticColors.CUSTOM_FORMATTING.containsKey(lower);
    }

    public static boolean isNamedCode(String name) {
        return ChromaticAPI.isNamedRegistered(name) || ChromaticAPI.isNamedOutlineCode(name) ||
                ChromaticColors.NAMED_CUSTOM_FORMATTING.containsKey(ChromaticAPI.normalizeNamedKey(name));
    }
}
