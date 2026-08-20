package net.phoenixvine.chronicles.client.rich;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChronicleMarkdownParserTest {

    private static String text(RichSpan span) {
        if (span instanceof RichSpan.Text t) return t.text();
        if (span instanceof RichSpan.Link l) return l.label();
        if (span instanceof RichSpan.Tip t) return t.label();
        return "";
    }

    private static String plain(List<RichSpan> spans) {
        StringBuilder sb = new StringBuilder();
        for (RichSpan s : spans) sb.append(text(s));
        return sb.toString();
    }

    @Test
    void nullOrBlankInputProducesNoBlocks() {
        assertTrue(ChronicleMarkdownParser.parse(null).isEmpty());
        assertTrue(ChronicleMarkdownParser.parse("   \n  \n").isEmpty());
    }

    @Test
    void headingLevelsMatchHashCount() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("# One\n## Two\n### Three");

        assertEquals(3, blocks.size());
        RichBlock.Heading h1 = assertInstanceOf(RichBlock.Heading.class, blocks.get(0));
        RichBlock.Heading h2 = assertInstanceOf(RichBlock.Heading.class, blocks.get(1));
        RichBlock.Heading h3 = assertInstanceOf(RichBlock.Heading.class, blocks.get(2));
        assertEquals(1, h1.level());
        assertEquals(2, h2.level());
        assertEquals(3, h3.level());
        assertEquals("One", plain(h1.spans()));
    }

    @Test
    void plainParagraphBecomesOneParagraphBlock() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("Just a sentence.");

        assertEquals(1, blocks.size());
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        assertEquals("Just a sentence.", plain(p.spans()));
    }

    @Test
    void consecutiveNonBlankLinesJoinIntoOneParagraph() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("Line one\nLine two");

        assertEquals(1, blocks.size());
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        assertEquals("Line one Line two", plain(p.spans()));
    }

    @Test
    void multipleBlankLinesCollapseToOneBlankBlockAndLeadingBlanksAreDropped() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("\n\nFirst\n\n\nSecond");

        assertEquals(3, blocks.size());
        assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        assertInstanceOf(RichBlock.Blank.class, blocks.get(1));
        assertInstanceOf(RichBlock.Paragraph.class, blocks.get(2));
    }

    @Test
    void unorderedListItemGetsBulletMarkerAndNestIndent() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("- top\n  - nested");

        RichBlock.ListItem top = assertInstanceOf(RichBlock.ListItem.class, blocks.get(0));
        RichBlock.ListItem nested = assertInstanceOf(RichBlock.ListItem.class, blocks.get(1));
        assertEquals("•", top.marker());
        assertEquals(10, top.indent());
        assertEquals(20, nested.indent());
    }

    @Test
    void checkboxSyntaxProducesChecklistBlockWithCorrectCheckedState() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("- [ ] todo\n- [x] done\n- [X] alsoDone");

        RichBlock.Checklist unchecked = assertInstanceOf(RichBlock.Checklist.class, blocks.get(0));
        RichBlock.Checklist checked = assertInstanceOf(RichBlock.Checklist.class, blocks.get(1));
        RichBlock.Checklist checkedUpper = assertInstanceOf(RichBlock.Checklist.class, blocks.get(2));
        assertFalse(unchecked.checkedDefault());
        assertTrue(checked.checkedDefault());
        assertTrue(checkedUpper.checkedDefault());
    }

    @Test
    void orderedListPreservesNumberAsMarker() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("1. first\n2. second");

        RichBlock.ListItem first = assertInstanceOf(RichBlock.ListItem.class, blocks.get(0));
        RichBlock.ListItem second = assertInstanceOf(RichBlock.ListItem.class, blocks.get(1));
        assertEquals("1.", first.marker());
        assertEquals("2.", second.marker());
    }

    @Test
    void ruleLineBecomesRuleBlock() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("---");
        assertInstanceOf(RichBlock.Rule.class, blocks.get(0));
    }

    @Test
    void fencedCodeBlockCapturesLanguageAndVerbatimBody() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("```java\nint x = 1;\nint y = 2;\n```");

        RichBlock.CodeBlock cb = assertInstanceOf(RichBlock.CodeBlock.class, blocks.get(0));
        assertEquals("java", cb.lang());
        assertEquals("int x = 1;\nint y = 2;", cb.code());
        assertTrue(cb.spans().isEmpty());
    }

    @Test
    void unterminatedFenceStillCapturesToEndOfInput() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("```\nfoo");
        RichBlock.CodeBlock cb = assertInstanceOf(RichBlock.CodeBlock.class, blocks.get(0));
        assertEquals("foo", cb.code());
    }

    @Test
    void blockquoteJoinsConsecutiveQuoteLinesWithASpace() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("> line one\n> line two");
        RichBlock.Quote q = assertInstanceOf(RichBlock.Quote.class, blocks.get(0));
        assertEquals("line one line two", plain(q.spans()));
    }

    @Test
    void pipeTableParsesHeaderAndRows() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("""
                | A | B |
                | - | - |
                | 1 | 2 |
                | 3 | 4 |
                """);

        RichBlock.Table t = assertInstanceOf(RichBlock.Table.class, blocks.get(0));
        assertEquals(2, t.header().size());
        assertEquals("A", plain(t.header().get(0)));
        assertEquals("B", plain(t.header().get(1)));
        assertEquals(2, t.rows().size());
        assertEquals("1", plain(t.rows().get(0).get(0)));
        assertEquals("4", plain(t.rows().get(1).get(1)));
    }

    @Test
    void calloutContainerCapturesTypeTitleAndNestedBlocks() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse(":::warning Careful\nInner text\n:::");

        RichBlock.Callout c = assertInstanceOf(RichBlock.Callout.class, blocks.get(0));
        assertEquals("warning", c.type());
        assertEquals("Careful", c.title());
        assertEquals(1, c.children().size());
        RichBlock.Paragraph inner = assertInstanceOf(RichBlock.Paragraph.class, c.children().get(0));
        assertEquals("Inner text", plain(inner.spans()));
    }

    @Test
    void spoilerContainerBecomesDetailsBlockWithFallbackTitle() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse(":::spoiler\nhidden\n:::");
        RichBlock.Details d = assertInstanceOf(RichBlock.Details.class, blocks.get(0));
        assertEquals("Details", d.title());
    }

    @Test
    void nestedContainersOfSameSyntaxBalanceCorrectly() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse(":::note Outer\n:::tip Inner\nnested\n:::\n:::");

        RichBlock.Callout outer = assertInstanceOf(RichBlock.Callout.class, blocks.get(0));
        assertEquals("note", outer.type());
        assertEquals(1, outer.children().size());
        RichBlock.Callout inner = assertInstanceOf(RichBlock.Callout.class, outer.children().get(0));
        assertEquals("tip", inner.type());
    }

    @Test
    void footnoteDefinitionIsStrippedFromBodyAndResolvedAtReference() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("See note[^1].\n\n[^1]: The detail.");

        assertEquals(2, blocks.size());
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.Tip tip = assertInstanceOf(RichSpan.Tip.class, p.spans().get(1));
        assertEquals("[1]", tip.label());
        assertEquals("The detail.", tip.tooltip());
    }

    @Test
    void unresolvedFootnoteReferenceFallsBackToLiteralText() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("Dangling[^missing].");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        assertEquals("Dangling[^missing].", plain(p.spans()));
    }

    @Test
    void boldToggleWrapsOnlyTheMarkedRun() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("plain **bold** plain");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));

        assertEquals(3, p.spans().size());
        RichSpan.Text before = assertInstanceOf(RichSpan.Text.class, p.spans().get(0));
        RichSpan.Text bold = assertInstanceOf(RichSpan.Text.class, p.spans().get(1));
        RichSpan.Text after = assertInstanceOf(RichSpan.Text.class, p.spans().get(2));
        assertFalse(before.style().isBold());
        assertTrue(bold.style().isBold());
        assertEquals("bold", bold.text());
        assertFalse(after.style().isBold());
    }

    @Test
    void italicToggleWrapsOnlyTheMarkedRun() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("*italic*");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.Text t = assertInstanceOf(RichSpan.Text.class, p.spans().get(0));
        assertTrue(t.style().isItalic());
    }

    @Test
    void strikethroughTildeTogglePersistsAcrossFlushes() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("~~gone~~ still");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.Text struck = assertInstanceOf(RichSpan.Text.class, p.spans().get(0));
        RichSpan.Text rest = assertInstanceOf(RichSpan.Text.class, p.spans().get(1));
        assertTrue(struck.style().isStrikethrough());
        assertFalse(rest.style().isStrikethrough());
    }

    @Test
    void highlightTokenSetsNonZeroBackgroundOnlyInsideTheRun() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("==marked== plain");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.Text marked = assertInstanceOf(RichSpan.Text.class, p.spans().get(0));
        RichSpan.Text rest = assertInstanceOf(RichSpan.Text.class, p.spans().get(1));
        assertTrue(marked.background() != 0);
        assertEquals(0, rest.background());
    }

    @Test
    void hexColorTokenAppliesUntilReset() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("{#FF0000}red{reset}normal");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.Text red = assertInstanceOf(RichSpan.Text.class, p.spans().get(0));
        RichSpan.Text normal = assertInstanceOf(RichSpan.Text.class, p.spans().get(1));
        assertEquals(0xFF0000, red.style().getColor().getValue());
        assertNull(normal.style().getColor());
    }

    @Test
    void malformedColorTokenIsTreatedAsLiteralText() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("{#NOTHEX}text");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        assertEquals("{#NOTHEX}text", plain(p.spans()));
    }

    @Test
    void inlineCodeSpanCarriesCopyText() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("run `code here` now");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.Text code = assertInstanceOf(RichSpan.Text.class, p.spans().get(1));
        assertEquals("code here", code.text());
        assertEquals("code here", code.copyText());
    }

    @Test
    void kbdTagWrapsLabelWithPaddingSpaces() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("<kbd>Ctrl</kbd>");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.Text kbd = assertInstanceOf(RichSpan.Text.class, p.spans().get(0));
        assertEquals(" Ctrl ", kbd.text());
        assertTrue(kbd.style().isBold());
    }

    @Test
    void httpAndWikiTargetsBecomeLinksTipTargetsBecomeTips() {
        List<RichBlock> blocks = ChronicleMarkdownParser
                .parse("[Ext](https://example.com) [Page](wiki:home) [Note](tip:hover text)");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));

        List<RichSpan.Link> links = p.spans().stream()
                .filter(RichSpan.Link.class::isInstance).map(RichSpan.Link.class::cast).toList();
        List<RichSpan.Tip> tips = p.spans().stream()
                .filter(RichSpan.Tip.class::isInstance).map(RichSpan.Tip.class::cast).toList();

        assertEquals(2, links.size());
        assertEquals("https://example.com", links.get(0).url());
        assertEquals("wiki:home", links.get(1).url());
        assertEquals(1, tips.size());
        assertEquals("hover text", tips.get(0).tooltip());
    }

    @Test
    void inlineImageTokenParsesResourceLocationAndDimensions() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("[img:minecraft:textures/item/diamond.png,32,16]");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.Image img = assertInstanceOf(RichSpan.Image.class, p.spans().get(0));
        assertEquals("minecraft:textures/item/diamond.png", img.texture().toString());
        assertEquals(32, img.w());
        assertEquals(16, img.h());
    }

    @Test
    void inlineImageWithoutDimensionsDefaultsTo48() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("[img:minecraft:textures/item/diamond.png]");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.Image img = assertInstanceOf(RichSpan.Image.class, p.spans().get(0));
        assertEquals(48, img.w());
        assertEquals(48, img.h());
    }

    @Test
    void itemIconTokenParsesResourceLocation() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("[item:minecraft:diamond]");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        RichSpan.ItemIcon icon = assertInstanceOf(RichSpan.ItemIcon.class, p.spans().get(0));
        assertEquals("minecraft:diamond", icon.itemId().toString());
    }

    @Test
    void invalidItemIconIdIsSwallowedRatherThanThrowing() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("[item:NOT A VALID ID!!]");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        assertTrue(p.spans().isEmpty());
    }

    @Test
    void smartQuotesAlternateOpenAndCloseButNotInsideCodeSpans() {
        List<RichBlock> blocks = ChronicleMarkdownParser.parse("say \"hi\" and `\"raw\"`");
        RichBlock.Paragraph p = assertInstanceOf(RichBlock.Paragraph.class, blocks.get(0));
        String rendered = plain(p.spans());
        assertTrue(rendered.contains("“hi”"));
        assertTrue(rendered.contains("\"raw\""));
    }
}
