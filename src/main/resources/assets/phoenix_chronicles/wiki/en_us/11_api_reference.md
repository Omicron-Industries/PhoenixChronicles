# API Reference

Click ⎘ to copy any snippet to clipboard.

## QuestAPI (Java: net.phoenixvine.chronicles.QuestAPI)

The single entry point for other mods/scripts to read or push quest state.
Every method here is safe to call with a bad/unknown quest ID or a null
player - it logs a warning once (not spammed) and returns a safe default
instead of throwing, so a typo doesn't silently fail with no explanation.

- **fireExternalEvent** — Advance any external_trigger task listening for this id (server-side)

```
QuestAPI.fireExternalEvent(serverPlayer, "mymod:killed_dragon", null);
```

- **forceComplete** — Force a quest straight to COMPLETED, bypassing its tasks (server-side)

```
QuestAPI.forceComplete(serverPlayer, "phoenix_chronicles:my_quest");
```

- **setState** — Force a quest to any state, bypassing tasks/prereqs (server-side)

```
QuestAPI.setState(serverPlayer, "phoenix_chronicles:my_quest", QuestState.LOCKED);
```

- **getState** — Read a player's current state for a quest - callable either side

```
QuestState state = QuestAPI.getState(player, "phoenix_chronicles:my_quest");
```

- **getAllStates** — Every quest state for this player, keyed by ID - a snapshot map

```
Map<ResourceLocation, QuestState> all = QuestAPI.getAllStates(player);
```

- **isCompleted / isUnlocked** — Convenience booleans built on getState

```
boolean done = QuestAPI.isCompleted(player, "phoenix_chronicles:my_quest");
```

- **getProgress** — 0.0-1.0 fraction of non-optional tasks done (1.0 if already COMPLETED)

```
float pct = QuestAPI.getProgress(player, "phoenix_chronicles:my_quest");
```

---

## Forge event hooks (Java: net.phoenixvine.chronicles.event.QuestEvent)

All nested inside QuestEvent except PhoenixQuestScriptRewardEvent below, which
is its own top-level class. getPlayer()/getNode() are on the QuestEvent base.

- **QuestEvent.StateChanged** — Fired right after a quest's state actually changed

```
@SubscribeEvent
public void onStateChanged(QuestEvent.StateChanged e) {
    if (e.getNewState() == QuestState.COMPLETED) {
        ServerPlayer p = (ServerPlayer) e.getPlayer();
        ResourceLocation id = e.getNode().getId();
    }
}
```

- **QuestEvent.RewardClaimed** — Cancelable - fired once rewards for a quest are granted

```
@SubscribeEvent
public void onReward(QuestEvent.RewardClaimed e) { … }  // cancel() to veto the grant
```

- **QuestEvent.ExternalEvent** — Cancelable - fired when QuestAPI.fireExternalEvent() runs

```
@SubscribeEvent
public void onExternal(QuestEvent.ExternalEvent e) {
    String triggerId = e.getTriggerId();
    CompoundTag data = e.getData();  // empty tag if none was passed
}
```

- **QuestEvent.PlayerTick** — Cancelable - cancel to suppress default task checks this tick
- **QuestEvent.PinChanged** — Fired when a player pins/unpins a quest on the HUD tracker
- **QuestEvent.TreeReloaded** — Quest tree (re)loaded from disk - getPlayer()/getNode() are null

- **PhoenixQuestScriptRewardEvent** — Fired by a script_event reward - carries custom data

```
@SubscribeEvent
public void onScriptReward(PhoenixQuestScriptRewardEvent e) {
    String evtId = e.getEventId();      // matches event_id in SNBT
    CompoundTag data = e.getData();     // optional NBT payload
    ServerPlayer p = e.getServerPlayer();
}
```

---

## Custom task type (Java - full QuestTask subclass)

Implement QuestTask, then register a builder in your mod's common setup:

