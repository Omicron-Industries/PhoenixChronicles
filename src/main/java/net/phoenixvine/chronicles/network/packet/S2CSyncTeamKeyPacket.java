package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncTeamKeyPacket {

    private final String teamKey;

    public S2CSyncTeamKeyPacket(String teamKey) {
        this.teamKey = teamKey == null ? "" : teamKey;
    }

    public S2CSyncTeamKeyPacket(FriendlyByteBuf buf) {
        this.teamKey = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(teamKey);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.phoenixvine.chronicles.client.util.ClientTeamCache.set(teamKey)));
        ctx.get().setPacketHandled(true);
    }
}
