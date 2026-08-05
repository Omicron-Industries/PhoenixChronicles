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

public class C2SSetQuestStatePacket {

    private final ResourceLocation questId;
    private final boolean activate;

    public C2SSetQuestStatePacket(ResourceLocation questId, boolean activate) {
        this.questId = questId;
        this.activate = activate;
    }

    public C2SSetQuestStatePacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
        this.activate = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
        buf.writeBoolean(activate);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || questId == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            QuestState current = QuestProgressTracker.getQuestState(player, node);
            if (activate && current == QuestState.UNLOCKED) {
                QuestProgressTracker.changeQuestState(player, node, QuestState.ACTIVE);
                QuestProgressTracker.sendProgressSync(player);
            } else if (!activate && current == QuestState.ACTIVE) {
                QuestProgressTracker.changeQuestState(player, node, QuestState.UNLOCKED);
                QuestProgressTracker.sendProgressSync(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
