package net.phoenixvine.chronicles.mixin;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.QuestAPI;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiblockWorldSavedData.class, remap = false)
public abstract class MultiblockFormedMixin {

    @Inject(method = "addMapping", at = @At("HEAD"), remap = false)
    private void phoenixChronicles$onMultiblockFormed(MultiblockState state, CallbackInfo ci) {
        if (!(state.getWorld() instanceof ServerLevel serverLevel)) return;

        ResourceLocation machineId = ForgeRegistries.BLOCKS.getKey(
                serverLevel.getBlockState(state.controllerPos).getBlock());
        if (machineId == null) return;

        Player nearest = serverLevel.getNearestPlayer(
                state.controllerPos.getX() + 0.5, state.controllerPos.getY() + 0.5, state.controllerPos.getZ() + 0.5,
                10.0, false);
        if (!(nearest instanceof ServerPlayer serverPlayer)) return;

        CompoundTag data = new CompoundTag();
        data.putString("machine_id", machineId.toString());
        QuestAPI.fireExternalEvent(serverPlayer, "gtceu_multiblock:" + machineId, data);
    }
}