```
PhoenixTaskRegistry.register("mymod:eat_sun", tag -> new EatSunTask(tag))
        .label("Eat the Sun")
        .icon("§c☀")
        .tooltip("Eat a star.\nTarget: star registry id.")
        .field(PhoenixTaskRegistry.FieldDef.itemId("target", "Star"))
        .register();
```

.field(...) entries drive the quest editor's auto-generated form for this
task type - skip it and the editor just won't show dedicated fields for it.
This registers a real Java subclass, so a JS script can't satisfy it directly -
for a script-only custom task type with genuine per-player completion logic,
use registerScripted below instead - no Java class needed at all.

---

## Custom task type (KubeJS - real script task, no Java mod needed)

registerScripted() hands back the same Builder as above, but backed by a
script-driven task under the hood - your callbacks run fresh for every
completion check, not just a fire-and-increment counter like the JSON bridge's
external_trigger tasks below - this is genuine custom completion logic.

```
PhoenixTaskRegistry.registerScripted('mypack:eat_sun')
  .onCompleted((task, player) => {
    // task.getData() is the raw SNBT tag for this quest's copy of the task -
    // read whatever custom fields you declared with .field(...) below
    return player.getPersistentData().getInt('suns_eaten')
        >= task.getData().getInt('count')
  })
  .onConsume((task, player) => {
    // optional - runs once when the player claims this quest's rewards
  })
  .progressString((task, player) => {
    // optional - return null to fall back to a plain Done/Pending label
    return `${player.getPersistentData().getInt('suns_eaten')}/${task.getData().getInt('count')}`
  })
  .dependsOnInventory(false)  // default true - set false unless completion
                              // can ONLY change when the player's items do
  .label('Eat the Sun').icon('§c☀').tooltip('Eat a star.')
  .field(PhoenixTaskRegistry.FieldDef.integer('count', 'Suns to eat'))
  .register()
```

onCompleted is required; onConsume/progressString/dependsOnInventory are all
optional and default to no-op/null/true respectively. Call this from a
startup_script - PhoenixTaskRegistry is already a global binding, no
Java.loadClass needed.

---

## Custom enable_if flags (Java)

enable_if expressions: comma = AND, pipe = OR (lower precedence than AND,
so "a,b|c,d" = (a AND b) OR (c AND d)), ! negates a single term.
A bare name checks the static/dynamic registry below; prefixed forms route to
a built-in provider: mod:modid, rule:gameRuleName[ op value], config:file#key
[ op value], kjs:key[ op value]. Comparison ops (rule:/config:/kjs: - NOT
mod: or a bare flag name, those are existence-only): = != > >= < <=
config: and kjs: use the exact same comparison syntax and semantics - kjs:
is not existence-only, it takes an operator too.

- `=` / `!=` compare as plain strings, case-insensitively (`Expert` matches
  `expert`)
- `> >= < <=` parse both sides as numbers; if either side isn't a valid
  number the comparison is simply false (no error)
- No operator at all (`config:file#key`, `kjs:key`, a bare flag name) is an
  existence check: false if the value is missing, blank, or textually
  "false" / "0" / "null" (case-insensitive) - true otherwise

- enable_if: "expert_mode" (plain registered flag)
- enable_if: "!hardcore,mod:refinedstorage" (NOT hardcore AND RS loaded)
- enable_if: "rule:doDaylightCycle=false" (game rule comparison)
- enable_if: "kjs:pack_tier>=2" (numeric comparison against a KubeJS-written flag)

- **setFlag** — Static value, set once (e.g. a pack-mode read at startup)

```
PhoenixQuestFlags.setFlag("expert_mode", true);
```

- **registerCondition** — Dynamic - re-evaluated on every check, keep it cheap

```
PhoenixQuestFlags.registerCondition("has_rs",
        () -> ModList.get().isLoaded("refinedstorage"));
```

- **registerProvider** — Adds a whole new prefix namespace (your own PREFIX:expr syntax)

```
PhoenixQuestFlags.registerProvider(new MyCustomProvider());
```

