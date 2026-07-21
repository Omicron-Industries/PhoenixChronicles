package net.phoenixvine.chronicles.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CategoryPrereqDefaults {

    private CategoryPrereqDefaults() {}

    private static final Map<String, Boolean> requireAllByCategory = new ConcurrentHashMap<>();
    private static final Map<String, Integer> optionalMinCountByCategory = new ConcurrentHashMap<>();

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

    public static Boolean getRequireAll(String category) {
        if (category == null) return null;
        return requireAllByCategory.get(category.toUpperCase());
    }

    public static Integer getOptionalMinCount(String category) {
        if (category == null) return null;
        return optionalMinCountByCategory.get(category.toUpperCase());
    }

    public static void clear() {
        requireAllByCategory.clear();
        optionalMinCountByCategory.clear();
    }
}

