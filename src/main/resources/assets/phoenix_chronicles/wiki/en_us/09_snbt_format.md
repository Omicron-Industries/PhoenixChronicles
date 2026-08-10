# SNBT File Format

Each quest is a single .snbt file in config/phoenix_chronicles/quests/
The file name (without .snbt) becomes the quest id if no id field is present.

## Minimal quest

```
{id: "my_quest", title: "My Quest"}
```

## Full example

```
{
  id: "magic/first_spell",
  title: "First Spell",
  description: "Cast your first spell.",
  subtitle: "Chapter 1",
  chapter: "MAGIC",
  shape: "CIRCLE",  node_size: "NORMAL",
  icon_item: "minecraft:book",
  positionX: 120,  positionY: 80,
  parent: "magic/intro",
  visibility: "NORMAL",
  repeat_mode: "NONE",
  reward_choice: true,  reward_choice_count: 1,
  auto_claim_rewards: false,
  dev_notes: "Placeholder until magic system is done.",
  tasks: [{type: "checkmark", task_id: "phoenix_chronicles:task_1",
           description: "Cast a spell"}],
  rewards: [
    {type: "xp", levels: 5},
    {type: "item", item: "minecraft:book", count: 1}
  ]
}
```

## Prerequisites list (extended format)

```
prerequisites: [
  {id: "magic/intro", required: true},
  {id: "magic/side",  required: false},
  {id: "magic/bad",   forbidden: true},
  {id: "magic/link",  required: true, link: true}
]
```

## File locations

- **Quest SNBT** — `config/phoenix_chronicles/quests/*.snbt` (any depth)
- **Quest markdown** — config/phoenix_chronicles/quests/{id}.md
- **Categories** — config/phoenix_chronicles/categories.txt (one per line)
- **Groups** — config/phoenix_chronicles/quest_groups.json
- **Settings** — config/phoenix_chronicles/settings.json
- **Tutorial prog.** — config/phoenix_chronicles/tutorial_progress.dat
