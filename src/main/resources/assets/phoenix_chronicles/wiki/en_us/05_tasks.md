# Task Types

## Built-in

- **kill_entity** — Kill mobs
  - target: entity_id, count, consume

- **item_check** — Have item(s) in inventory
  - target: item_id, count, consume - if AE2 is installed, also counts your linked ME network storage (per-task toggle: check_ae2_storage, on by default)

- **craft_item** — Craft an item
  - target: item_id, count

- **experience** — Reach an XP level
  - count: level

- **location_terminal** — Interact with a terminal
  - target: terminal_id, consume

- **advancement** — Earn an advancement
  - target: advancement_id

- **block_interact** — Place / right-click a block
  - target: block_id, secondary: PLACE|RIGHT_CLICK

- **fluid_check** — Have fluid in inventory
  - target: fluid_id, count: mB, consume - if AE2 is installed, also counts your linked ME network storage (per-task toggle: check_ae2_storage, on by default)

- **stat** — Reach a stat value
  - target: stat_id (e.g. minecraft:jump), count

- **dimension** — Enter a dimension
  - secondary: dimension_id

- **biome** — Visit a biome
  - target: biome_id

- **structure** — Enter a structure
  - target: structure_id

- **checkmark** — Manual checkbox
  - (no fields)

- **tag_item** — Have item matching tag
  - target: tag (e.g. c:ores/iron), count

- **info** — Display-only text panel
  - target: body text

- **external_trigger** — Fired by QuestAPI.fireExternalEvent()
  - target: trigger_id, count: times

- **energy_check** — Have stored energy
  - target: FE|EU|ANY, secondary: INVENTORY|HELD|BLOCK, count: FE

- **filter_item** — Have item(s) matching a composable filter
  - filter: exact/tag/mod/any_of/all_of/not, count, consume - if AE2 is installed, also counts your linked ME network storage (per-task toggle: check_ae2_storage)

- **filter_fluid** — Have fluid matching a composable filter
  - filter: exact/tag/mod/any_of/all_of/not, amount: mB, consume - if AE2 is installed, also counts your linked ME network storage (per-task toggle: check_ae2_storage)

{{kjs_task_types_block}}

---

## Filter Token items

Item Filter Token / Fluid Filter Token are physical items that carry a
composable filter (exact/tag/mod/any_of/all_of/not) - configure one once,
then reuse it across quests instead of re-entering the same rules by hand.

- **Right-click** — Open the filter editor for this token
- **Shift + Right-click** — Clear the token back to unconfigured (never happens on a plain right-click)
- **"Use Held Filter ⚡" button** — In the Tasks & Rewards editor, next to a filter_item/filter_fluid task's target field - applies whatever token you're holding directly, no retyping

The item picker (⊞ button) also accepts an already-configured token from your
inventory the same way - it auto-detects the token and applies its filter.

## Item/Fluid picker multi-select

Left-click picks one item/fluid as usual. Right-click toggles it into a
multi-select set (highlighted, count shown in the footer) - hitting Select
then adds every right-clicked entry in one go instead of reopening the
picker per item - useful for building an ANY-match list quickly.

---

## SNBT task entry format

```
{type: "kill_entity", task_id: "phoenix_chronicles:task_…", description: "…",
 target: "minecraft:zombie", count: 5, consume: false, optional: false}
```
