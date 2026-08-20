# Rich Text in Descriptions

Both the SNBT description field and .md files support rich text tags.
The parser handles { and [ only: & is never converted (unlike FTB Quests).

## Colour

- **{#RRGGBB}** — Set foreground colour: 6-digit hex, e.g. {#FF4444}
- **{reset}** — Return to default text colour

- Example: {#FFD700}Golden text{reset} back to normal

## Inline images

- **[img:rl,w,h]** — Embed a texture inline with the text

rl = resource location, e.g. minecraft:textures/item/diamond.png
w,h = pixel dimensions in GUI space (optional, default 16x16)

- Example: [img:minecraft:textures/item/diamond.png,16,16]
- Example: [img:mymod:textures/gui/banner.png,64,32]

Images fall back to the SNBT description field if no .md file is found,
so you can embed textures directly in the quest creator without a .md file.

## Links

- **[label](url)** — Clickable hyperlink: opens in the system browser
- **[label](tip:text)** — Tooltip-only reference: shows text on hover, no click

- Example: [Phoenix Wiki](https://example.com/wiki)
- Example: [Mana Crystal](tip:Dropped by Silverfish in End biomes)

## Minecraft formatting codes

§ codes (§l bold, §c red, etc.) work normally in both SNBT and .md files.
& is NOT processed: &wHelloooo passes through literally as written.
This avoids conflicts with other mods that use & for their own purposes.

## Markdown files (.md)

Place a file at: config/phoenix_chronicles/quests/{quest_id}.md
The .md file content is shown in the fullscreen quest view. If absent,
the SNBT description field is used instead (including any rich text tags).

**The compact card view** (the small popup you get from a normal click) uses
only the inline tag set above - colour, images, links/tips, § formatting
codes. No headings, bold/italic, lists, tables, or callouts there - it's a
tight fixed-height card interleaved with the task list, not built for
long-form content.

**The fullscreen view** (`[+]` expand button) uses the *same full block-level
markdown engine as this wiki* - `# headings`, **bold**, *italic*, `~~strikethrough~~`,
`==highlight==`, `<kbd>Key</kbd>` badges, lists, `- [ ] checklist items`,
`` `code` `` spans, fenced code blocks, `| tables |` (wraps and clicks work
the same as here), `:::note`/`:::warning`/`:::tip` callouts, `:::spoiler`
collapsible sections, `[item:minecraft:diamond]` live item icons, and `[^id]`
footnotes all work. See the wiki's own source files
(`assets/phoenix_chronicles/wiki/en_us/*.md`) for syntax examples of all of
these - it's the exact same parser. One difference: checklist checkmarks in
quest descriptions reset each session (no per-quest persistence), while the
wiki's own checklists remember their checked state across sessions.

## Page breaks

- **---** — A line with just 3+ dashes splits the description into pages instead of one continuous scroll - purely opt-in, a description with no marker behaves exactly as before.

Works in both the SNBT description field and .md files, and shows in both
the compact card and fullscreen quest views with a Prev/Next pager pill.
The in-game description editor has a dedicated "PB" toolbar button that
inserts one at the cursor. FTB Quests imports: {@pagebreak} in the source
description maps to this automatically.

## Heading sizes

`#`, `##`, and `###` aren't just colour differences - each level renders at
a real, larger size, and levels 4-6 fall back to the `###` treatment:

# This is an H1
## This is an H2
### This is an H3

Bold text is scaled up too, everywhere - not just in headings - since
Minecraft's normal bold is just a heavier stroke at the same size:

**This bold sentence reads noticeably bigger than this plain one.**

## Collapsible sections

Any heading (this page is full of them) doubles as a collapse toggle -
click the ▾/▸ arrow next to a heading's text to fold or unfold everything
under it, down to the next heading of the same or shallower level. It
defaults open, so nothing about a page's contents changes unless someone
clicks. `:::spoiler` blocks (below) still exist too, for collapsible
content that isn't naturally headed by a heading.

## Per-text scaling

- **{scale:X}...{reset}** — Scale a run of inline text up or down (X is a
  multiplier, e.g. 1.4), stacking multiplicatively with whatever size that
  text would already render at (page default, heading level, bold)

- Example: Normal size, then {scale:1.6}noticeably bigger{reset}, back to normal.
- Example: {scale:0.8}fine print{reset} for a caveat that shouldn't compete with the main text.

A whole page can also opt into a bigger (or smaller) base size by putting
`{scale:1.2}` alone on its own line anywhere in the source - see the
[Live Stats](wiki:live_stats) page for a worked page-level example.

## Clickable links inside code blocks

Fenced code blocks aren't just syntax-highlighted plain text anymore -
`[label](wiki:...)`, `[label](tip:...)`, and `[label](https://...)`
annotations work inside them too, reusing the same click/hover handling
as normal inline links:

```yaml
# See [the SNBT format reference](wiki:snbt_format) for the full schema
visibility: "HIDDEN"  # [what does HIDDEN mean?](tip:Hidden until an ancestor prerequisite completes)
```
