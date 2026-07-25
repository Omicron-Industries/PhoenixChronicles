package net.phoenixvine.chronicles.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class QuestLangRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int PACK_FORMAT = 15;

    public static Path langDir(Path configDir) {
        return configDir.resolve("assets").resolve("phoenix_chronicles").resolve("lang");
    }

    public static void ensurePackStructure(Path configDir) {
        try {
            Files.createDirectories(langDir(configDir));
            Path meta = configDir.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                String json = "{\n  \"pack\": {\n    \"pack_format\": " + PACK_FORMAT + ",\n" +
                        "    \"description\": \"Phoenix Chronicles quest translations\"\n  }\n}\n";
                Files.writeString(meta, json, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to set up lang pack structure: " + e.getMessage());
        }
    }

    public static void mergeWrite(Path configDir, Map<String, String> entries) {
        if (entries.isEmpty()) return;
        try {
            ensurePackStructure(configDir);
            Path file = langDir(configDir).resolve("en_us.json");

            Map<String, String> existing = new LinkedHashMap<>();
            if (Files.exists(file)) {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, String> parsed = GSON.fromJson(raw, new TypeToken<Map<String, String>>() {}.getType());
                if (parsed != null) existing.putAll(parsed);
            }
            boolean changed = false;
            for (Map.Entry<String, String> e : entries.entrySet()) {
                if (!existing.containsKey(e.getKey())) {
                    existing.put(e.getKey(), e.getValue());
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(file, GSON.toJson(existing), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to write lang/en_us.json: " + e.getMessage());
        }
    }

    public static void writeKey(Path configDir, String key, String value) {
        try {
            ensurePackStructure(configDir);
            Path file = langDir(configDir).resolve("en_us.json");

            Map<String, String> existing = new LinkedHashMap<>();
            if (Files.exists(file)) {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, String> parsed = GSON.fromJson(raw, new TypeToken<Map<String, String>>() {}.getType());
                if (parsed != null) existing.putAll(parsed);
            }
            existing.put(key, value);
            Files.writeString(file, GSON.toJson(existing), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to write lang/en_us.json: " + e.getMessage());
        }
    }
}
