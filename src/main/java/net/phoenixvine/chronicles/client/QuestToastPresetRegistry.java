package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestToastPresetRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, QuestToastConfig> CACHE = new LinkedHashMap<>();
    private static boolean loaded = false;

    private QuestToastPresetRegistry() {}

    public static void put(String name, QuestToastConfig cfg) {
        if (!loaded) load();
        CACHE.put(name, cfg);
        save();
    }

    public static QuestToastConfig getOrNull(String name) {
        if (!loaded) load();
        return CACHE.get(name);
    }

    public static void remove(String name) {
        if (!loaded) load();
        CACHE.remove(name);
        save();
    }

    public static List<String> names() {
        if (!loaded) load();
        return new ArrayList<>(CACHE.keySet());
    }

    public static void load() {
        loaded = true;
        CACHE.clear();
        Path p = configPath();
        if (!Files.exists(p)) return;
        try {
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                QuestToastConfig cfg = GSON.fromJson(e.getValue(), QuestToastConfig.class);
                if (cfg != null) CACHE.put(e.getKey(), cfg);
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load toast_presets.json: " + e.getMessage());
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, QuestToastConfig> e : CACHE.entrySet())
            root.add(e.getKey(), GSON.toJsonTree(e.getValue()));
        try {
            Path p = configPath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to save toast_presets.json: " + e.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("toast_presets.json");
    }
}

