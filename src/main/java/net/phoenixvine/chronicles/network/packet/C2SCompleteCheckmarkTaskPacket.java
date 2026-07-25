package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.CheckmarkTask;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2SCompleteCheckmarkTaskPacket {

    private final ResourceLocation taskId;

    public C2SCompleteCheckmarkTaskPacket(ResourceLocation taskId) {
        this.taskId = taskId;
    }

    public C2SCompleteCheckmarkTaskPacket(FriendlyByteBuf buf) {
        this.taskId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(taskId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            QuestNode node = QuestTreeRegistry.getTaskOwner(taskId);
            if (node == null) return;

            QuestTask task = node.getTasks().stream()
                    .filter(t -> t.getTaskId().equals(taskId))
                    .findFirst().orElse(null);
            if (!(task instanceof CheckmarkTask)) return;
            if (task.isCompletedFor(player)) return;

            CheckmarkTask.complete(player, taskId);
            QuestProgressTracker.checkAndTryComplete(player, node);
        });
        ctx.get().setPacketHandled(true);
    }
}
