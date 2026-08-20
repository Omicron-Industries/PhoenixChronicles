# Variants (per-quest conditional overrides)

A variant lets ONE quest present differently depending on a flag condition -
a different title/description, different tasks, different rewards, even a
different visibility - without duplicating the quest itself. Common uses:
an "expert mode" pack toggle, seasonal/event content, or per-tier reward
scaling. This is entirely separate from the enable_if field on the base
quest - enable_if controls whether the quest exists at all; a variant only
changes what it LOOKS like once it's already there.

A single quest isn't limited to one variant - add as many as you need (e.g.
one each for Normal / Expert / Hardcore pack modes); see "Resolution rule"
below for how the list is evaluated when more than one is present.

---

## There is no built-in "pack mode" switch

This is the part that trips people up: variants don't read from some
dedicated "current pack mode" setting, because no such setting exists.
A variant's condition is a plain enable_if-style flag expression (see Quest
Fields → Visibility, and the API Reference page's flag section for full
syntax) - evaluated fresh every time the quest is resolved. "pack_mode" is
just a NAME a pack dev chooses to use consistently; you set what it means
via one of the same flag mechanisms enable_if already uses:

- **config:file#key[=val]** — Read from a `config/*.toml|.json|.properties` file
- **kjs:key[=val]** — Read from config/phoenix_chronicles/kjs_flags.json (KubeJS-writable) - same comparison operators as config:, not existence-only
- **flag:name** — Set once in Java via PhoenixQuestFlags.setFlag(name, bool)

Both config: and kjs: take the same optional comparator: `=` `!=` `>` `>=`
`<` `<=` (config:pack.toml#tier>=2, kjs:pack_tier>=2) - leave it off for a
plain existence check. See the API Reference page's flag section for the
exact comparison semantics (string vs. numeric, case sensitivity, what
counts as "false").

Pick ONE mechanism and use it consistently across every variant's
condition in the pack - mixing conventions per-quest is how packs end up
with quests that silently never match any variant.

---

## Resolution rule: first match wins

A quest's variants list is checked IN ORDER; the first whose condition
evaluates true is the active one for that check - not the most specific,
not a merge of several matches. If a variant's condition is true, whatever
field IT sets wins outright; a field it leaves blank falls back to the base
quest's own value (fields never merge ACROSS variants either - only base
↔ single matched variant). If nothing matches, the base quest is used
as-is. Order variants from most to least specific in the list.

---

## What a variant can override

- **title / description** — Replace the base text - leave blank to inherit
- **visibility** — NORMAL / HIDDEN / MYSTERY / DISABLED - leave as Inherit to keep base
- **tasks** — REPLACES the base task list entirely, not merged - all-or-nothing
- **rewards** — REPLACES the base reward list entirely, not merged

Not overridable per variant: chapter, shape, icon, position, prerequisites,
repeat mode - those stay the same regardless of which variant is active.

---

## Editing variants in-game

- **"◈ Variants (N)" button** — In the Quest Creator, next to "⊞ Tasks & Rewards"

Opens a list of this quest's variants (+ Add Variant / ▲▼ reorder / × delete).
Selecting one shows: a condition text box, title/description override boxes,
a visibility cycle button, and "Edit Tasks/Rewards…" - the SAME task/reward
editor screen used for the base quest, just scoped to that variant. A
"Clear task/reward override" button appears once one is set, to revert
back to inheriting the base list. There's no separate "base vs. variant"
editing mode - base fields are still edited directly in the Quest Creator;
this screen is purely the overlay list of overrides.

---

## SNBT shape

Only written at all if the quest has at least one variant:

```
variants: [
  { condition: "config:pack_mode.toml#general.mode=expert",
    title: "Expert-Only Challenge",
    visibility: "HIDDEN",
    tasks: [ {task_id: "...", type: "...", ...} ],
    rewards: [ {type: "item", item: "...", count: 1} ] },
  { condition: "kjs:pack_tier>=2", description: "Tier 2 flavor text" }
]
```

Each block only needs the keys it actually overrides - condition is the
only field always written.
