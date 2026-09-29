package net.phoenixvine.chronicles.integration.jei;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.List;

@JeiPlugin
public class ChroniclesJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;
    private static List<QuestNode> registeredQuests = List.of();

    @Override
    public ResourceLocation getPluginUid() {
        return QuestJeiCategory.ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new QuestJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registeredQuests = currentQuests();
        registration.addRecipes(QuestJeiCategory.TYPE, registeredQuests);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static List<QuestNode> currentQuests() {
        return QuestTreeRegistry.getAllQuests().values().stream()
                .filter(n -> n.getVisibility() != QuestNode.Visibility.HIDDEN)
                .toList();
    }

    public static void refreshQuestRecipes() {
        if (runtime == null) return;
        IRecipeManager recipeManager = runtime.getRecipeManager();
        if (!registeredQuests.isEmpty()) recipeManager.hideRecipes(QuestJeiCategory.TYPE, registeredQuests);
        registeredQuests = currentQuests();
        recipeManager.addRecipes(QuestJeiCategory.TYPE, registeredQuests);
    }
}
