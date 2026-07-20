package net.phoenixvine.chronicles.integration.emi;

import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@EmiEntrypoint
public class ChroniclesEmiPlugin implements EmiPlugin {

    // Save a static reference to the registry so we can dynamically add/remove later
    public static EmiRegistry currentRegistry;

    @Override
    public void register(EmiRegistry registry) {
        currentRegistry = registry;

        // Register the category
        registry.addCategory(QuestEmiCategory.CATEGORY);

        // FIX: Associate an item as the "workstation" provider for this entire category sheet
        // If your mod has a specific quest book item, change net.minecraft.world.item.Items.BOOK to that!
        registry.addWorkstation(QuestEmiCategory.CATEGORY,
                dev.emi.emi.api.stack.EmiStack.of(net.minecraft.world.item.Items.BOOK));

        // Populate the quests
        loadQuestsIntoEmi(registry);
    }

    public static void loadQuestsIntoEmi(EmiRegistry registry) {
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            // Filter out quests completely turned off by features/flags
            if (node.isFlagDisabled()) {
                continue;
            }

            // Map and load based on visibility configurations
            if (node.getVisibility() != QuestNode.Visibility.HIDDEN) {
                registry.addRecipe(new QuestEmiRecipe(node));
            }
        }
    }
}
