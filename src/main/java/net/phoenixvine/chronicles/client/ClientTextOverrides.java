package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientTextOverrides {

    private static final Map<String, String> overrides = new ConcurrentHashMap<>();

    public static void put(String key, String value) {
        overrides.put(key, value);
    }

    public static String get(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && !"en_us".equalsIgnoreCase(mc.options.languageCode)) return null;
        return overrides.get(key);
    }
}
