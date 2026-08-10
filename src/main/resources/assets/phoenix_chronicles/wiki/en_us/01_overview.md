# Phoenix Chronicles: Dev Reference

In-game quest system for Minecraft Forge 1.20.1.
Dev mode activates automatically in Creative or at op level ≥ 2.

## Live registry

- **Quests loaded:** {{quest_count}}
- **Categories:** {{category_count}}
- **Task types:** {{task_type_count}} registered

---

## Quick navigation

- **[Getting Started](wiki:getting_started)** New to this? Start here: the actual click-by-click workflow
- **[Canvas](wiki:canvas)** Pan, zoom, right-click, Alt+drag dep lines.
- **[Quest Fields](wiki:quest_fields)** All SNBT keys and their meanings.
- **[Tasks](wiki:tasks)** Every task type with expected fields.
- **[Rewards](wiki:rewards)** All reward types: including choice rewards.
- **[Variants](wiki:variants)** Per-quest overrides for expert mode, seasonal content, etc..
- **[Rich Text](wiki:rich_text)** {#RRGGBB} colour, [img:…] inline textures, [links].
- **[SNBT Format](wiki:snbt_format)** Full file format reference and folder layout.
- **[Live Stats](wiki:live_stats)** Per-chapter quest counts and type breakdown.
- **[Customization](wiki:customization)** Sidebar, chapter theme, background pictures, toasts.
- **[Tutorial Overlays](wiki:tutorials)** Guided Prev/Next/Skip overlays for new players.

---

## Tutorial quests

Add `tutorial_steps` to any quest SNBT to attach a guided, spotlight-highlighting overlay that
walks new players through the UI - see [Tutorial Overlays](wiki:tutorials) for the full field
reference, how triggering works, and a real worked example.
