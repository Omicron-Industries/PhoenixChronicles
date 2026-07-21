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
        
        filter.allow(QuestAPI.class);
        filter.allow(QuestState.class);
        filter.allow(QuestNode.class);

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

        event.add("QuestAPI", QuestAPI.class);
        event.add("PhoenixTaskRegistry", PhoenixTaskRegistry.class);
        event.add("PhoenixQuestFlags", PhoenixQuestFlags.class);
    }
}

