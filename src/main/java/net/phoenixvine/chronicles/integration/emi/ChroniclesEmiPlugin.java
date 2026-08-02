package net.phoenixvine.chronicles.integration.emi;

import net.phoenixvine.chronicles.client.SuiteHudBarButton;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;

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

        registry.addGenericExclusionArea((screen, consumer) -> {
            if (SuiteHudBarButton.screenWantsBarPublic(screen)) {
                consumer.accept(new Bounds(0, 0, SuiteHudBarButton.barWidth(), SuiteHudBarButton.barHeight()));
            }
        });
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
