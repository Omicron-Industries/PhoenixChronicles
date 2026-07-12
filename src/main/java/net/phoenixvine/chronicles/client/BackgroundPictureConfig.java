package net.phoenixvine.chronicles.client;

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

/**
 * Arbitrary decorative pictures placed directly on a chapter's canvas background - separate from
 * CategoryConfig's chapter-wide theme (background style/tint/single CUSTOM texture). Added via
 * the canvas right-click menu's "Add picture…", freely positioned (shift+drag) and deleted
 * (right-click) independent of anything the theme system controls.
 *
 * Stored at: config/phoenix_chronicles/background_pictures.json, keyed by category.
 */
public final class BackgroundPictureConfig {

    /** One placed picture: canvas-space center position, canvas-space size, texture, opacity. */
    public static class Picture {

        public String texture = "";
        public float x, y;
        public float w = 64f, h = 64f;
        public float opacity = 1.0f;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<Picture>> CACHE = new LinkedHashMap<>();
    private static boolean loaded = false;

    private BackgroundPictureConfig() {}

    /** Live, mutable list for this category - never null, empty if none placed yet. */
    public static List<Picture> get(String category) {
        if (!loaded) load();
        return CACHE.computeIfAbsent(category, c -> new ArrayList<>());
    }

    public static void add(String category, Picture p) {
        get(category).add(p);
        save();
    }

    public static void remove(String category, Picture p) {
        get(category).remove(p);
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