Unknown plain flag names default to TRUE (with a one-time console warning) so
a typo doesn't silently hide a quest; unknown prefixes default to FALSE.

---

## KubeJS - real plugin (PhoenixChroniclesKubeJSPlugin)

A genuine KubeJSPlugin (kubejs-forge is a compileOnly build dependency now,
registered via kubejs.plugins.txt) allowlists QuestAPI, QuestState, QuestNode,
QuestEvent, PhoenixQuestScriptRewardEvent, QuestTask, PhoenixTaskRegistry (+
Builder/FieldDef), and PhoenixQuestFlags for script access, and adds three
global bindings so scripts skip the Java.loadClass boilerplate entirely:

- **QuestAPI** — same methods as the Java API page above

```
QuestAPI.fireExternalEvent(player, 'my_trigger_id', null)
```

- **PhoenixTaskRegistry** — only useful from script for an already-Java-defined task type -

- QuestTask is an abstract class with multiple abstract methods, not
  something a plain JS object can satisfy; see the JSON bridge below instead
  for a script-only custom task type.

- **PhoenixQuestFlags** — setFlag/registerCondition/registerProvider, same as the Java API

The mod itself never touches this plugin class - KubeJS only instantiates it
if KubeJS is actually installed, so nothing here requires KubeJS to be present.

- **Listen for a quest state change** — server_scripts (standard Forge event, no bridge needed)

```
ForgeEvents.onEvent(
  'net.phoenixvine.chronicles.event.QuestEvent$StateChanged', event => {
    if (event.newState == 'COMPLETED') {
      let id = event.node.id.path  // e.g. 'magic/first_spell'
    }
  })
```

- **Listen for a script_event reward** — server_scripts

```
ForgeEvents.onEvent(
  'net.phoenixvine.chronicles.event.PhoenixQuestScriptRewardEvent', event => {
    if (event.eventId === 'my_event') {
      event.serverPlayer.tell('Reward fired!')
    }
  })
```

---

## KubeJS - JSON bridges (config file only, no plugin/Java needed at all)

These two work even WITHOUT the plugin above and even without KubeJS itself -
any script/tool that can write a JSON file to config/phoenix_chronicles/ can
use them, since they're read straight off disk at (re)load, no scripting
runtime involved on this mod's side at all.

:::tip
Simpler than `registerScripted` above, but the tradeoff is completion is
always counter-based (fire an event N times) - use `registerScripted` instead
if you need a live per-player check (network storage, a stat threshold, etc.).
:::

:::spoiler Custom task type from script (config/phoenix_chronicles/kjs_task_types.json)
Every KJS-defined type is backed by ExternalTriggerTask under the hood (the
one built-in task class designed for exactly this - script decides when it's
done via QuestAPI.fireExternalEvent), but shows up in the quest editor's type
dropdown under its OWN name/icon/tooltip/fields, indistinguishable from a
real Java-registered type.

```js
const taskTypes = [{
  type_id: 'mypack:sun_eaten', label: 'Eat the Sun', icon: '§c☀',
  tooltip: 'Complete after eating a star.', default_trigger_id: 'mypack:sun_eaten',
  fields: [{id: 'required', label: 'Times', type: 'integer'}]
}]
const file = new java.io.File('config/phoenix_chronicles/kjs_task_types.json')
file.getParentFile().mkdirs()
file.text = JSON.stringify(taskTypes, null, 2)
```
:::

- **Export a flag value for enable_if: "kjs:..."** — config/phoenix_chronicles/kjs_flags.json

```js
const file = new java.io.File('config/phoenix_chronicles/kjs_flags.json')
file.getParentFile().mkdirs()
file.text = JSON.stringify({ expert_mode: Platform.isLoaded('somemod'), pack_tier: 2 }, null, 2)
```

Values can be booleans, numbers, or strings - written out as JSON, read back
as text. Nested objects flatten with dots (`{"pack": {"tier": 2}}` reads back
as key `pack.tier`). Then in quest SNBT, either an existence check or a
comparison against the same value:

