package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.event.QuestEvent;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2STogglePinPacket {

    private final ResourceLocation questId;

    public C2STogglePinPacket(ResourceLocation questId) {
        this.questId = questId;
    }

    public C2STogglePinPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                data.togglePin(questId);
                boolean nowPinned = data.isPinned(questId);
                QuestNode node = QuestTreeRegistry.getQuest(questId);
                if (node != null) {
                    MinecraftForge.EVENT_BUS.post(new QuestEvent.PinChanged(player, node, nowPinned));
                }
                QuestProgressTracker.sendProgressSync(player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
