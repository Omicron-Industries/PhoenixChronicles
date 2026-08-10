package net.phoenixvine.chronicles.codec;

import net.phoenixvine.chronicles.model.CategoryDefinition;
import net.phoenixvine.chronicles.registry.CategoryRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CategoryLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryLoader.class);

    public static void reloadAllCategoriesFromDisk(Path categoriesFolder) {
        CategoryRegistry.clear();

        if (!Files.exists(categoriesFolder)) {
            LOGGER.info("[Chronicles] No categories folder found at {}", categoriesFolder);
            return;
        }

        try (Stream<Path> walk = Files.walk(categoriesFolder)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml"))
                    .sorted()
                    .forEach(CategoryLoader::loadCategoryFile);
        } catch (IOException e) {
            LOGGER.error("[Chronicles] Failed to walk categories directory", e);
        }
    }

    private static void loadCategoryFile(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            CategoryDefinition category = parseCategory(lines, file.getFileName().toString());
            if (category != null) {
                CategoryRegistry.register(category);
                LOGGER.info("[Chronicles] Loaded category: {}", category.id());
            }
        } catch (IOException e) {
            LOGGER.error("[Chronicles] Failed to read category file: {}", file.getFileName(), e);
        }
    }

    private static CategoryDefinition parseCategory(List<String> lines, String fileName) {
        String id = null;
        String displayName = null;
        List<String> chapters = new ArrayList<>();
        int color = 0;
        int nameColor = 0;
        String icon = "";
        boolean inChapters = false;

        for (String rawLine : lines) {
            int indent = leadingSpaces(rawLine);
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (indent == 0) {
                inChapters = false;
                if (line.equals("chapters:")) {
                    inChapters = true;
                    continue;
                }
                String[] kv = splitKV(line);
                if (kv == null) continue;
                switch (kv[0]) {
                    case "id" -> id = kv[1];
                    case "display_name" -> displayName = unquote(kv[1]);
                    case "color" -> color = parseColor(kv[1]);
                    case "name_color" -> nameColor = parseColor(kv[1]);
                    case "icon" -> icon = unquote(kv[1]);
                }
                continue;
            }

            if (inChapters && indent == 2 && line.startsWith("- ")) {
                String chap = line.substring(2).trim().toUpperCase();
                if (!chap.isEmpty()) chapters.add(chap);
            }
        }

        if (id == null) {
            id = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        }
        id = id.toLowerCase();
        if (displayName == null) displayName = id;

        return new CategoryDefinition(id, displayName, chapters, color, icon, nameColor);
    }

    private static int parseColor(String hex) {
        try {
            String clean = hex.trim();
            if (clean.startsWith("#")) clean = clean.substring(1);
            return clean.isEmpty() ? 0 : (int) (Long.parseLong(clean, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void writeCategoryFile(Path categoriesFolder, CategoryDefinition category) {
        try {
            Files.createDirectories(categoriesFolder);
            StringBuilder sb = new StringBuilder();
            sb.append("id: ").append(category.id()).append('\n');
            sb.append("display_name: \"").append(category.displayName()).append("\"\n");
            if (category.color() != 0)
                sb.append("color: ").append(String.format("%06X", category.color())).append('\n');
            if (category.nameColor() != 0)
                sb.append("name_color: ").append(String.format("%06X", category.nameColor())).append('\n');
            if (category.icon() != null && !category.icon().isEmpty())
                sb.append("icon: \"").append(category.icon()).append("\"\n");
            sb.append("chapters:\n");
            for (String chap : category.chapters()) sb.append("  - ").append(chap).append('\n');
            Files.writeString(fileFor(categoriesFolder, category.id()), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[Chronicles] Failed to write category file for '{}'", category.id(), e);
        }
    }

    public static void deleteCategoryFile(Path categoriesFolder, String categoryId) {
        try {
            Files.deleteIfExists(fileFor(categoriesFolder, categoryId));
        } catch (IOException e) {
            LOGGER.error("[Chronicles] Failed to delete category file for '{}'", categoryId, e);
        }
    }

    private static Path fileFor(Path categoriesFolder, String categoryId) {
        return categoriesFolder.resolve(categoryId + ".yml");
    }

    private static String[] splitKV(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) return null;
        String key = line.substring(0, colon).trim().toLowerCase();
        String val = line.substring(colon + 1).trim();
        if (key.isEmpty()) return null;
        return new String[] { key, val };
    }

    private static String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else break;
        }
        return count;
    }
}
