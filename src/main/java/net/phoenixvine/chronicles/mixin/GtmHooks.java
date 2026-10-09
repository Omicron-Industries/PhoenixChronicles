package net.phoenixvine.chronicles.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.QuestAPI;

import java.util.UUID;

public final class GtmHooks {

    private static final double NEAREST_PLAYER_RANGE = 10.0;

    private GtmHooks() {}

    public static void onRecipeFinished(Level level, BlockPos machinePos, UUID owner, ResourceLocation recipeType,
                                        ResourceLocation recipeId) {
        if (!(level instanceof ServerLevel serverLevel) || recipeType == null) return;

        ServerPlayer player = null;
        if (owner != null) player = serverLevel.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            Player nearest = serverLevel.getNearestPlayer(machinePos.getX() + 0.5, machinePos.getY() + 0.5,
                    machinePos.getZ() + 0.5, NEAREST_PLAYER_RANGE, false);
            if (nearest instanceof ServerPlayer sp) player = sp;
        }
        if (player == null) return;

        QuestAPI.fireRecipeCompleted(player, recipeType, recipeId, 1);
    }

    public static void onMultiblockFormed(Level level, BlockPos controllerPos) {
        if (!(level instanceof ServerLevel serverLevel) || controllerPos == null) return;

        ResourceLocation machineId = ForgeRegistries.BLOCKS.getKey(
                serverLevel.getBlockState(controllerPos).getBlock());
        if (machineId == null) return;

        Player nearest = serverLevel.getNearestPlayer(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5,
                controllerPos.getZ() + 0.5, NEAREST_PLAYER_RANGE, false);
        if (!(nearest instanceof ServerPlayer serverPlayer)) return;

        CompoundTag data = new CompoundTag();
        data.putString("machine_id", machineId.toString());
        QuestAPI.fireExternalEvent(serverPlayer, "gtceu_multiblock:" + machineId, data);
    }
}
