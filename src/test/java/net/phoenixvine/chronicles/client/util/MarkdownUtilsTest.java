package net.phoenixvine.chronicles.client.util;

import net.phoenixvine.chronicles.model.FullQuestData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownUtilsTest {

    private static Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void firstH1LineBecomesTitleAndIsExcludedFromDescription(@TempDir Path dir) throws IOException {
        Path file = write(dir, "q.md", "# My Quest Title\nBody text here.");

        FullQuestData data = MarkdownUtils.loadMarkdownContent(file);

        assertEquals("My Quest Title", data.title().getString());
        assertEquals("Body text here.", data.description().getString());
    }

    @Test
    void secondHeadingLineIsNotTreatedAsANewTitle(@TempDir Path dir) throws IOException {
        Path file = write(dir, "q.md", "# First Title\n# Second Looks Like A Title Too\nBody.");

        FullQuestData data = MarkdownUtils.loadMarkdownContent(file);

        assertEquals("First Title", data.title().getString());
    }

    @Test
    void blankLinesInsertParagraphBreaksInDescription(@TempDir Path dir) throws IOException {
        Path file = write(dir, "q.md", "# T\nFirst paragraph.\n\nSecond paragraph.");

        FullQuestData data = MarkdownUtils.loadMarkdownContent(file);

        assertEquals("First paragraph.\n\nSecond paragraph.", data.description().getString());
    }

    @Test
    void consecutiveNonBlankLinesJoinWithASpace(@TempDir Path dir) throws IOException {
        Path file = write(dir, "q.md", "# T\nLine one\nLine two");

        FullQuestData data = MarkdownUtils.loadMarkdownContent(file);

        assertEquals("Line one Line two", data.description().getString());
    }

    @Test
    void ruleLineIsKeptVerbatimAndForcesAParagraphBreakAfterIt(@TempDir Path dir) throws IOException {
        Path file = write(dir, "q.md", "# T\nBefore.\n---\nAfter.");

        FullQuestData data = MarkdownUtils.loadMarkdownContent(file);

        assertTrue(data.description().getString().contains("---"));
        assertEquals("Before.\n\n---\n\nAfter.", data.description().getString());
    }

    @Test
    void missingFileReturnsEmptyTitleAndDescriptionRatherThanThrowing() {
        Path missing = Path.of("build", "definitely-does-not-exist-" + System.nanoTime() + ".md");

        FullQuestData data = MarkdownUtils.loadMarkdownContent(missing);

        assertEquals("", data.title().getString());
        assertEquals("", data.description().getString());
        assertTrue(data.tasks().isEmpty());
    }

    @Test
    void fileWithNoH1LineHasEmptyTitleButKeepsBody(@TempDir Path dir) throws IOException {
        Path file = write(dir, "q.md", "Just a paragraph, no heading.");

        FullQuestData data = MarkdownUtils.loadMarkdownContent(file);

        assertEquals("", data.title().getString());
        assertEquals("Just a paragraph, no heading.", data.description().getString());
    }
}
