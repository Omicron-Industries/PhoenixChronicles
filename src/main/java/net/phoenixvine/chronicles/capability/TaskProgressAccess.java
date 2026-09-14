package net.phoenixvine.chronicles.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.chronicles.client.util.ClientPooledProgress;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;
import net.phoenixvine.chronicles.common.tracker.TeamKeyResolver;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.S2CSyncPooledProgressPacket;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
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

        if (player instanceof ServerPlayer sp) {
            if (pooled) {
                broadcastPooledProgress(sp, taskId, tag);
            } else if (!wasCompleted && tag.getBoolean("completed")) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        }
    }

    public static CompoundTag getOrEmpty(Player player, ResourceLocation taskId) {
        QuestNode owner = QuestTreeRegistry.getTaskOwner(taskId);
        assert owner != null;
        CompoundTag tag = resolve(player, taskId, owner, owner.isPooledProgress());
        return tag != null ? tag : new CompoundTag();
    }

    private static @Nullable CompoundTag resolve(Player player, ResourceLocation taskId, QuestNode owner,
                                                 boolean pooled) {
        if (pooled) {
            if (player instanceof ServerPlayer sp) {
                return TeamKeyResolver.resolve(sp)
                        .map(key -> PooledTaskProgress.get(sp.serverLevel()).getOrCreate(key, taskId))
                        .orElse(null);
            }
            if (player.level().isClientSide()) {
                return ClientPooledProgress.get(taskId);
            }
        }
        return player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .map(data -> data.getOrCreateTaskProgress(taskId))
                .orElse(null);
    }

    private static void broadcastPooledProgress(ServerPlayer source, ResourceLocation taskId, CompoundTag tag) {
        if (source.getServer() == null) return;

        TeamKeyResolver.resolve(source).ifPresent(teamKey -> {
            var packet = new S2CSyncPooledProgressPacket(Map.of(taskId, tag.copy()));
            for (ServerPlayer member : source.getServer().getPlayerList().getPlayers()) {
                if (TeamKeyResolver.resolve(member).filter(teamKey::equals).isPresent()) {
                    ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> member), packet);
                }
            }
        });
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
