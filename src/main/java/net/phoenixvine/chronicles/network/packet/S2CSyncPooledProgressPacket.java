package net.phoenixvine.chronicles.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.client.util.ClientPooledProgress;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class S2CSyncPooledProgressPacket {

    private final Map<ResourceLocation, CompoundTag> progress;

    public S2CSyncPooledProgressPacket(Map<ResourceLocation, CompoundTag> progress) {
        this.progress = progress;
    }

    public S2CSyncPooledProgressPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.progress = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            progress.put(buf.readResourceLocation(), buf.readNbt());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(progress.size());
        for (Map.Entry<ResourceLocation, CompoundTag> entry : progress.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeNbt(entry.getValue());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            for (Map.Entry<ResourceLocation, CompoundTag> entry : progress.entrySet()) {
                ClientPooledProgress.put(entry.getKey(), entry.getValue());
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
