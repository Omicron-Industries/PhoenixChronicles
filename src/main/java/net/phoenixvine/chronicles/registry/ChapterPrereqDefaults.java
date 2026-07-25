package net.phoenixvine.chronicles.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChapterPrereqDefaults {

    private ChapterPrereqDefaults() {}

    private static final Map<String, Boolean> requireAllByChapter = new ConcurrentHashMap<>();
    private static final Map<String, Integer> optionalMinCountByChapter = new ConcurrentHashMap<>();

    public static void load(Path configDir) {
        requireAllByChapter.clear();
        optionalMinCountByChapter.clear();
        Path file = configDir.resolve("chapter_prereq_defaults.snbt");
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file);
            CompoundTag root = TagParser.parseTag(content);
            for (String key : root.getAllKeys()) {
                if (!(root.get(key) instanceof CompoundTag entry)) continue;
                String chapter = key.toUpperCase();
                if (entry.contains("require_all")) {
                    requireAllByChapter.put(chapter, entry.getBoolean("require_all"));
                }
                if (entry.contains("optional_min_count")) {
                    optionalMinCountByChapter.put(chapter, entry.getInt("optional_min_count"));
                }
            }
            if (!requireAllByChapter.isEmpty() || !optionalMinCountByChapter.isEmpty()) {
                System.out.println(
                        "[Phoenix Chronicles] Loaded prereq defaults for chapters: " + requireAllByChapter.keySet());
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load chapter_prereq_defaults.snbt: " + e.getMessage());
        }
    }

    public static Boolean getRequireAll(String chapter) {
        if (chapter == null) return null;
        return requireAllByChapter.get(chapter.toUpperCase());
    }

    public static Integer getOptionalMinCount(String chapter) {
        if (chapter == null) return null;
        return optionalMinCountByChapter.get(chapter.toUpperCase());
    }

    public static void clear() {
        requireAllByChapter.clear();
        optionalMinCountByChapter.clear();
    }
}
