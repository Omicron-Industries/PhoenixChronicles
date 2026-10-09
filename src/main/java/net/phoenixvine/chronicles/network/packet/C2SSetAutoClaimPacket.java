package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.model.AutoClaimCategory;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2SSetAutoClaimPacket {

    private final boolean enabled;
    private final int categoryMask;

    public C2SSetAutoClaimPacket(boolean enabled, int categoryMask) {
        this.enabled = enabled;
        this.categoryMask = categoryMask;
    }

    public C2SSetAutoClaimPacket(FriendlyByteBuf buf) {
        this.enabled = buf.readBoolean();
        this.categoryMask = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(categoryMask);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PlayerQuestData data = player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
            if (data == null) return;

            data.setAutoClaimEnabled(enabled);
            data.setAutoClaimCategories(AutoClaimCategory.fromMask(categoryMask));
            if (enabled) QuestProgressTracker.autoClaimSweep(player);
            QuestProgressTracker.sendProgressSync(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
