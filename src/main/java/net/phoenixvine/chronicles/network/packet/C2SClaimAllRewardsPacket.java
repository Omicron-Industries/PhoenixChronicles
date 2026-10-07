package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import java.util.ArrayList;
import java.util.List;
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

            // Granting a reward can complete other quests or fail outright; snapshot the list first and keep going
            // past a failure so one bad quest can't leave every quest after it unclaimed (and the client unsynced).
            List<QuestNode> candidates = new ArrayList<>(QuestTreeRegistry.getAllQuests().values());
            for (QuestNode node : candidates) {
                try {
                    if (node.isFlagDisabled(player.getServer())) continue;
                    if (data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) continue;
                    if (data.hasClaimedRewards(node.getId())) continue;
                    if (node.isRewardChoice()) continue;
                    if (node.getEffectiveRewards(player.getServer(), player).isEmpty()) continue;

                    QuestProgressTracker.grantRewards(player, node);
                } catch (Exception e) {
                    PhoenixChronicles.LOGGER.error("[ClaimAll] Failed to claim rewards for quest {}", node.getId(), e);
                }
            }

            QuestProgressTracker.sendProgressSync(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
