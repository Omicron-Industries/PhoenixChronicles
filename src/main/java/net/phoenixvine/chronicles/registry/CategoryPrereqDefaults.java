package net.phoenixvine.chronicles.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-category defaults for the prerequisite unlock gate, letting packdevs set
 * "quests in this category default to ANY-of-prereqs" (etc.) without touching every
 * quest individually. A quest can still override its own gate explicitly — see
 * {@link net.phoenixvine.chronicles.model.QuestNode#getEffectiveRequireAllPrerequisites()}.
 *
 * ── Config file ───────────────────────────────────────────────────────────────
 *
 * config/phoenix_chronicles/category_prereq_defaults.snbt
 *
 * {
 * THE_FACTORY: { require_all: 0b, optional_min_count: 1 }
 * BOSS: { require_all: 1b }
 * }
 *
 * Categories not listed (or fields not listed) fall back to the hardcoded engine
 * default (require_all = true, optional_min_count = 0).
 */
public final class CategoryPrereqDefaults {

    private CategoryPrereqDefaults() {}

    private static final Map<String, Boolean> requireAllByCategory = new ConcurrentHashMap<>();
    private static final Map<String, Integer> optionalMinCountByCategory = new ConcurrentHashMap<>();

    /**
     * Loads category_prereq_defaults.snbt from the given config directory.
     * Called during ServerStartingEvent alongside CategoryFlagRegistry.load().
     * Safe to call multiple times — clears previous state each time.
     */
    public static void load(Path configDir) {
        requireAllByCategory.clear();
        optionalMinCountByCategory.clear();
        Path file = configDir.resolve("category_prereq_defaults.snbt");
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file);
            CompoundTag root = TagParser.parseTag(content);
            for (String key : root.getAllKeys()) {
                if (!(root.get(key) instanceof CompoundTag entry)) continue;
                String category = key.toUpperCase();
                if (entry.contains("require_all")) {
                    requireAllByCategory.put(category, entry.getBoolean("require_all"));
                }
                if (entry.contains("optional_min_count")) {
                    optionalMinCountByCategory.put(category, entry.getInt("optional_min_count"));
                }
            }
            if (!requireAllByCategory.isEmpty() || !optionalMinCountByCategory.isEmpty()) {
                System.out.println(
                        "[Phoenix Chronicles] Loaded prereq defaults for categories: " + requireAllByCategory.keySet());
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load category_prereq_defaults.snbt: " + e.getMessage());
        }
    }

    /** Returns the category's default require-all setting, or null if unconfigured. */
    public static Boolean getRequireAll(String category) {
        if (category == null) return null;
        return requireAllByCategory.get(category.toUpperCase());
    }

    /** Returns the category's default optional-prereq min-count, or null if unconfigured. */
    public static Integer getOptionalMinCount(String category) {
        if (category == null) return null;
        return optionalMinCountByCategory.get(category.toUpperCase());
    }

    /** Clears all loaded defaults (called implicitly by load()). */
    public static void clear() {
        requireAllByCategory.clear();
        optionalMinCountByCategory.clear();
    }
}
