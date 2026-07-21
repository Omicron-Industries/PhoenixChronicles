package net.phoenixvine.chronicles.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.TeamKeyResolver;

import java.util.Optional;
import java.util.function.Consumer;

public final class TaskProgressAccess {

    private TaskProgressAccess() {}

    public static void with(Player player, ResourceLocation taskId, Consumer<CompoundTag> action) {
        CompoundTag tag = resolve(player, taskId);
        if (tag != null) action.accept(tag);
    }

    public static CompoundTag getOrEmpty(Player player, ResourceLocation taskId) {
        CompoundTag tag = resolve(player, taskId);
        return tag != null ? tag : new CompoundTag();
    }

    private static CompoundTag resolve(Player player, ResourceLocation taskId) {
        QuestNode owner = QuestTreeRegistry.getTaskOwner(taskId);
        if (owner != null && owner.isPooledProgress() && player instanceof ServerPlayer sp) {
            Optional<String> teamKey = TeamKeyResolver.resolve(sp);
            if (teamKey.isPresent()) {
                return PooledTaskProgress.get(sp.serverLevel()).getOrCreate(teamKey.get(), taskId);
            }
        }
        return player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .map(data -> data.getOrCreateTaskProgress(taskId))
                .orElse(null);
    }

    public static void clear(Player player, ResourceLocation taskId) {
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .ifPresent(data -> data.clearTaskProgress(taskId));
        QuestNode owner = QuestTreeRegistry.getTaskOwner(taskId);
        if (owner != null && owner.isPooledProgress() && player instanceof ServerPlayer sp) {
            PooledTaskProgress.get(sp.serverLevel()).clearTaskProgress(taskId);
        }
    }
}

