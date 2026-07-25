package net.phoenixvine.chronicles.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.phoenixvine.chronicles.flag.PhoenixQuestFlags;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChapterFlagRegistry {

    private ChapterFlagRegistry() {}

    private static final Map<String, String> chapterExpressions = new ConcurrentHashMap<>();

    public static void load(Path configDir) {
        chapterExpressions.clear();
        Path file = configDir.resolve("chapter_flags.snbt");
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file);
            CompoundTag tag = TagParser.parseTag(content);
            for (String key : tag.getAllKeys()) {
                String expr = tag.getString(key).trim();
                if (!expr.isEmpty()) {
                    chapterExpressions.put(key.toUpperCase(), expr);
                }
            }
            if (!chapterExpressions.isEmpty()) {
                System.out.println("[Phoenix Chronicles] Loaded chapter flags for: " + chapterExpressions.keySet());
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load chapter_flags.snbt: " + e.getMessage());
        }
    }

    public static boolean isChapterEnabled(String chapter) {
        if (chapter == null) return true;
        String expr = chapterExpressions.get(chapter.toUpperCase());
        if (expr == null) return true;
        return PhoenixQuestFlags.evaluate(expr, null, "chapter " + chapter + " flag");
    }

    public static void clear() {
        chapterExpressions.clear();
    }
}
