package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the client its own resolved team key (see TeamKeyResolver -- "guild:<id>" / "ftbteam:<id>" /
 * "sb:<name>", or empty for no team) so client-side flag evaluation (quest description {@code :::if}
 * text, and anywhere else PhoenixQuestFlags.evaluate runs without server access) can look up
 * team-scoped flags without needing the server-only team APIs (GuildManager/FTBTeamsAPI's manager are
 * both backed by server-side SavedData). Sent alongside the existing per-player sync on login, mirroring
 * how pooled task progress already resolves the team once at that same point.
 */
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
