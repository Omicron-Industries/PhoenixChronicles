package net.phoenixvine.chronicles.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.item.FluidFilterTokenItem;
import net.phoenixvine.chronicles.item.ItemFilterTokenItem;

import java.util.function.Supplier;

public class C2SSetFilterTokenPacket {

    private final boolean clear;
    private final CompoundTag filterTag;

    public C2SSetFilterTokenPacket(CompoundTag filterTag) {
        this.clear = filterTag == null;
        this.filterTag = filterTag;
    }

    public C2SSetFilterTokenPacket(FriendlyByteBuf buf) {
        this.clear = buf.readBoolean();
        this.filterTag = this.clear ? null : buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(clear);
        if (!clear) buf.writeNbt(filterTag);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof ItemFilterTokenItem) &&
                    !(stack.getItem() instanceof FluidFilterTokenItem)) {
                stack = player.getOffhandItem();
            }
            String key = stack.getItem() instanceof ItemFilterTokenItem ? ItemFilterTokenItem.TAG_FILTER :
                    stack.getItem() instanceof FluidFilterTokenItem ? FluidFilterTokenItem.TAG_FILTER : null;
            if (key == null) return;

            if (clear) {
                if (stack.hasTag()) stack.getTag().remove(key);
            } else {
                stack.getOrCreateTag().put(key, filterTag.copy());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
