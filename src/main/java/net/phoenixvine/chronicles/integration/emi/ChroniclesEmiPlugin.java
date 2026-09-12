package net.phoenixvine.chronicles.integration.emi;

import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.wiki.client.suite.SuiteHudBar;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import org.jetbrains.annotations.NotNull;

@EmiEntrypoint
public class ChroniclesEmiPlugin implements EmiPlugin {

    public static EmiRegistry currentRegistry;

    @Override
    public void register(@NotNull EmiRegistry registry) {
        currentRegistry = registry;

        registry.addCategory(QuestEmiCategory.CATEGORY);

        registry.addWorkstation(QuestEmiCategory.CATEGORY,
                dev.emi.emi.api.stack.EmiStack.of(net.minecraft.world.item.Items.BOOK));

        loadQuestsIntoEmi(registry);

        registry.addGenericExclusionArea((screen, consumer) -> {
            if (SuiteHudBar.screenWantsBar(screen)) {
                consumer.accept(new Bounds(0, 0, SuiteHudBar.barWidth(), SuiteHudBar.barHeight()));
            }
        });
    }

    public static void loadQuestsIntoEmi(@NotNull EmiRegistry registry) {
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {

            if (node.isFlagDisabled(null)) {
                continue;
            }

            if (node.getVisibility() != QuestNode.Visibility.HIDDEN) {
                registry.addRecipe(new QuestEmiRecipe(node));
            }
        }
    }
}
