package net.phoenixvine.chronicles.common.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    /**
     * Adds every key from en_us.json that another language file in the pack is missing, using the English text
     * as the starting value, so a freshly created file (fr_fr.json, an empty one included) fills itself in and
     * only has to be translated. Keys a translator already wrote are never touched.
     */
    public static void syncTranslations(Path configDir) {
        Path dir = langDir(configDir);
        Path enFile = dir.resolve("en_us.json");
        if (!Files.exists(enFile)) return;
        try {
            Map<String, String> english = readEntries(enFile);
            if (english.isEmpty()) return;

            List<Path> others;
            try (Stream<Path> files = Files.list(dir)) {
                others = files.filter(f -> f.getFileName().toString().endsWith(".json") &&
                        !f.getFileName().toString().equals("en_us.json")).toList();
            }
            for (Path file : others) {
                try {
                    Map<String, String> existing = readEntries(file);
                    boolean changed = false;
                    for (Map.Entry<String, String> e : english.entrySet()) {
                        if (!existing.containsKey(e.getKey())) {
                            existing.put(e.getKey(), e.getValue());
                            changed = true;
                        }
                    }
                    if (changed) writeAtomically(file, GSON.toJson(existing));
                } catch (Exception e) {
                    System.err.println("[Phoenix Chronicles] Failed to fill " + file.getFileName() + ": " +
                            e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to sync translation files: " + e.getMessage());
        }
    }

    /** Written beside the file and moved over it, so a crash mid-write can't leave a half-written translation. */
    private static void writeAtomically(Path file, String content) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, String> readEntries(Path file) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, String> entries = new LinkedHashMap<>();
        if (raw.isBlank()) return entries;
        Map<String, String> parsed = GSON.fromJson(raw, new TypeToken<Map<String, String>>() {}.getType());
        if (parsed != null) entries.putAll(parsed);
        return entries;
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
                syncTranslations(configDir);
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
            syncTranslations(configDir);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to write lang/en_us.json: " + e.getMessage());
        }
    }
}
