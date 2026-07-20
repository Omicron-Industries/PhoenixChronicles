package net.phoenixvine.chronicles.integration.kubejs;

import net.phoenixvine.chronicles.QuestAPI;
import net.phoenixvine.chronicles.event.PhoenixQuestScriptRewardEvent;
import net.phoenixvine.chronicles.event.QuestEvent;
import net.phoenixvine.chronicles.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.tasks.ScriptTask;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

public class PhoenixChroniclesKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        // Player-facing read/write API
        filter.allow(QuestAPI.class);
        filter.allow(QuestState.class);
        filter.allow(QuestNode.class);

        // Events - QuestEvent's nested StateChanged/RewardClaimed/ExternalEvent/PlayerTick/
        // PinChanged/TreeReloaded classes are covered by allowing the outer QuestEvent class;
        // KubeJS's ForgeEvents.onEvent() already works against any Forge event by fully-qualified
        // name without needing an allowlist entry to LISTEN, but scripts inspecting instances of
        // these types (event.getNode() etc. returning a QuestNode, for example) still need the
        // classes themselves allowed to call methods on what comes back.
        filter.allow(QuestEvent.class);
        filter.allow(PhoenixQuestScriptRewardEvent.class);

        filter.allow(QuestTask.class);
        filter.allow(ScriptTask.class);
        filter.allow(PhoenixTaskRegistry.class);
        filter.allow(PhoenixTaskRegistry.Builder.class);
        filter.allow(PhoenixTaskRegistry.FieldDef.class);
        filter.allow(PhoenixQuestFlags.class);
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        // Global bindings - only meaningful for server_scripts/startup_scripts (QuestAPI mutates
        // authoritative state and PhoenixQuestFlags evaluates server-side conditions), but adding
        // them for every script type is harmless: an unused global binding costs nothing, and a
        // client script calling into QuestAPI still gets QuestAPI's own client-side guards/
        // warnings rather than a binding-not-found error that looks like a bug in KubeJS itself.
        event.add("QuestAPI", QuestAPI.class);
        event.add("PhoenixTaskRegistry", PhoenixTaskRegistry.class);
        event.add("PhoenixQuestFlags", PhoenixQuestFlags.class);
    }
}
