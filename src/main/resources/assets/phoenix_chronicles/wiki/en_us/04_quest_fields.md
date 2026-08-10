# Quest SNBT Fields

All fields are optional except id and title.

## Identity

- **id** — Path portion only: e.g. "my_quest" → phoenix_chronicles:my_quest
- **title** — Display name shown in quest headers and search
- **description** — Lore / body text
- **subtitle** — Smaller text below the title on the detail card

## Appearance

- **chapter** — Chapter tab name: e.g. MAIN, MAGIC, COMBAT
- **shape** — SQUARE · CIRCLE · DIAMOND · HEXAGON · TRIANGLE · STAR · PENTAGON · SHIELD · CROSS
- **node_size** — TINY(14px) · SMALL(18px) · NORMAL(32px, default) · LARGE(48px) · HUGE(64px)
- **node_size_px** — Optional exact pixel override (8-200), takes priority over node_size - set via the canvas's right-click → "Resize (scroll + drag)…", not hand-edited
- **icon_item** — Item id for the node icon: e.g. minecraft:diamond [item:minecraft:diamond] [^itemicon]
- **icon_fluid** — Fluid id for the node icon (flat tinted square): e.g. minecraft:water - icon_texture > icon_fluid > icon_item in priority, mutually exclusive
- **background** — Animated backdrop id behind the icon: built-in "sun"/"glitch", or a custom id registered via QuestBackgroundRegistry - see API Reference
- **external_screen** — Opens a registered Screen on click instead of the normal quest popup - id must be registered via ExternalScreenRegistry, see API Reference
- **positionX** — Canvas X coordinate (pixels from left edge)
- **positionY** — Canvas Y coordinate (pixels from top edge)

## Visibility

- **visibility** — NORMAL · HIDDEN · MYSTERY · DISABLED
- **enable_if** — Flag expression: quest hidden+disabled when false
- **hide_dep_line** — true / false: hides all dep lines on the canvas
- **disabled_blocks_children** — true → DISABLED quest still gates children

## Completion

- **optional** — true → marks the quest itself optional (distinct from optional TASKS)

- Shown as a purple "Optional" badge in the canvas hover tooltip and the
  Quest Tasks header, and skipped entirely when it's someone else's
  prerequisite - an optional quest never has to be completed to unlock a
  quest that lists it as a prereq, no matter that quest's AND/OR-gate or
  per-prereq required/forbidden settings. Doesn't change chapter-completion
  math.

- **task_min_count** — 0 = all tasks required; N = complete any N tasks
- **repeat_mode** — NONE · DAILY · COOLDOWN · INFINITE
- **repeat_cooldown_hours** — Hours between repeats (COOLDOWN mode only)

## Prerequisites

- **parent** — Canvas-tree grouping only (not unlocking) - auto-set to the first prerequisites entry, or "none"
- **require_all_prereqs** — true = AND gate; false = OR gate (legacy)
- **prerequisites** — List of {id, required, forbidden, link} tags
- **optional_prereq_min_count** — Min optional prereqs needed (0 = all)

## Rewards (on the quest, not inside rewards list)

- **reward_choice** — true → player picks N rewards instead of getting all
- **reward_choice_count** — How many rewards the player may pick (default 1)
- **auto_claim_rewards** — true → rewards are automatically given on completion

## Developer

- **dev_notes** — Free-text notes visible only in the quest editor (not to players)

## Tutorial

- **tutorial_steps** — List of {text, highlight} tags: see Overview page

## Multiplayer / Teams

- **shared** — true → completing this quest cascades to all online teammates

Team membership is resolved in this order: Phoenix Guilds, then FTB Teams
(party/server teams only), then falling back to Minecraft's built-in
scoreboard teams (/team add, /team join) if neither mod is installed.
Task progress remains per-player; only the final COMPLETED state is shared.
New quests default `shared` to true automatically when Phoenix Guilds or
FTB Teams is installed (there's no team to share with otherwise) - existing
quests keep whatever value is already saved in their file.

[^itemicon]: The icon shown here is rendered live with vanilla item rendering (via `[item:id]`), not a static picture. Give it a custom hover tooltip with `[item:id|Some tooltip text]` - if you leave off the `|tooltip`, it shows nothing on hover.
