package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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

