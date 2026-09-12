package net.phoenixvine.chronicles.client.rich;

import net.phoenixvine.chronicles.common.condition.ConditionNode;

import java.util.List;

public sealed interface RichBlock {

    List<RichSpan> spans();

    /**
     * {@code collapsible} is opt-in via a trailing {@code {collapse}} marker on the heading line (see
     * ChronicleMarkdownParser) -- an ordinary heading stays a plain, non-collapsing Heading exactly as
     * before; only a marked one gets wrapped into a {@link CollapsibleSection} by the parser's
     * post-process pass.
     */
    record Heading(int level, List<RichSpan> spans, boolean collapsible) implements RichBlock {}

    record Paragraph(List<RichSpan> spans) implements RichBlock {}

    record ListItem(String marker, int indent, List<RichSpan> spans) implements RichBlock {}

    record Checklist(String checkKey, boolean checkedDefault, int indent, List<RichSpan> spans) implements RichBlock {}

    record Quote(List<RichSpan> spans) implements RichBlock {}

    record CodeBlock(String lang, String code) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Callout(String type, String title, List<RichBlock> children) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Details(String expandKey, String title, List<RichBlock> children) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    /** {@code {scale:N}} alone on its own line -- scales every following block on the page by N. */
    record ScaleDirective(float multiplier) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Table(List<List<RichSpan>> header, List<List<List<RichSpan>>> rows) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Rule() implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Blank() implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    /**
     * A {@code :::if <expr>} ... {@code :::else} ... {@code :::} block (the {@code :::else} and
     * everything after it is optional) -- ported from Phoenix Archive. {@code <expr>} is the small
     * AND/OR/NOT boolean expression ChronicleMarkdownParser hands to ConditionExprParser, e.g.
     * {@code quest:main/forge AND NOT quest:main/reactor_meltdown}. {@code thenChildren} renders while
     * the condition currently holds, {@code elseChildren} (possibly empty) while it doesn't; unlike
     * every other block here this is re-checked every render, not just once at parse time, so a quest
     * description using this reads differently as quest state changes and can redact/reveal or swap
     * individual paragraphs instead of the whole description. See QuestTasksScreen#resolveConditionals.
     */
    record ConditionalSection(ConditionNode condition, List<RichBlock> thenChildren, List<RichBlock> elseChildren)
            implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    /**
     * A heading that opted into collapse/expand via a trailing {@code {collapse}} marker -- swallows
     * every block after it up to (not including) the next heading of the same or shallower level as its
     * own children, exactly like an ordinary heading defines a section, except this one can be toggled
     * shut. {@code collapseKey} is namespaced separately from Details' own expand-key space (see
     * ChronicleRichTextRenderer#collapseTrackingKey) so the two can share the same expanded-keys Set
     * without colliding. Ported from Phoenix Archive.
     */
    record CollapsibleSection(int level, List<RichSpan> headingSpans, String collapseKey, List<RichBlock> children)
            implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }
}
