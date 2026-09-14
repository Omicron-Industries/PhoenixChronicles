package net.phoenixvine.chronicles.client.rich;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.condition.ConditionNode;

import java.util.List;

public sealed interface RichSpan
                                 permits RichSpan.Text, RichSpan.Link, RichSpan.Tip, RichSpan.Image,
                                 RichSpan.CodeCopy, RichSpan.ItemIcon, RichSpan.DetailsToggle, RichSpan.TocJump,
                                 RichSpan.ChecklistToggle, RichSpan.ConditionalTip {

    record Text(String text, Style style, int background, String copyText, float scale) implements RichSpan {

        public Text(String text, Style style) {
            this(text, style, 0, null, 1f);
        }

        public Text(String text, Style style, int background) {
            this(text, style, background, null, 1f);
        }

        public Text(String text, Style style, int background, String copyText) {
            this(text, style, background, copyText, 1f);
        }
    }

    record Link(String label, Style style, String url) implements RichSpan {}

    record Tip(String label, Style style, String tooltip) implements RichSpan {}

    record TipCandidate(ConditionNode condition, String tooltip) {}

    record ConditionalTip(String label, Style style, List<TipCandidate> candidates) implements RichSpan {}

    record Image(ResourceLocation texture, int w, int h) implements RichSpan {}

    record ItemIcon(ResourceLocation itemId, String tooltip) implements RichSpan {

        public ItemIcon(ResourceLocation itemId) {
            this(itemId, null);
        }
    }

    record CodeCopy(String code) implements RichSpan {}

    record DetailsToggle(String key) implements RichSpan {}

    record TocJump(int targetY) implements RichSpan {}

    record ChecklistToggle(String key, boolean checkedDefault) implements RichSpan {}

    record Region(int x1, int y1, int x2, int y2, RichSpan span) {

        public boolean contains(double mx, double my) {
            return mx >= x1 && mx < x2 && my >= y1 && my < y2;
        }
    }
}
