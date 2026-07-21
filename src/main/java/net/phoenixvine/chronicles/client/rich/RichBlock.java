package net.phoenixvine.chronicles.client.rich;

import java.util.List;

public sealed interface RichBlock {

    List<RichSpan> spans();

    record Heading(int level, List<RichSpan> spans) implements RichBlock {}

    record Paragraph(List<RichSpan> spans) implements RichBlock {}

    record ListItem(String marker, int indent, List<RichSpan> spans) implements RichBlock {}

    record Blank() implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }
}

