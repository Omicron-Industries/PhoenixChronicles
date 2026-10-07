package net.phoenixvine.chronicles.mixin;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.QuestAPI;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Tells quests every time a GregTech machine finishes a recipe run, credited to the machine's owner (or the nearest
 * player when it has none), so recipe tasks can count them.
 */
@Mixin(value = RecipeLogic.class, remap = false)
public abstract class GTRecipeFinishedMixin {

    @Shadow
    protected GTRecipe lastRecipe;

    @Shadow
    @Final
    public IRecipeLogicMachine machine;

    @Inject(method = "onRecipeFinish", at = @At("HEAD"), remap = false)
    private void phoenixChronicles$onRecipeFinished(CallbackInfo ci) {
        GTRecipe recipe = this.lastRecipe;
        if (recipe == null || recipe.recipeType == null) return;

        MetaMachine meta = machine.self();
        if (!(meta.getLevel() instanceof ServerLevel level)) return;

        ServerPlayer player = null;
        UUID owner = meta.getOwnerUUID();
        if (owner != null) player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            Player nearest = level.getNearestPlayer(meta.getPos().getX() + 0.5, meta.getPos().getY() + 0.5,
                    meta.getPos().getZ() + 0.5, 10.0, false);
            if (nearest instanceof ServerPlayer sp) player = sp;
        }
        if (player == null) return;

        QuestAPI.fireRecipeCompleted(player, recipe.recipeType.registryName, recipe.id, 1);
    }
}
