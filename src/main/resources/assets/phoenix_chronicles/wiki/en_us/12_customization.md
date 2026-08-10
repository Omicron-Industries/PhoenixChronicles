# Customization

Everything below is dev-mode-only pack authoring - players never see any of
these editors, only their results.

## Sidebar & Questbook Title

- **Chapter tile** — Left-click select, right-click → chapter theme editor
- **Collapse toggle** — Small arrow above the chapter list - reclaims canvas width
- **Questbook icon/name** — Top-left of the sidebar - click to open the naming popup

Book icon + name default to a generic "Quest Book" until set.

- **Sidebar Behavior** — Settings → General - COLLAPSIBLE (default, click the arrow above) or HOVER_TO_EXPAND (FTB Quests-style: always collapsed, opens on mouseover, closes when the mouse leaves - no manual toggle in this mode).

---

## Chapter theme (right-click canvas → Edit chapter theme…)

- **Display name** — Raw override; empty = derived from the chapter slug
- **Icon** — Sidebar tile icon for this chapter
- **Style** — DOT_GRID / GRID_LINES / HEX_GRID / DIAGONAL_LINES / SOLID / CUSTOM
- **Color tint** — #RRGGBB overlay on the canvas background

Picking a texture (Browse…) auto-switches Style to CUSTOM - the two used to
be independent, so choosing a texture silently did nothing until Style was
also changed by hand. That's fixed; a texture pick sets both now.

## Custom textures (Browse… button, or Add picture…)

Drop PNGs in config/phoenix_chronicles/textures/ - the browser lists them as
phoenix_chronicles:textures/custom/<relative-path>. That location was never a real
game asset path (Minecraft only loads assets/<namespace>/... from a jar or
resource pack), so these are dynamically registered at runtime the first time
they're drawn (see CustomTextureCache) instead of needing a resource pack.

- **Left-click a thumbnail** — Select and apply
- **Right-click a thumbnail** — Copy its resource location to clipboard

---

## Background pictures (right-click canvas → Add picture…)

Freestanding decorative images, separate from the chapter theme above -
positioned in canvas space, so they pan/zoom with the graph like nodes do.

- **Add** — Right-click empty canvas → Add picture… → pick a texture
- **Move** — Shift+drag the picture directly
- **Right-click a picture** — Move / Resize ▸ / Resize (scroll+drag)… / Move to Chapter ▸ / Delete
- **Resize ▸** — Fixed presets: 32 / 64 / 128 / 256 / 512 / 1024 px
- **Resize (scroll+drag)…** — Interactive mode - bypasses canvas zoom/pan entirely

- Scroll = resize (±20%, shift = fine ±5%), drag = move, right-click/Esc = done

Every add/move/resize/chapter-move/delete is one Ctrl+Z-undoable step, even
a whole interactive resize session (one entry covers the full edit).

---

## Dependency lines (right-click canvas → Dependency lines…, or per-quest)

- **Line Style** — THIN / NORMAL / BOLD / THICK / WIDE / GLOW - controls rail width
- **Line Anim Speed** — How fast the arrow chain travels on hover

Base rail/arrow colors (locked/active/done) come from the active theme's
locked/activeColor/done colors - editing the theme recolors dependency lines
along with everything else. The hover-boost colors (cyan for what a quest
needs, amber for what it unlocks) are intentionally fixed, not themed.
Lines are static until you hover a connected quest; only that quest's own
edges animate, to avoid the whole tree moving at once.

---

## Quest toasts (Settings screen, or right-click a quest → Design toast…)

- **Toast Style** — COMPACT (corner banner) / ABOVE_HOTBAR / BIG_CENTER - global default
- **Toast Position** — Which corner, for the COMPACT style

Any quest without its own design uses whichever Toast Style is selected.

- **Design toast…** — Per-quest custom layout - freeform icon/title/label position+color

Drag the icon/title/label directly in the live preview; the side panel is split
into tabs (Element, Icons, Backdrop, Presets) instead of one long scrolling list.

- **Backdrop tab** — Background is always its own independent box - drag its middle to move it,

drag a corner handle to resize it, or use "Fit to elements now" to snap it around
the icon/title/label's CURRENT positions. It never moves on its own just because
you dragged one of them elsewhere.

- **Position fields / arrow keys** — Numeric % X/Y for pixel-precise placement, or nudge with arrow keys
- **Alignment guides** — Dragging near screen-center or another element snaps + shows a guide line
- **Previewing: Complete / Unlock** — Toggle which toast text the live preview shows, without triggering a real one
- **Reset element** — Restores just the selected icon/title/label to default - leaves the rest alone
- **Copy / Paste design** — In-memory clipboard - copy one quest's design, paste it onto another's
- **Save as preset… / Load preset** — Named, reusable templates saved to config/phoenix_chronicles/toast_presets.json
- **Reset to default style** — Deletes the quest's custom design (only shown once one exists)

---

## Recipe viewer (EMI)

Clicking a task/reward item icon opens EMI's recipe browser for it, if EMI
is installed (JEI has no integration yet). Pressing Escape returns you to
the quest book instead of EMI's own default (a throwaway inventory screen,
then straight to gameplay) - this is a client setting, on by default:

- **Return to Quest Book from Recipe Viewer** — Settings → General - opt out here

---

## Player Settings screen (all players - gear icon, not dev-only)

Everything above this line on the page is dev/pack-authoring only. The
Settings screen itself is for every player and covers, by topic:

- **General** — Text Scale, Theme, Layout Density, Reduce Motion, Recipe Viewer, Theme Editor
- **HUD** — Position, Opacity, Show Title/Progress/Rewards for the pinned-quest tracker
- **Pop-Ups** — Master on/off, Style, Position, Sounds for unlock/complete toasts
- **Canvas** — Hide Completed by default, Grid Snap, dependency line style/animation
- **Phantasia** — Auto-Spin for embedded 3D multiblock/scene previews
- **Inventory** — Show/hide and position the quest-book button on the inventory screen
- **Dev Mode** — The opt-in toggle that unlocks everything documented on this page, plus:

- **Show Dev Info by Default** — Opens quest editors with dev-only fields expanded by default
- **Show Flag-Disabled Chapters** — Off by default: the sidebar normally hides chapters a chapter_flags.snbt rule disabled (e.g. pack-mode variants) - turn on to see/edit them anyway
- **Show Flag-Disabled Quests** — On by default: within a visible chapter, also show individual quests a flag rule disabled (marked with a purple ⚑ border) so you can edit them in place
- **Always-On Profiler** — Off by default: keeps the Ctrl+P profiler running all session, logging a snapshot every 10s - for tracking intermittent perf issues without remembering to toggle it
