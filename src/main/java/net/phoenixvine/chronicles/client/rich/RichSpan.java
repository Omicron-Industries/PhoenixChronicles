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

    /** One candidate tooltip body for a {@link ConditionalTip}, guarded by a condition. */
    record TipCandidate(ConditionNode condition, String tooltip) {}

    /**
     * A footnote reference ({@code [^id]}) whose definition carries a {@code flag:}-guarded variant
     * (see ChronicleMarkdownParser's {@code [^id?<expr>]:} footnote-def syntax) -- e.g. an OP/QA-only
     * clarification layered on top of the public note, both attached to the same {@code [id]} marker
     * in the text. Unlike a plain {@link Tip} (a single, fixed tooltip baked in at parse time), the
     * winning tooltip here is picked fresh every render from {@code candidates} (first whose condition
     * holds, in file order; a candidate with a {@code null} condition always matches) -- the same
     * "re-checked every render" behavior {@code RichBlock.ConditionalSection} already gives block-level
     * {@code :::if} content, just for a single inline footnote instead of a whole paragraph. Resolved
     * into a plain {@code Tip} (or bare {@link Text} if nothing matches) by
     * QuestTasksScreen#resolveConditionals before rendering -- the renderer itself never sees this type.
     */
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
