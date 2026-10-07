package net.phoenixvine.chronicles.common.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.codec.QuestFileWatcher;
import net.phoenixvine.chronicles.common.model.EmergencyKit;

import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-chapter default emergency kits, used by quests in the chapter that have no kit of their own. */
public final class ChapterEmergencyItems {

    private ChapterEmergencyItems() {}

    private static final String FILE_NAME = "chapter_emergency_items.snbt";

    private static final Map<String, EmergencyKit> kitsByChapter = new ConcurrentHashMap<>();

    public static void load(Path configDir) {
        kitsByChapter.clear();
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return;

        try {
            CompoundTag root = TagParser.parseTag(Files.readString(file, StandardCharsets.UTF_8));
            for (String key : root.getAllKeys()) {
                EmergencyKit kit;
                if (root.get(key) instanceof ListTag legacyItems) {
                    kit = EmergencyKit.fromLegacyItems(legacyItems);
                } else if (root.get(key) instanceof CompoundTag kitTag) {
                    kit = EmergencyKit.fromNBT(kitTag);
                } else {
                    continue;
                }
                if (!kit.isDefault()) kitsByChapter.put(key.toUpperCase(), kit);
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load " + FILE_NAME + ": " + e.getMessage());
        }
    }

    /** A copy of the chapter's kit; empty (with every setting inherited) when the chapter has none. */
    public static EmergencyKit get(String chapter) {
        if (chapter == null) return new EmergencyKit();
        EmergencyKit stored = kitsByChapter.get(chapter.toUpperCase());
        return stored == null ? new EmergencyKit() : stored.copy();
    }

    public static boolean save(Path configDir, String chapter, EmergencyKit kit) {
        if (chapter == null || chapter.isBlank()) return false;
        Path file = configDir.resolve(FILE_NAME);
        try {
            CompoundTag root = Files.exists(file) ?
                    TagParser.parseTag(Files.readString(file, StandardCharsets.UTF_8)) : new CompoundTag();

            String key = chapter.toUpperCase();
            if (kit.isDefault()) root.remove(key);
            else root.put(key, kit.serializeNBT());

            Files.createDirectories(configDir);
            // Any .snbt written under the config folder makes the file watcher reload every quest from disk and
            // resync all clients. This write is already reflected in memory, so skip that reload.
            QuestFileWatcher.suppressNextReload();
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
            load(configDir);
            return true;
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to save " + FILE_NAME + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Chapter and questbook kits aren't tied to any quest, so they can be claimed even when every quest in the
     * chapter is locked - which is the point, for a softlock. Each one is claimed under a stable id of its own so
     * the player's claim history and cooldown are tracked like a quest's.
     */
    public static final String QUESTBOOK_KEY = "_QUESTBOOK";
    private static final String STATION_NAMESPACE = "phoenix_chronicles";
    private static final String STATION_PREFIX = "_emergency_station/";
    public static final ResourceLocation QUESTBOOK_STATION = ResourceLocation.fromNamespaceAndPath(STATION_NAMESPACE,
            STATION_PREFIX + "questbook");

    public static EmergencyKit getQuestbook() {
        return get(QUESTBOOK_KEY);
    }

    public static boolean saveQuestbook(Path configDir, EmergencyKit kit) {
        return save(configDir, QUESTBOOK_KEY, kit);
    }

    public static ResourceLocation chapterStation(String chapter) {
        String sanitized = chapter.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return ResourceLocation.fromNamespaceAndPath(STATION_NAMESPACE, STATION_PREFIX + "chapter/" + sanitized);
    }

    public static boolean isStation(ResourceLocation id) {
        return id != null && STATION_NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(STATION_PREFIX);
    }

    /** Chapters (not the questbook) that currently have a kit with rewards. */
    public static List<String> chaptersWithKits() {
        List<String> chapters = new ArrayList<>();
        for (Map.Entry<String, EmergencyKit> e : kitsByChapter.entrySet()) {
            if (!e.getKey().equals(QUESTBOOK_KEY) && e.getValue().hasRewards()) chapters.add(e.getKey());
        }
        return chapters;
    }

    /** The kit a station id stands for, or null when it doesn't exist (any more). */
    @Nullable
    public static EmergencyKit stationKit(ResourceLocation id) {
        if (QUESTBOOK_STATION.equals(id)) {
            EmergencyKit kit = getQuestbook();
            return kit.hasRewards() ? kit : null;
        }
        for (String chapter : chaptersWithKits()) {
            if (chapterStation(chapter).equals(id)) return get(chapter);
        }
        return null;
    }

    public static String stationLabel(ResourceLocation id) {
        if (QUESTBOOK_STATION.equals(id)) return "Entire questbook";
        for (String chapter : chaptersWithKits()) {
            if (chapterStation(chapter).equals(id)) {
                String lower = chapter.toLowerCase(Locale.ROOT).replace('_', ' ');
                return "Chapter: " + Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }
        }
        return id.getPath();
    }

    public static void clear() {
        kitsByChapter.clear();
    }
}
