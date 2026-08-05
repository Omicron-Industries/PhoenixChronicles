package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2SClaimAllRewardsPacket {

    public C2SClaimAllRewardsPacket() {}

    public C2SClaimAllRewardsPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PlayerQuestData data = player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
            if (data == null) return;

            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                if (node.isFlagDisabled()) continue;
                if (data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) continue;
                if (data.hasClaimedRewards(node.getId())) continue;
                if (node.isRewardChoice()) continue;
                if (node.getEffectiveRewards(player.getServer()).isEmpty()) continue;

                QuestProgressTracker.grantRewards(player, node);
            }

            QuestProgressTracker.sendProgressSync(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
