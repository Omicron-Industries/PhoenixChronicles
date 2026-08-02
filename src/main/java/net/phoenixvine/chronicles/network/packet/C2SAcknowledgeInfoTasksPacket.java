package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.InfoTask;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2SAcknowledgeInfoTasksPacket {

    private final ResourceLocation questId;

    public C2SAcknowledgeInfoTasksPacket(ResourceLocation questId) {
        this.questId = questId;
    }

    public C2SAcknowledgeInfoTasksPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            if (QuestProgressTracker.getQuestState(player, node) == QuestState.COMPLETED) return;

            player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                boolean anyChanged = false;
                for (var task : node.getTasks()) {
                    if (task instanceof InfoTask) {
                        boolean wasDone = task.isCompletedFor(player);
                        if (!wasDone) {
                            InfoTask.acknowledge(player, task.getTaskId());
                            anyChanged = true;
                        }
                    }
                }
                if (anyChanged) {
                    QuestProgressTracker.checkAndTryComplete(player, node);

                    QuestProgressTracker.sendProgressSync(player);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
