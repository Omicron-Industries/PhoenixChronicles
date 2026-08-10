# Tutorial Overlays

A per-quest guided-overlay system: a sequence of Prev/Next/Skip cards that dim the screen and
spotlight a specific part of the UI, walking a new player through it step by step.

:::note
This is entirely separate from this dev wiki. The dev wiki is a reference for pack authors; a
tutorial overlay is what regular players see in-game, and it's driven by data on a quest, not by
anything in `assets/phoenix_chronicles/wiki/`.
:::

## How it triggers

There's no button or menu to start one - it's automatic. Every frame, the overview screen checks
for the first quest (in registry order) that:

- has a non-empty `tutorial_steps` list,
- is in state `ACTIVE` or `UNLOCKED` for the current player, and
- hasn't already been dismissed by that player.

If one exists, its overlay is shown. There's no way to have two tutorials queued and shown
back-to-back automatically - only ever the first matching quest at a time.

:::warning
This is **not** gated by dev mode - there's no dev-mode check anywhere in the trigger logic. If
you're op'd/in Creative testing a quest that has `tutorial_steps` and is ACTIVE/UNLOCKED and not
yet dismissed for you personally, you'll get the exact same overlay a real player would, popping
up over your editing session. It only stops once you Skip/Finish it for your own player, same as
anyone else - see the progress-storage section below for how to reset that while testing.
:::

## SNBT field

```
tutorial_steps: [
  { text: "…", highlight: "…" },
  { text: "…", highlight: "…" }
]
```

- **text** — required. Steps with blank text are silently skipped.
- **highlight** — optional, defaults to `"none"` if omitted. Valid values:

| Value | Effect |
|---|---|
| `none` | No spotlight - just the text card, full-screen dim |
| `sidebar` | Spotlights the chapter sidebar |
| `canvas` | Spotlights the quest canvas area |
| `toolbar` | Spotlights the top toolbar (search/filters/zoom) |
| `node:{quest_id}` | Spotlights a specific quest node, using its live on-screen position |

`node:` targets track the node's *actual current position* on the canvas - if a pack author moves
the node later, the highlight follows it automatically, nothing to update by hand.

## Player-facing behavior

One step shown at a time. Prev is hidden on the first step; the last step's Next button reads
"Finish" instead. Skip is available on every step and is **permanent** - once skipped there is
currently no in-game way for a player to bring it back (see below).

## Progress storage

Client-side, in `config/phoenix_chronicles/tutorial_progress.dat` - one integer per quest id (the
current step index the player is on). `-1` means dismissed/finished; no entry at all means the
player hasn't started it yet.

- **`/chronicles tutorial reset <quest_id>`** — replays a specific tutorial (tab-completes to
  quests that actually have tutorial steps). Client-side only, no permission needed - it just
  edits your own `tutorial_progress.dat`.
- **`/chronicles tutorial reset`** (no argument) — clears progress for every tutorial at once.

## Worked example

The `getting_started/welcome.snbt` quest (a real quest in this pack, not a hypothetical) uses all
four highlight types to walk a new player through the sidebar, their first quest, and the toolbar:

```
tutorial_steps: [
  { text: "Welcome to Phoenix Chronicles! This sidebar lists every chapter in the pack - click one to jump to it.",
    highlight: "sidebar" },
  { text: "This is your first quest. Complete the tasks below and claim your reward to get started.",
    highlight: "node:welcome" },
  { text: "Up here: search, chapter filters, and zoom. The '?' button opens this wiki if you're in dev mode.",
    highlight: "toolbar" },
  { text: "That's the basics. Good luck out there!",
    highlight: "none" }
]
```

Load into a world with that quest ACTIVE or UNLOCKED and undismissed to see it fire.
