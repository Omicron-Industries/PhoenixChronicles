package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class C2SBulkQuestActionPacket {

    public enum Action {
        FORCE_COMPLETE,
        RESET
    }

    private final List<ResourceLocation> questIds;
    private final Action action;

    public C2SBulkQuestActionPacket(List<ResourceLocation> questIds, Action action) {
        this.questIds = questIds;
        this.action = action;
    }

    public C2SBulkQuestActionPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.questIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) questIds.add(buf.readResourceLocation());
        this.action = buf.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(questIds.size());
        for (ResourceLocation id : questIds) buf.writeResourceLocation(id);
        buf.writeEnum(action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2) || questIds.isEmpty()) return;

            player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                for (ResourceLocation questId : questIds) {
                    QuestNode node = QuestTreeRegistry.getQuest(questId);
                    if (node == null) continue;

                    if (action == Action.FORCE_COMPLETE) {
                        data.setQuestState(questId, QuestState.COMPLETED);
                        QuestProgressTracker.updateActiveTracking(player.getUUID(), node, QuestState.COMPLETED);
                    } else {
                        List<ResourceLocation> taskIds = new ArrayList<>();
                        for (QuestTask t : node.getTasks()) taskIds.add(t.getTaskId());
                        data.resetQuestProgress(questId, taskIds);
                        if (QuestProgressTracker.prereqsSatisfied(node, data, player.getServer())) {
                            QuestProgressTracker.changeQuestState(player, node, QuestState.UNLOCKED);
                        }
                    }
                }

                ChronicleNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new S2CSyncPlayerProgressPacket(data));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
