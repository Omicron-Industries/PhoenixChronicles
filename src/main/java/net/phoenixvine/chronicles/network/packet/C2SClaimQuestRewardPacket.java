package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2SClaimQuestRewardPacket {

    private final ResourceLocation questId;
    private final int choiceIndex; 

    public C2SClaimQuestRewardPacket(ResourceLocation questId, int choiceIndex) {
        this.questId = questId;
        this.choiceIndex = choiceIndex;
    }

    public C2SClaimQuestRewardPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
        this.choiceIndex = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
        buf.writeInt(choiceIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            QuestState currentState = QuestProgressTracker.getQuestState(player, node);
            if (currentState != QuestState.COMPLETED) return;

            if (choiceIndex >= 0) {
                QuestProgressTracker.grantChosenReward(player, node, choiceIndex);
            } else {
                QuestProgressTracker.grantRewards(player, node);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

