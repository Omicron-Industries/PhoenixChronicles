package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: "your local config/phoenix_chronicles files just changed on disk - reload
 * your own copy of the quest tree from them instead of waiting for a full network sync."
 *
 * Only valid when the client shares the same game directory as the server (integrated/singleplayer
 * server), which is exactly the case FtbQuestsImporter's import command runs in during normal use.
 * Encoding a full S2CSyncQuestsPacket for an entire freshly-imported pack (900+ quests, each with
 * tasks/rewards/prerequisites) synchronously on the main thread was measured to be slow enough to
 * cause keepalive timeouts on a big import - re-reading the same files the client already has
 * locally is far cheaper than re-serializing and transmitting them all over the network.
 */
public class S2CReloadQuestsFromDiskPacket {

    public S2CReloadQuestsFromDiskPacket() {}

    public S2CReloadQuestsFromDiskPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.phoenixvine.chronicles.codec.QuestFileLoader.reloadAllQuestsFromDisk()));
        ctx.get().setPacketHandled(true);
    }
}
