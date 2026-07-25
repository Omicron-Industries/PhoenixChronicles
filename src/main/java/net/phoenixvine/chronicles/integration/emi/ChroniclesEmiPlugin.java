package net.phoenixvine.chronicles.integration.emi;

import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@EmiEntrypoint
public class ChroniclesEmiPlugin implements EmiPlugin {

    public static EmiRegistry currentRegistry;

    @Override
    public void register(EmiRegistry registry) {
        currentRegistry = registry;

        registry.addCategory(QuestEmiCategory.CATEGORY);

        registry.addWorkstation(QuestEmiCategory.CATEGORY,
                dev.emi.emi.api.stack.EmiStack.of(net.minecraft.world.item.Items.BOOK));

        loadQuestsIntoEmi(registry);
    }

    public static void loadQuestsIntoEmi(EmiRegistry registry) {
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {

            if (node.isFlagDisabled()) {
                continue;
            }

            if (node.getVisibility() != QuestNode.Visibility.HIDDEN) {
                registry.addRecipe(new QuestEmiRecipe(node));
            }
        }
    }
}
