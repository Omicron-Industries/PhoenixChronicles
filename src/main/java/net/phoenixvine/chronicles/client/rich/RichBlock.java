package net.phoenixvine.chronicles.client.rich;

import java.util.List;

/**
 * Block-level structure produced by {@link ChronicleMarkdownParser}.
 *
 * Every block owns its own list of inline {@link RichSpan}s (colors, links, tips, images —
 * parsed identically regardless of block type), plus whatever block-level metadata the renderer
 * needs to lay it out (heading level, list marker/indent). This is what lets a quest description
 * be actual structured Markdown instead of one flat span list with no paragraph/heading/list
 * concept — the thing both the compact card and the fullscreen panel were each missing in their
 * own way.
 */
public sealed interface RichBlock {

    List<RichSpan> spans();

    /**
     * level is 1-6, mirroring "#".."######". Rendered bold + a heading color, with extra
     * vertical breathing room before/after.
     */
    record Heading(int level, List<RichSpan> spans) implements RichBlock {}

    /**
     * A normal run of text. Consecutive source lines with no blank line between them are
     * joined with a single space (standard Markdown "soft break" behavior) before parsing.
     */
    record Paragraph(List<RichSpan> spans) implements RichBlock {}

    /**
     * marker is the literal glyph/number drawn at the left (e.g. "•" or "2."). indent is the
     * pixel offset applied to this item's text so wrapped continuation lines hang correctly
     * under the first line's text instead of under the marker.
     */
    record ListItem(String marker, int indent, List<RichSpan> spans) implements RichBlock {}

    /**
     * An explicit blank line preserved from the source — adds a bit of extra vertical gap
     * beyond the normal inter-block spacing, for intentional breathing room.
     */
    record Blank() implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }
}
