package net.phoenixvine.chronicles.client.util;

import net.minecraft.client.Minecraft;
import net.phoenixvine.chronicles.flag.PhoenixQuestFlags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CategoryShaderConfig {

    private CategoryShaderConfig() {}

    public static final class CategoryOverride {

        public String condition = "";
        public String shaderId = "";

        public CategoryOverride() {}

        public CategoryOverride(String condition, String shaderId) {
            this.condition = condition != null ? condition : "";
            this.shaderId = shaderId != null ? shaderId : "";
        }

        public CategoryOverride copy() {
            return new CategoryOverride(condition, shaderId);
        }
    }

    private static final class Entry {
        String shaderId = "";
        final List<CategoryOverride> overrides = new ArrayList<>();

        boolean isEmpty() {
            return shaderId.isEmpty() && overrides.isEmpty();
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static boolean loaded = false;

    private static Entry entryOrNull(String categoryId) {
        if (!loaded) load();
        return CACHE.get(categoryId);
    }

    private static Entry entryOrCreate(String categoryId) {
        if (!loaded) load();
        return CACHE.computeIfAbsent(categoryId, k -> new Entry());
    }

    private static void pruneIfEmpty(String categoryId, Entry e) {
        if (e.isEmpty()) CACHE.remove(categoryId);
    }

    public static String get(String categoryId) {
        Entry e = entryOrNull(categoryId);
        return e != null ? e.shaderId : "";
    }

    public static void set(String categoryId, String shaderId) {
        Entry e = entryOrCreate(categoryId);
        e.shaderId = shaderId == null ? "" : shaderId.trim();
        pruneIfEmpty(categoryId, e);
    }

    public static List<CategoryOverride> getOverrides(String categoryId) {
        Entry e = entryOrNull(categoryId);
        return e != null ? Collections.unmodifiableList(e.overrides) : List.of();
    }

    public static void setOverrides(String categoryId, List<CategoryOverride> overrides) {
        Entry e = entryOrCreate(categoryId);
        e.overrides.clear();
        if (overrides != null) {
            for (CategoryOverride o : overrides) {
                if (o != null) e.overrides.add(o.copy());
            }
        }
        pruneIfEmpty(categoryId, e);
    }

    public static String resolve(String categoryId) {
        Entry e = entryOrNull(categoryId);
        if (e == null) return "";
        for (CategoryOverride o : e.overrides) {
            if (PhoenixQuestFlags.evaluate(o.condition, null, "category shader override")) return o.shaderId;
        }
        return e.shaderId;
    }

    public static void invalidate() {
        loaded = false;
        CACHE.clear();
    }

    private static void load() {
        loaded = true;
        CACHE.clear();
        Path p = configPath();
        if (!Files.exists(p)) return;
        try {
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonElement v = e.getValue();
                Entry entry = new Entry();
                if (v.isJsonPrimitive()) {
                    
                    entry.shaderId = v.getAsString();
                } else if (v.isJsonObject()) {
                    JsonObject eo = v.getAsJsonObject();
                    if (eo.has("shader_id")) entry.shaderId = eo.get("shader_id").getAsString();
                    if (eo.has("overrides") && eo.get("overrides").isJsonArray()) {
                        for (JsonElement ovEl : eo.getAsJsonArray("overrides")) {
                            if (!ovEl.isJsonObject()) continue;
                            JsonObject ovo = ovEl.getAsJsonObject();
                            String cond = ovo.has("condition") ? ovo.get("condition").getAsString() : "";
                            String sid = ovo.has("shader_id") ? ovo.get("shader_id").getAsString() : "";
                            entry.overrides.add(new CategoryOverride(cond, sid));
                        }
                    }
                } else {
                    continue;
                }
                if (!entry.isEmpty()) CACHE.put(e.getKey(), entry);
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load category_shaders.json: " + e.getMessage());
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Entry> e : CACHE.entrySet()) {
            Entry entry = e.getValue();
            JsonObject eo = new JsonObject();
            if (!entry.shaderId.isEmpty()) eo.addProperty("shader_id", entry.shaderId);
            if (!entry.overrides.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (CategoryOverride ov : entry.overrides) {
                    JsonObject ovo = new JsonObject();
                    ovo.addProperty("condition", ov.condition);
                    if (!ov.shaderId.isEmpty()) ovo.addProperty("shader_id", ov.shaderId);
                    arr.add(ovo);
                }
                eo.add("overrides", arr);
            }
            root.add(e.getKey(), eo);
        }
        try {
            Path p = configPath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to save category_shaders.json: " + e.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("category_shaders.json");
    }
}
