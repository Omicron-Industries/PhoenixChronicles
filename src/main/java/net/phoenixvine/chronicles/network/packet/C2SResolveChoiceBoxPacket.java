package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2SResolveChoiceBoxPacket {

    private final ResourceLocation questId;
    private final int boxIndex;
    private final int optionIndex;

    public C2SResolveChoiceBoxPacket(ResourceLocation questId, int boxIndex, int optionIndex) {
        this.questId = questId;
        this.boxIndex = boxIndex;
        this.optionIndex = optionIndex;
    }

    public C2SResolveChoiceBoxPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
        this.boxIndex = buf.readInt();
        this.optionIndex = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
        buf.writeInt(boxIndex);
        buf.writeInt(optionIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || questId == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            QuestProgressTracker.resolveChoiceBox(player, node, boxIndex, optionIndex);
        });
        ctx.get().setPacketHandled(true);
    }
}
