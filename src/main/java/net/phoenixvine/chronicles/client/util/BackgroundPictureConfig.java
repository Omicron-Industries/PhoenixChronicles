package net.phoenixvine.chronicles.client.util;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BackgroundPictureConfig {

    public static class Picture {

        public String texture = "";
        public float x, y;
        public float w = 64f, h = 64f;
        public float opacity = 1.0f;
        public int color = 0xFFFFFF;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<Picture>> CACHE = new LinkedHashMap<>();
    private static boolean loaded = false;

    private BackgroundPictureConfig() {}

    public static List<Picture> get(String chapter) {
        if (!loaded) load();
        return CACHE.computeIfAbsent(chapter, c -> new ArrayList<>());
    }

    public static void add(String chapter, Picture p) {
        get(chapter).add(p);
        save();
    }

    public static void remove(String chapter, Picture p) {
        get(chapter).remove(p);
        save();
    }

    public static void load() {
        loaded = true;
        CACHE.clear();
        Path p = configPath();
        if (!Files.exists(p)) return;
        try {
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, List<Picture>>>() {}.getType();
            Map<String, List<Picture>> parsed = GSON.fromJson(raw, type);
            if (parsed != null) CACHE.putAll(parsed);
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load background_pictures.json: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            Path p = configPath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(CACHE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to save background_pictures.json: " + e.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("background_pictures.json");
    }
}
