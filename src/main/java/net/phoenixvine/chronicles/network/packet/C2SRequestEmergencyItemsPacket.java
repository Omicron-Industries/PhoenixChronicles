package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2SRequestEmergencyItemsPacket {

    private final ResourceLocation questId;

    public C2SRequestEmergencyItemsPacket(ResourceLocation questId) {
        this.questId = questId;
    }

    public C2SRequestEmergencyItemsPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || questId == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            QuestProgressTracker.EmergencyResult result = QuestProgressTracker.claimEmergencyItems(player, node);
            player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
        });
        ctx.get().setPacketHandled(true);
    }
}
