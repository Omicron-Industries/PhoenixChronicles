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
