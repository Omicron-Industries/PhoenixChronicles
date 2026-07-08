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

/**
 * Client → Server: player pinned/unpinned a quest on the HUD tracker.
 *
 * Pins used to only be toggled on the client's own (unsaved) capability instance, which is a
 * separate object from the ServerPlayer's - the client capability is what the player SEES, but
 * the server capability is what actually gets written to the player's save data. Since pins were
 * never told to the server, they lived only as long as that client-side object did: gone on
 * disconnect/relogin, and even mid-session any other server-driven progress sync would silently
 * wipe them back out (S2CSyncPlayerProgressPacket overwrites the client's copy wholesale with the
 * server's, which never had the pin either).
 *
 * Wire format: ResourceLocation questId
 */
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
