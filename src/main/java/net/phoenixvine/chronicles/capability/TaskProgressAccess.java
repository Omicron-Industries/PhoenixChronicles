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

/**
 * Resolves which store a task's progress {@link CompoundTag} actually lives in: the owning
 * quest's per-player {@link PlayerQuestData}, or — if the quest has {@code pooledProgress} set
 * and the player is currently on a resolvable team — a tag shared by the whole team in
 * {@link PooledTaskProgress}. Every task subclass should read/write progress through this
 * class instead of touching either store directly, so pooling stays transparent to task logic
 * and every existing accumulator (kill counts, block breaks, stat thresholds, ...) gets pooling
 * for free.
 */
public final class TaskProgressAccess {

    private TaskProgressAccess() {}

    /** Runs {@code action} against the correct progress tag for this player+task, if a capability exists. */
    public static void with(Player player, ResourceLocation taskId, Consumer<CompoundTag> action) {
        CompoundTag tag = resolve(player, taskId);
        if (tag != null) action.accept(tag);
    }

    /** Returns the progress tag for this player+task, or a fresh detached tag if no capability is present. */
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

    /** Clears progress for this task everywhere it might be stored (per-player and pooled). */
    public static void clear(Player player, ResourceLocation taskId) {
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .ifPresent(data -> data.clearTaskProgress(taskId));
        QuestNode owner = QuestTreeRegistry.getTaskOwner(taskId);
        if (owner != null && owner.isPooledProgress() && player instanceof ServerPlayer sp) {
            PooledTaskProgress.get(sp.serverLevel()).clearTaskProgress(taskId);
        }
    }
}
