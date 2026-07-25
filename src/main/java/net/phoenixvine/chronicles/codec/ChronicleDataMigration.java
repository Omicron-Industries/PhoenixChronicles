package net.phoenixvine.chronicles.codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.phoenixvine.chronicles.registry.QuestLangRegistry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ChronicleDataMigration {

    private static final String MARKER = ".chapter_migration_done";

    private ChronicleDataMigration() {}

    public static void migrate(Path configDir) {
        Path marker = configDir.resolve(MARKER);
        if (Files.exists(marker)) return;

        migrateQuestFiles(configDir);
        migrateFlatFile(configDir, "categories.json", "chapters.json");
        migrateFlatFile(configDir, "categories.txt", "chapters.txt");
        migrateFlatFile(configDir, "category_flags.snbt", "chapter_flags.snbt");
        migrateFlatFile(configDir, "category_prereq_defaults.snbt", "chapter_prereq_defaults.snbt");
        migrateLangKeys(configDir);

        try {
            Files.createDirectories(configDir);
            Files.writeString(marker,
                    "This file marks that the one-time category->chapter data migration has run.\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to write migration marker: " + e.getMessage());
        }
    }

    private static void migrateFlatFile(Path configDir, String oldName, String newName) {
        try {
            Path oldFile = configDir.resolve(oldName);
            Path newFile = configDir.resolve(newName);
            if (Files.exists(oldFile) && !Files.exists(newFile)) {
                Files.copy(oldFile, newFile, StandardCopyOption.COPY_ATTRIBUTES);
                System.out.println("[Phoenix Chronicles] Migrated " + oldName + " -> " + newName);
            }
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to migrate " + oldName + ": " + e.getMessage());
        }
    }

    private static void migrateQuestFiles(Path configDir) {
        Path questsDir = configDir.resolve("quests");
        if (!Files.exists(questsDir)) return;
        int migrated = 0;
        try (Stream<Path> walk = Files.walk(questsDir)) {
            List<Path> snbtFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".snbt"))
                    .toList();
            for (Path p : snbtFiles) {
                try {
                    String raw = Files.readString(p, StandardCharsets.UTF_8);
                    CompoundTag tag = TagParser.parseTag(raw);
                    if (tag.contains("category") && !tag.contains("chapter")) {
                        String chapter = tag.getString("category");
                        tag.remove("category");
                        tag.putString("chapter", chapter);
                        Files.writeString(p, tag.toString(), StandardCharsets.UTF_8);
                        migrated++;
                    }
                } catch (Exception e) {
                    System.err.println(
                            "[Phoenix Chronicles] Failed to migrate quest file '" + p.getFileName() + "': " +
                                    e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to walk quests directory for migration: " +
                    e.getMessage());
        }
        if (migrated > 0) {
            System.out.println(
                    "[Phoenix Chronicles] Migrated " + migrated + " quest file(s) from 'category' to 'chapter'.");
        }
    }

    private static void migrateLangKeys(Path configDir) {
        try {
            Path file = QuestLangRegistry.langDir(configDir).resolve("en_us.json");
            if (!Files.exists(file)) return;

            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = gson.fromJson(raw, JsonObject.class);
            if (root == null) return;

            boolean changed = false;
            String prefix = "phoenix_chronicles.category.";
            for (String key : new ArrayList<>(root.keySet())) {
                if (!key.startsWith(prefix)) continue;
                String newKey = "phoenix_chronicles.chapter." + key.substring(prefix.length());
                if (!root.has(newKey)) {
                    root.add(newKey, root.get(key));
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(file, gson.toJson(root), StandardCharsets.UTF_8);
                System.out.println("[Phoenix Chronicles] Migrated legacy 'category' lang keys to 'chapter'.");
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to migrate lang keys: " + e.getMessage());
        }
    }
}
