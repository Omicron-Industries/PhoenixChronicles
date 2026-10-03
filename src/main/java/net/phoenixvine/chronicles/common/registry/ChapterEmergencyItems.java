package net.phoenixvine.chronicles.common.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChapterEmergencyItems {

    private ChapterEmergencyItems() {}

    private static final String FILE_NAME = "chapter_emergency_items.snbt";

    private static final Map<String, List<ItemStack>> itemsByChapter = new ConcurrentHashMap<>();

    public static void load(Path configDir) {
        itemsByChapter.clear();
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return;

        try {
            CompoundTag root = TagParser.parseTag(Files.readString(file, StandardCharsets.UTF_8));
            for (String key : root.getAllKeys()) {
                if (!(root.get(key) instanceof ListTag list)) continue;
                List<ItemStack> stacks = new ArrayList<>();
                for (Tag t : list) {
                    if (!(t instanceof CompoundTag c)) continue;
                    ItemStack stack = ItemStack.of(c);
                    if (!stack.isEmpty()) stacks.add(stack);
                }
                if (!stacks.isEmpty()) itemsByChapter.put(key.toUpperCase(), stacks);
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load " + FILE_NAME + ": " + e.getMessage());
        }
    }

    public static List<ItemStack> get(String chapter) {
        if (chapter == null) return List.of();
        List<ItemStack> stored = itemsByChapter.get(chapter.toUpperCase());
        if (stored == null) return List.of();
        List<ItemStack> copy = new ArrayList<>(stored.size());
        for (ItemStack s : stored) copy.add(s.copy());
        return copy;
    }

    public static boolean save(Path configDir, String chapter, List<ItemStack> items) {
        if (chapter == null || chapter.isBlank()) return false;
        Path file = configDir.resolve(FILE_NAME);
        try {
            CompoundTag root = Files.exists(file) ?
                    TagParser.parseTag(Files.readString(file, StandardCharsets.UTF_8)) : new CompoundTag();

            String key = chapter.toUpperCase();
            ListTag list = new ListTag();
            for (ItemStack s : items) {
                if (s != null && !s.isEmpty()) list.add(s.save(new CompoundTag()));
            }
            if (list.isEmpty()) root.remove(key);
            else root.put(key, list);

            Files.createDirectories(configDir);
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
            load(configDir);
            return true;
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to save " + FILE_NAME + ": " + e.getMessage());
            return false;
        }
    }

    public static void clear() {
        itemsByChapter.clear();
    }
}
