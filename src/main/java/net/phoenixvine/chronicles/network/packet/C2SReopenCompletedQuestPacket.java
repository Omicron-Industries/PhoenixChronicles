package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import java.util.function.Supplier;

/**
 * Sent by the quest editor when a required task was added to a quest: every online player who had already completed
 * it gets it back as unlocked, so the new task has to be done. Claimed rewards stay claimed, and quests that depended
 * on the completion lock again until it is redone.
 */
public class C2SReopenCompletedQuestPacket {

    private final ResourceLocation questId;

    public C2SReopenCompletedQuestPacket(ResourceLocation questId) {
        this.questId = questId;
    }

    public C2SReopenCompletedQuestPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null || sender.getServer() == null) return;

            for (ServerPlayer player : sender.getServer().getPlayerList().getPlayers()) {
                PlayerQuestData data = player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
                if (data == null || data.getQuestState(questId, QuestState.LOCKED) != QuestState.COMPLETED) continue;
                QuestProgressTracker.changeQuestState(player, node, QuestState.UNLOCKED);
                QuestProgressTracker.relockUnsatisfied(player);
                QuestProgressTracker.sendProgressSync(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
