# Reward Types

- **item** — Give item(s) to the player

- Fields: type, item (item_id), count

- **xp** — Award experience levels

- Fields: type, levels (integer)

- **command** — Run a server command as console

- Fields: type, command (%player% replaced with player name)

- **loot_table** — Roll a loot table, give all resulting items

- Fields: type, loot_table (resource location)

- **script_event** — Fire PhoenixQuestScriptRewardEvent on the Forge bus

- Fields: type, event_id, data (optional CompoundTag NBT)
- KubeJS: listen with ForgeEvents.onEvent('…ScriptRewardEvent', e => …)

- **conflux_unlock** — Grants a Conflux (PhoenixCore) research unlock or flag - no-op if PhoenixCore isn't loaded

- Fields: type, flag_mode (bool), node_id (resource location) or flag (string)

- **open_screen** — Tells the claiming player's client to open a screen from ExternalScreenRegistry

- Fields: type, screen_id (must be registered in Java - see API Reference)

---

## Choice rewards

Set reward_choice: true on the quest to let players pick from the rewards list.
reward_choice_count controls how many they may pick (default 1).
The reward screen shows all options; unchosen rewards are discarded.

- **Example (pick 1 of 3)**

```
reward_choice: true,  reward_choice_count: 1,
rewards: [
    {type: "item", item: "minecraft:diamond_sword", count: 1},
    {type: "item", item: "minecraft:diamond_pickaxe", count: 1},
    {type: "item", item: "minecraft:elytra", count: 1}
  ]
```

---

## SNBT reward entry format

```
{type: "item", item: "minecraft:diamond", count: 3}
{type: "script_event", event_id: "my_event", data: {key: 1}}
```

## Java event hook

```
@SubscribeEvent
public void onReward(PhoenixQuestScriptRewardEvent e) {
    e.getPlayer();  e.getEventId();  e.getData();
}
```
