package net.phoenixvine.chronicles.codec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChronicleDataMigrationTest {

    @Test
    void migratesLegacyFlatFilesWhenNewNameIsAbsent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("categories.json"), "{}", StandardCharsets.UTF_8);

        ChronicleDataMigration.migrate(dir);

        assertTrue(Files.exists(dir.resolve("chapters.json")));
        assertTrue(Files.exists(dir.resolve("categories.json")), "original file should be left in place, not moved");
    }

    @Test
    void doesNotOverwriteAnExistingNewNameFile(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("categories.json"), "{\"old\":true}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("chapters.json"), "{\"new\":true}", StandardCharsets.UTF_8);

        ChronicleDataMigration.migrate(dir);

        assertEquals("{\"new\":true}", Files.readString(dir.resolve("chapters.json"), StandardCharsets.UTF_8));
    }

    @Test
    void rewritesCategoryKeyToChapterKeyInsideQuestSnbtFiles(@TempDir Path dir) throws IOException {
        Path questsDir = dir.resolve("quests");
        Files.createDirectories(questsDir);
        Files.writeString(questsDir.resolve("q1.snbt"), "{category:\"groundwork\",id:\"q1\"}",
                StandardCharsets.UTF_8);

        ChronicleDataMigration.migrate(dir);

        String rewritten = Files.readString(questsDir.resolve("q1.snbt"), StandardCharsets.UTF_8);
        assertTrue(rewritten.contains("chapter:\"groundwork\""));
        assertFalse(rewritten.contains("category:"));
    }

    @Test
    void leavesQuestFileAloneIfChapterKeyAlreadyPresent(@TempDir Path dir) throws IOException {
        Path questsDir = dir.resolve("quests");
        Files.createDirectories(questsDir);
        String original = "{category:\"old\",chapter:\"already_migrated\",id:\"q1\"}";
        Files.writeString(questsDir.resolve("q1.snbt"), original, StandardCharsets.UTF_8);

        ChronicleDataMigration.migrate(dir);

        assertEquals(original, Files.readString(questsDir.resolve("q1.snbt"), StandardCharsets.UTF_8));
    }

    @Test
    void writesMarkerFileAfterMigrating(@TempDir Path dir) {
        ChronicleDataMigration.migrate(dir);
        assertTrue(Files.exists(dir.resolve(".chapter_migration_done")));
    }

    @Test
    void secondMigrateCallIsANoOpOnceMarkerExists(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("categories.json"), "{}", StandardCharsets.UTF_8);
        ChronicleDataMigration.migrate(dir);
        Files.delete(dir.resolve("chapters.json"));

        ChronicleDataMigration.migrate(dir);

        assertFalse(Files.exists(dir.resolve("chapters.json")));
    }

    @Test
    void migratesLegacyCategoryLangKeysToChapterKeysWithoutClobberingExisting(@TempDir Path dir) throws IOException {
        Path langDir = dir.resolve("assets").resolve("phoenix_chronicles").resolve("lang");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("en_us.json"), """
                {
                  "phoenix_chronicles.category.groundwork.name": "Groundwork",
                  "phoenix_chronicles.chapter.finale.name": "Finale (already migrated)"
                }
                """, StandardCharsets.UTF_8);

        ChronicleDataMigration.migrate(dir);

        String result = Files.readString(langDir.resolve("en_us.json"), StandardCharsets.UTF_8);
        assertTrue(result.contains("\"phoenix_chronicles.chapter.groundwork.name\": \"Groundwork\""));
        assertTrue(result.contains("phoenix_chronicles.chapter.finale.name"));
    }
}
