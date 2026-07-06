package net.phoenixvine.chronicles.client.rich;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Sealed hierarchy representing a parsed segment of Chronicles rich text.
 *
 * Chronicles format extensions (applied only to our own SNBT strings):
 * {#RRGGBB} — hex color switch
 * {reset} — reset all formatting
 * [label](url) — clickable hyperlink (url must start with http)
 * [label](tip:msg) — hover tooltip
 * [img:rl] — inline image at default 48×48
 * [img:rl,w,h] — inline image at custom size
 *
 * §X codes are NOT re-parsed here — Minecraft's own font renderer handles them
 * natively, so Text spans may contain §X and render correctly.
 *
 * & is NEVER touched by the parser. This guarantees zero conflict with any mod
 * that uses & in its own text (no FTB-style global & conversion).
 */
public sealed interface RichSpan
                                 permits RichSpan.Text, RichSpan.Link, RichSpan.Tip, RichSpan.Image {

    /** Plain or §-coded text with an optional hex-color Style overlay. */
    record Text(String text, Style style) implements RichSpan {}

    /** Clickable link — rendered underlined blue, opens url in browser on click. */
    record Link(String label, Style style, String url) implements RichSpan {}

    /** Hover tooltip — displays tooltip text when mouse is over the label. */
    record Tip(String label, Style style, String tooltip) implements RichSpan {}

    /** Inline image blitted at the given size. */
    record Image(ResourceLocation texture, int w, int h) implements RichSpan {}

    // ── Screen-space region produced by the renderer ──────────────────────────

    /** A rendered region associated with a RichSpan, used for mouse interaction. */
    record Region(int x1, int y1, int x2, int y2, RichSpan span) {

        public boolean contains(double mx, double my) {
            return mx >= x1 && mx < x2 && my >= y1 && my < y2;
        }
    }
}
