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

/**
 * Client → Server packet: player clicked "Claim Rewards" on a quest.
 *
 * Wire format:
 * ResourceLocation questId
 * int choiceIndex (-1 = grant all rewards, ≥0 = chosen reward index)
 *
 * The server validates that:
 * - The quest exists
 * - All tasks are actually complete for this player
 * - Rewards have not already been claimed
 * before calling QuestProgressTracker.grantRewards / grantChosenReward.
 *
 * The client does NOT wait for a response — it closes the screen immediately
 * on click. If the server rejects the claim (e.g. tasks incomplete due to
 * desync) nothing bad happens; the player just won't receive rewards.
 */
public class C2SClaimQuestRewardPacket {

    private final ResourceLocation questId;
    private final int choiceIndex; // -1 = grant all

    // ── Client-side constructor ───────────────────────────────────────────────

    public C2SClaimQuestRewardPacket(ResourceLocation questId, int choiceIndex) {
        this.questId = questId;
        this.choiceIndex = choiceIndex;
    }

    // ── Codec ─────────────────────────────────────────────────────────────────

    public C2SClaimQuestRewardPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
        this.choiceIndex = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
        buf.writeInt(choiceIndex);
    }

    // ── Handle (server thread) ────────────────────────────────────────────────

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            // Rewards can only be claimed once the quest is actually COMPLETED - that IS the
            // required precondition, not a reason to reject the claim (the previous check here
            // had this backwards and unconditionally rejected every claim, since the client only
            // ever shows/sends this packet once the quest is already COMPLETED).
            //
            // Don't re-derive "is it really done" from live task checks here either: tasks like
            // item requirements check the player's CURRENT inventory, which is legitimately empty
            // after the items were consumed on completion - re-validating them at claim time would
            // incorrectly reject a perfectly legitimate claim. The already-recorded quest state is
            // the source of truth for "was this completed."
            QuestState currentState = QuestProgressTracker.getQuestState(player, node);
            if (currentState != QuestState.COMPLETED) return;

            // grantRewards/grantChosenReward already guard against double-claiming internally
            // (PlayerQuestData.hasClaimedRewards) and mark rewards claimed once granted.
            if (choiceIndex >= 0) {
                QuestProgressTracker.grantChosenReward(player, node, choiceIndex);
            } else {
                QuestProgressTracker.grantRewards(player, node);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
