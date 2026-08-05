package net.phoenixvine.chronicles.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class S2COpenExternalScreenPacket {

    @Nullable
    private final ResourceLocation screenId;

    public S2COpenExternalScreenPacket(@Nullable ResourceLocation screenId) {
        this.screenId = screenId;
    }

    public S2COpenExternalScreenPacket(FriendlyByteBuf buf) {
        this.screenId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(screenId != null ? screenId : new ResourceLocation("minecraft", "empty"));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (screenId == null) return;
            net.minecraft.client.gui.screens.Screen screen = net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry
                    .open(screenId, null);
            if (screen != null) Minecraft.getInstance().setScreen(screen);
        }));
        ctx.get().setPacketHandled(true);
    }
}
