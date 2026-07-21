package net.phoenixvine.chronicles.integration.emi;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;

public class QuestEmiCategory {

    public static final ResourceLocation ID = new ResourceLocation("phoenixcore", "quests");

    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(ID,
            EmiStack.of(Items.BOOK));
}

