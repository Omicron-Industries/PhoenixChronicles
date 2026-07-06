package net.phoenixvine.chronicles.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages named chapter folders that group sidebar categories into collapsible sections.
 *
 * Persisted as config/phoenix_chronicles/chapter_folders.snbt:
 * [{id:"main", label:"Main Story", categories:["MAIN","SIDE_QUESTS"]}, ...]
 *
 * Categories not listed in any folder appear ungrouped at the top of the sidebar.
 * Collapsed state is purely client-side (never written to disk).
 */
public final class ChapterFolderRegistry {

    public record ChapterFolder(String id, String label, List<String> categories) {}

    private static final List<ChapterFolder> folders = new ArrayList<>();
    private static final Set<String> collapsed = new HashSet<>();
    private static Path lastPath = null;

    private ChapterFolderRegistry() {}

    public static List<ChapterFolder> getFolders() {
        return Collections.unmodifiableList(folders);
    }

    public static boolean isCollapsed(String folderId) {
        return collapsed.contains(folderId);
    }

    public static void toggleCollapsed(String folderId) {
        if (!collapsed.remove(folderId)) collapsed.add(folderId);
    }

    /** Returns the folder that contains the given category, or null if ungrouped. */
    public static ChapterFolder folderFor(String category) {
        for (ChapterFolder f : folders)
            if (f.categories().contains(category.toUpperCase())) return f;
        return null;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static void load(Path configDir) {
        folders.clear();
        lastPath = configDir.resolve("chapter_folders.snbt");
        if (!Files.exists(lastPath)) return;
        try {
            String raw = Files.readString(lastPath, StandardCharsets.UTF_8);
            // Wrap in a compound so TagParser can handle it
            CompoundTag root = TagParser.parseTag("{data:" + raw + "}");
            ListTag list = root.getList("data", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag ft = list.getCompound(i);
                String id = ft.getString("id");
                String label = ft.getString("label");
                List<String> cats = new ArrayList<>();
                if (ft.contains("categories")) {
                    ListTag cl = ft.getList("categories", Tag.TAG_STRING);
                    for (int j = 0; j < cl.size(); j++) cats.add(cl.getString(j).toUpperCase());
                }
                if (!id.isBlank()) folders.add(new ChapterFolder(id, label, cats));
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load chapter_folders.snbt: " + e.getMessage());
        }
    }

    public static void save() {
        if (lastPath == null) return;
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < folders.size(); i++) {
                ChapterFolder f = folders.get(i);
                if (i > 0) sb.append(",");
                sb.append("{id:\"").append(f.id()).append("\",label:\"").append(f.label()).append("\",categories:[");
                for (int j = 0; j < f.categories().size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append("\"").append(f.categories().get(j)).append("\"");
                }
                sb.append("]}");
            }
            sb.append("]");
            Files.createDirectories(lastPath.getParent());
            Files.writeString(lastPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to save chapter_folders.snbt: " + e.getMessage());
        }
    }

    // ── Mutation (called from editor) ─────────────────────────────────────────

    public static void addFolder(String id, String label) {
        if (folders.stream().noneMatch(f -> f.id().equals(id)))
            folders.add(new ChapterFolder(id, label, new ArrayList<>()));
    }

    public static void removeFolder(String id) {
        folders.removeIf(f -> f.id().equals(id));
    }

    public static void renameFolder(String id, String newLabel) {
        for (int i = 0; i < folders.size(); i++) {
            ChapterFolder f = folders.get(i);
            if (f.id().equals(id)) {
                folders.set(i, new ChapterFolder(id, newLabel, f.categories()));
                return;
            }
        }
    }

    public static void addCategoryToFolder(String folderId, String category) {
        for (int i = 0; i < folders.size(); i++) {
            ChapterFolder f = folders.get(i);
            if (f.id().equals(folderId)) {
                List<String> cats = new ArrayList<>(f.categories());
                String upper = category.toUpperCase();
                if (!cats.contains(upper)) cats.add(upper);
                folders.set(i, new ChapterFolder(f.id(), f.label(), cats));
                return;
            }
        }
    }

    public static void removeCategoryFromFolder(String folderId, String category) {
        for (int i = 0; i < folders.size(); i++) {
            ChapterFolder f = folders.get(i);
            if (f.id().equals(folderId)) {
                List<String> cats = new ArrayList<>(f.categories());
                cats.remove(category.toUpperCase());
                folders.set(i, new ChapterFolder(f.id(), f.label(), cats));
                return;
            }
        }
    }
}
