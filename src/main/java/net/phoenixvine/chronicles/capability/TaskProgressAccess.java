package net.phoenixvine.chronicles.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.S2CSyncPooledProgressPacket;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.TeamKeyResolver;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class TaskProgressAccess {

    private TaskProgressAccess() {}

    public static void with(Player player, ResourceLocation taskId, Consumer<CompoundTag> action) {
        QuestNode owner = QuestTreeRegistry.getTaskOwner(taskId);
        boolean pooled = owner != null && owner.isPooledProgress();

        CompoundTag tag = resolve(player, taskId, owner, pooled);
        if (tag == null) return;

        boolean wasCompleted = tag.getBoolean("completed");
        action.accept(tag);
        boolean nowCompleted = tag.getBoolean("completed");

        if (pooled && player instanceof ServerPlayer sp) {
            broadcastPooledProgress(sp, taskId, tag);
        } else if (!pooled && !wasCompleted && nowCompleted && player instanceof ServerPlayer sp) {

            net.phoenixvine.chronicles.tracker.QuestProgressTracker.sendProgressSync(sp);
        }
    }

    public static CompoundTag getOrEmpty(Player player, ResourceLocation taskId) {
        QuestNode owner = QuestTreeRegistry.getTaskOwner(taskId);
        boolean pooled = owner != null && owner.isPooledProgress();
        CompoundTag tag = resolve(player, taskId, owner, pooled);
        return tag != null ? tag : new CompoundTag();
    }

    private static CompoundTag resolve(Player player, ResourceLocation taskId, QuestNode owner, boolean pooled) {
        if (pooled) {
            if (player instanceof ServerPlayer sp) {
                Optional<String> teamKey = TeamKeyResolver.resolve(sp);
                if (teamKey.isPresent()) {
                    return PooledTaskProgress.get(sp.serverLevel()).getOrCreate(teamKey.get(), taskId);
                }
            } else if (player.level().isClientSide()) {
                return net.phoenixvine.chronicles.client.ClientPooledProgress.get(taskId);
            }
        }
        return player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .map(data -> data.getOrCreateTaskProgress(taskId))
                .orElse(null);
    }

    private static void broadcastPooledProgress(ServerPlayer source, ResourceLocation taskId, CompoundTag tag) {
        Optional<String> teamKey = TeamKeyResolver.resolve(source);
        if (teamKey.isEmpty() || source.getServer() == null) return;

        CompoundTag copy = tag.copy();
        Map<ResourceLocation, CompoundTag> payload = Map.of(taskId, copy);
        for (ServerPlayer member : source.getServer().getPlayerList().getPlayers()) {
            if (TeamKeyResolver.resolve(member).equals(teamKey)) {
                ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> member),
                        new S2CSyncPooledProgressPacket(payload));
            }
        }
    }

    public static void clear(Player player, ResourceLocation taskId) {
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .ifPresent(data -> data.clearTaskProgress(taskId));
        QuestNode owner = QuestTreeRegistry.getTaskOwner(taskId);
        if (owner != null && owner.isPooledProgress() && player instanceof ServerPlayer sp) {
            PooledTaskProgress.get(sp.serverLevel()).clearTaskProgress(taskId);
            broadcastPooledProgress(sp, taskId, new CompoundTag());
        }
    }
}
