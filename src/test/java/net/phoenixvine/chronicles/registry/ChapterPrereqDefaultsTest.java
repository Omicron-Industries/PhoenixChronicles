package net.phoenixvine.chronicles.registry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChapterPrereqDefaultsTest {

    @AfterEach
    void clear() {
        ChapterPrereqDefaults.clear();
    }

    @Test
    void missingFileLeavesRegistryEmpty(@TempDir Path dir) {
        ChapterPrereqDefaults.load(dir);
        assertNull(ChapterPrereqDefaults.getRequireAll("ANYTHING"));
        assertNull(ChapterPrereqDefaults.getOptionalMinCount("ANYTHING"));
    }

    @Test
    void loadsRequireAllAndOptionalMinCountKeyedByUppercaseChapter(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("chapter_prereq_defaults.snbt"), """
                {
                  groundwork: {
                    require_all: 0b,
                    optional_min_count: 2
                  },
                  finale: {
                    require_all: 1b
                  }
                }
                """, StandardCharsets.UTF_8);

        ChapterPrereqDefaults.load(dir);

        assertEquals(Boolean.FALSE, ChapterPrereqDefaults.getRequireAll("groundwork"));
        assertEquals(Boolean.FALSE, ChapterPrereqDefaults.getRequireAll("GROUNDWORK"));
        assertEquals(2, ChapterPrereqDefaults.getOptionalMinCount("Groundwork"));
        assertEquals(Boolean.TRUE, ChapterPrereqDefaults.getRequireAll("finale"));
        assertNull(ChapterPrereqDefaults.getOptionalMinCount("finale"));
    }

    @Test
    void malformedFileIsSwallowedAndLeavesRegistryEmpty(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("chapter_prereq_defaults.snbt"), "{ not valid snbt !!", StandardCharsets.UTF_8);

        ChapterPrereqDefaults.load(dir);

        assertNull(ChapterPrereqDefaults.getRequireAll("GROUNDWORK"));
    }

    @Test
    void reloadReplacesPreviouslyLoadedEntries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("chapter_prereq_defaults.snbt");
        Files.writeString(file, "{ a: { require_all: 0b } }", StandardCharsets.UTF_8);
        ChapterPrereqDefaults.load(dir);
        assertEquals(Boolean.FALSE, ChapterPrereqDefaults.getRequireAll("a"));

        Files.writeString(file, "{ b: { require_all: 1b } }", StandardCharsets.UTF_8);
        ChapterPrereqDefaults.load(dir);

        assertNull(ChapterPrereqDefaults.getRequireAll("a"));
        assertEquals(Boolean.TRUE, ChapterPrereqDefaults.getRequireAll("b"));
    }

    @Test
    void chapterLookupIsNullSafe() {
        assertNull(ChapterPrereqDefaults.getRequireAll(null));
        assertNull(ChapterPrereqDefaults.getOptionalMinCount(null));
    }
}
