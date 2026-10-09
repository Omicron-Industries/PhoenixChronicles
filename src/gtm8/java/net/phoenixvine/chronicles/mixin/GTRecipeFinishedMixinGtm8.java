package net.phoenixvine.chronicles.mixin;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeLogic.class, remap = false)
public abstract class GTRecipeFinishedMixinGtm8 {

    @Shadow
    protected GTRecipe lastRecipe;

    @Inject(method = "onRecipeFinish", at = @At("HEAD"), remap = false)
    private void phoenixChronicles$onRecipeFinished(CallbackInfo ci) {
        GTRecipe recipe = this.lastRecipe;
        if (recipe == null || recipe.recipeType == null) return;

        MetaMachine meta = ((RecipeLogic) (Object) this).getMachine();
        GtmHooks.onRecipeFinished(meta.getLevel(), meta.getBlockPos(), meta.getOwnerUUID(),
                recipe.recipeType.registryName, recipe.id);
    }
}
