package net.phoenixvine.chronicles.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientTextOverrides {

    private static final Map<String, String> overrides = new ConcurrentHashMap<>();

    public static void put(String key, String value) {
        overrides.put(key, value);
    }

    public static String get(String key) {
        return overrides.get(key);
    }
}
