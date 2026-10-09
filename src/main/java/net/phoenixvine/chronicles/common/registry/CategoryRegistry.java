package net.phoenixvine.chronicles.common.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.phoenixvine.chronicles.common.codec.CategoryLoader;
import net.phoenixvine.chronicles.common.codec.QuestFileWatcher;
import net.phoenixvine.chronicles.common.model.CategoryDefinition;

import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CategoryRegistry {

    private static final Map<String, CategoryDefinition> CATEGORIES = new LinkedHashMap<>();
    private static final Set<String> collapsed = new HashSet<>();
    private static final List<String> categoryOrder = new ArrayList<>();
    private static final List<String> standaloneOrder = new ArrayList<>();
    private static final List<String> topLevelOrder = new ArrayList<>();
    private static final String CATEGORY_TOKEN_PREFIX = "C:";
    private static final String CHAPTER_TOKEN_PREFIX = "S:";

    private static Path categoriesFolder = null;
    private static Path uiStatePath = null;

    private CategoryRegistry() {}

    public static synchronized void register(CategoryDefinition category) {
        if (category != null) CATEGORIES.put(category.id(), category);
    }

    @Nullable
    public static synchronized CategoryDefinition get(String id) {
        return CATEGORIES.get(id);
    }

    public static synchronized void clear() {
        CATEGORIES.clear();
    }

    public static synchronized List<CategoryDefinition> getCategories() {
        List<CategoryDefinition> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : categoryOrder) {
            CategoryDefinition c = CATEGORIES.get(id);
            if (c != null && seen.add(id)) ordered.add(c);
        }
        for (CategoryDefinition c : CATEGORIES.values()) {
            if (seen.add(c.id())) ordered.add(c);
        }
        return Collections.unmodifiableList(ordered);
    }

    public static synchronized boolean isCollapsed(String categoryId) {
        return collapsed.contains(categoryId);
    }

    public static synchronized void toggleCollapsed(String categoryId) {
        if (!collapsed.remove(categoryId)) collapsed.add(categoryId);
    }

    @Nullable
    public static synchronized CategoryDefinition categoryFor(String chapter) {
        String upper = chapter.toUpperCase();
        for (CategoryDefinition c : CATEGORIES.values())
            if (c.chapters().contains(upper)) return c;
        return null;
    }

    public static synchronized void load(Path configDir) {
        categoriesFolder = configDir.resolve("categories");
        uiStatePath = configDir.resolve("category_ui_state.snbt");
        CategoryLoader.reloadAllCategoriesFromDisk(categoriesFolder);
        loadUiState();
    }

    public static synchronized void save() {
        saveUiState();
    }

    public static synchronized List<String> getStandaloneOrder() {
        return Collections.unmodifiableList(standaloneOrder);
    }

    public static String categoryToken(String categoryId) {
        return CATEGORY_TOKEN_PREFIX + categoryId;
    }

    public static String chapterToken(String chapter) {
        return CHAPTER_TOKEN_PREFIX + chapter.toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean isCategoryToken(String token) {
        return token.startsWith(CATEGORY_TOKEN_PREFIX);
    }

    public static String tokenId(String token) {
        return token.substring(CATEGORY_TOKEN_PREFIX.length());
    }

    public static synchronized List<String> resolveTopLevel(List<String> standaloneChapters) {
        Set<String> valid = new HashSet<>();
        List<String> categoryTokens = new ArrayList<>();
        for (CategoryDefinition c : getCategories()) {
            String token = categoryToken(c.id());
            valid.add(token);
            categoryTokens.add(token);
        }
        Set<String> standaloneUpper = new HashSet<>();
        for (String c : standaloneChapters) standaloneUpper.add(c.toUpperCase(java.util.Locale.ROOT));
        List<String> standaloneTokens = new ArrayList<>();
        for (String c : standaloneOrder) if (standaloneUpper.contains(c)) standaloneTokens.add(chapterToken(c));
        for (String c : standaloneUpper) if (!standaloneOrder.contains(c)) standaloneTokens.add(chapterToken(c));
        valid.addAll(standaloneTokens);

        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String token : topLevelOrder) if (valid.contains(token) && seen.add(token)) result.add(token);
        for (String token : categoryTokens) if (seen.add(token)) result.add(token);
        for (String token : standaloneTokens) if (seen.add(token)) result.add(token);
        return result;
    }

    public static synchronized void moveTopLevel(String token, @Nullable String beforeToken,
                                                 List<String> standaloneChapters) {
        if (token.equals(beforeToken)) return;
        List<String> order = resolveTopLevel(standaloneChapters);
        order.remove(token);
        int idx = beforeToken == null ? order.size() : order.indexOf(beforeToken);
        if (idx < 0) idx = order.size();
        order.add(idx, token);
        storeTopLevel(order);
    }

    public static synchronized void ensureLeadingStandalone(List<String> orderedChapterIds) {
        int insertAt = 0;
        boolean changed = false;
        for (String id : orderedChapterIds) {
            String token = chapterToken(id);
            if (topLevelOrder.contains(token)) continue;
            topLevelOrder.add(insertAt++, token);
            changed = true;
        }
        if (changed) saveUiState();
    }

    private static void storeTopLevel(List<String> order) {
        for (String previous : topLevelOrder) if (!order.contains(previous)) order.add(previous);
        topLevelOrder.clear();
        topLevelOrder.addAll(order);
        categoryOrder.clear();
        standaloneOrder.clear();
        for (String token : order) {
            if (isCategoryToken(token)) categoryOrder.add(tokenId(token));
            else standaloneOrder.add(tokenId(token));
        }
        saveUiState();
    }

    public static synchronized void reorderCategoryChapter(String categoryId, String chapId, String targetId) {
        CategoryDefinition cat = CATEGORIES.get(categoryId);
        if (cat == null) return;

        List<String> chapters = new ArrayList<>(cat.chapters());
        String upperChapId = chapId.toUpperCase();
        chapters.remove(upperChapId);

        int idx;
        if (targetId == null) {
            idx = chapters.size();
        } else {
            String upperTargetId = targetId.toUpperCase();
            int targetIdx = chapters.indexOf(upperTargetId);
            if (targetIdx >= 0) {
                idx = targetIdx;
            } else {
                idx = chapters.size();
            }
        }

        chapters.add(Math.max(0, Math.min(chapters.size(), idx)), upperChapId);

        CategoryDefinition updated = new CategoryDefinition(cat.id(), cat.displayName(), chapters, cat.color(),
                cat.icon(), cat.nameColor());
        CATEGORIES.put(categoryId, updated);
        writeCategory(updated);
    }

    public static synchronized void addCategory(String id, String label) {
        if (CATEGORIES.containsKey(id)) return;
        CategoryDefinition category = new CategoryDefinition(id, label, new ArrayList<>());
        CATEGORIES.put(id, category);
        if (!categoryOrder.contains(id)) categoryOrder.add(id);
        writeCategory(category);
    }

    public static synchronized void removeCategory(String id) {
        CATEGORIES.remove(id);
        categoryOrder.remove(id);
        collapsed.remove(id);
        if (categoriesFolder != null) {
            QuestFileWatcher.suppressNextReload();
            CategoryLoader.deleteCategoryFile(categoriesFolder, id);
        }
    }

    public static synchronized void renameCategory(String id, String newLabel) {
        CategoryDefinition c = CATEGORIES.get(id);
        if (c == null) return;
        CategoryDefinition updated = new CategoryDefinition(id, newLabel, c.chapters(), c.color(), c.icon(),
                c.nameColor());
        CATEGORIES.put(id, updated);
        writeCategory(updated);
    }

    public static synchronized void updateTheme(String id, int color, String icon, int nameColor) {
        CategoryDefinition c = CATEGORIES.get(id);
        if (c == null) return;
        CategoryDefinition updated = c.withTheme(color, icon, nameColor);
        CATEGORIES.put(id, updated);
        writeCategory(updated);
    }

    public static synchronized void addChapterToCategory(String categoryId, String chapter) {
        CategoryDefinition c = CATEGORIES.get(categoryId);
        if (c == null) return;
        List<String> chaps = new ArrayList<>(c.chapters());
        String upper = chapter.toUpperCase();
        if (!chaps.contains(upper)) chaps.add(upper);
        CategoryDefinition updated = new CategoryDefinition(c.id(), c.displayName(), chaps, c.color(), c.icon(),
                c.nameColor());
        CATEGORIES.put(categoryId, updated);
        writeCategory(updated);
    }

    public static synchronized void removeChapterFromCategory(String categoryId, String chapter) {
        CategoryDefinition c = CATEGORIES.get(categoryId);
        if (c == null) return;
        List<String> chaps = new ArrayList<>(c.chapters());
        chaps.remove(chapter.toUpperCase());
        CategoryDefinition updated = new CategoryDefinition(c.id(), c.displayName(), chaps, c.color(), c.icon(),
                c.nameColor());
        CATEGORIES.put(categoryId, updated);
        writeCategory(updated);
    }

    private static void writeCategory(CategoryDefinition category) {
        if (categoriesFolder == null) return;
        QuestFileWatcher.suppressNextReload();
        CategoryLoader.writeCategoryFile(categoriesFolder, category);
    }

    private static void loadUiState() {
        collapsed.clear();
        categoryOrder.clear();
        standaloneOrder.clear();
        topLevelOrder.clear();
        if (uiStatePath == null || !Files.exists(uiStatePath)) return;
        try {
            String raw = Files.readString(uiStatePath, StandardCharsets.UTF_8);
            CompoundTag root = TagParser.parseTag(raw);
            if (root.contains("collapsed")) {
                ListTag l = root.getList("collapsed", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) collapsed.add(l.getString(i));
            }
            if (root.contains("categoryOrder")) {
                ListTag l = root.getList("categoryOrder", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) categoryOrder.add(l.getString(i));
            }
            if (root.contains("standaloneOrder")) {
                ListTag l = root.getList("standaloneOrder", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) standaloneOrder.add(l.getString(i).toUpperCase());
            }
            if (root.contains("topLevelOrder")) {
                ListTag l = root.getList("topLevelOrder", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) topLevelOrder.add(l.getString(i));
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load category_ui_state.snbt: " + e.getMessage());
        }
    }

    private static void saveUiState() {
        if (uiStatePath == null) return;
        try {

            QuestFileWatcher.suppressNextReload();
            CompoundTag root = new CompoundTag();
            ListTag collapsedList = new ListTag();
            for (String id : collapsed) collapsedList.add(StringTag.valueOf(id));
            root.put("collapsed", collapsedList);
            ListTag orderList = new ListTag();
            for (String id : categoryOrder) orderList.add(StringTag.valueOf(id));
            root.put("categoryOrder", orderList);
            ListTag standaloneList = new ListTag();
            for (String chap : standaloneOrder) standaloneList.add(StringTag.valueOf(chap));
            root.put("standaloneOrder", standaloneList);
            ListTag topLevelList = new ListTag();
            for (String token : topLevelOrder) topLevelList.add(StringTag.valueOf(token));
            root.put("topLevelOrder", topLevelList);
            Files.createDirectories(uiStatePath.getParent());
            Files.writeString(uiStatePath, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to save category_ui_state.snbt: " + e.getMessage());
        }
    }
}
