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

/**
 * Manages the on-disk lang files behind Phoenix Chronicles' REAL per-player translation
 * support. Quest title/description/task text is exposed as ordinary Minecraft translation
 * keys ("phoenix_chronicles.quest.<id>.title", "phoenix_chronicles.task.<id>", etc.), and
 * config/phoenix_chronicles is registered as an always-active resource pack (see
 * net.phoenixvine.chronicles.client.ChroniclesLangPack) so each CLIENT's own Language system
 * resolves those keys using THEIR OWN selected game language - the same mechanism vanilla
 * item/block names use. This class only manages the underlying files; resolution itself
 * happens through vanilla I18n/Language, not through any Java-side lookup here.
 */
public class QuestLangRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int PACK_FORMAT = 15; // MC 1.20.1 resource pack format

    public static Path langDir(Path configDir) {
        return configDir.resolve("assets").resolve("phoenix_chronicles").resolve("lang");
    }

    /** Ensures configDir is a valid resource-pack root: pack.mcmeta + assets/phoenix_chronicles/lang/. */
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
     * Merge-writes {@code entries} into
     * config/phoenix_chronicles/assets/phoenix_chronicles/lang/en_us.json, WITHOUT overwriting
     * any key that already exists there (so a prior translation or manual edit is never
     * clobbered by a re-import). Does not itself trigger a resource reload - callers on the
     * client should follow up with ChroniclesLangPack.reload() so the new keys take effect
     * immediately.
     */
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

    /**
     * Writes a single key into en_us.json, OVERWRITING any existing value - unlike
     * {@link #mergeWrite}, this is for when the packdev is directly and deliberately editing
     * this one English default (e.g. a category display name), not bulk-importing content
     * that shouldn't clobber prior translations/edits.
     */
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