```
enable_if: "kjs:expert_mode"
enable_if: "kjs:pack_tier>=2"
```

The file is cached for 10s (rewriting it doesn't take effect instantly) -
call `PhoenixQuestFlags.invalidateCaches()` after writing if you need it
picked up immediately, or just wait it out. `config:` files use the same
10s-class cache (15s TTL) for the same reason.

---

## In-game commands

| Command | Access | Description |
|---|---|---|
| `/chronicles status <id>` | Any player | Check your own quest state |
| `/chronicles emergency <id>` | Any player | Get emergency items while that quest is ACTIVE |
| `/chronicles complete <id> [player]` | Op | Force-complete a quest |
| `/chronicles unlock <id> [player]` | Op | Bypass prerequisites, set UNLOCKED |
| `/chronicles active <id> [player]` | Op | Force-start a quest (state ACTIVE) |
| `/chronicles reset <id> [player]` | Op | Full reset: state, task progress, claimed rewards |
| `/chronicles reload` | Op | Hot-reload quests from config/, sync to all online players |
| `/chronicles export` | Op | Snapshot every loaded quest to a timestamped export/ folder |
| `/chronicles import [subfolder]` | Op | Additive load from a subfolder (default "import") |
| `/chronicles import-ftb [subfolder]` | Op | Import an FTB Quests pack (default "ftb_import") |
| `/chronicles validate` | Op | Report load errors and common config issues |

[player] arguments default to yourself when omitted; specify one to target
another online player instead (e.g. from console).

---

## Custom quest backgrounds (Java, client-only)

Animated backdrops drawn behind a node's icon. Implement IQuestBackground,
register from FMLClientSetupEvent, then set on any quest with "background".

```
public class MyBackground implements IQuestBackground {
  public void render(GuiGraphics g, QuestNode node, int x, int y, int size, long animTick) {
    // draw whatever you want in the x,y,size,size box
  }
}
QuestBackgroundRegistry.register("mypack:my_bg", new MyBackground());
```

Built-in ids: "sun" (pulsing/rotating), "glitch" (shear + RGB split), both real
GLSL shaders (ChronicleShaders) if you want a shader-driven effect of your own -
register a ShaderInstance the same way (RegisterShadersEvent) and use
BackgroundRenderUtil.drawShaderQuad(...) to draw with it. In SNBT:

```
{ id: "my_quest", background: "mypack:my_bg", ... }
```

---

## Custom dependency line styles (Java, client-only)

Replaces how a single prereq connector line is drawn - your own texture,
animation, and math, not just a pick from the built-in visual-style enum.

```
public class MyLineStyle implements IDependencyLineStyle {
  public void render(GuiGraphics g, int px, int py, int cx, int cy, int color, long animTick) {
    // px,py -> cx,cy are already final on-screen pixel coordinates
  }
}
DependencyLineStyleRegistry.register(new ResourceLocation("mypack", "my_line"), new MyLineStyle());
```

Built-in ids (namespace phoenix_chronicles): "solid", "textured", "flowing_particles".
A prereq with no explicit line_style_id falls back to the legacy line_style enum,
so old quest files keep rendering exactly as before. Set one via the prereq's
"line_style_id" SNBT field:

```
prerequisites: [{ id: "other_quest", line_style_id: "mypack:my_line" }]
```

---

## Click-to-open-screen (Java, client-only)

Makes clicking a quest node open your own Screen directly instead of the normal
quest-detail popup. Register a factory, then set "external_screen" on a quest:

```
ExternalScreenRegistry.register(new ResourceLocation("mypack", "my_screen"), node -> new MyScreen());
{ id: "my_quest", external_screen: "mypack:my_screen", ... }
```

Pair it with a "screen_opened" task (completes when the screen is opened) and/or
an "open_screen" reward (opens the screen when the player claims rewards).
