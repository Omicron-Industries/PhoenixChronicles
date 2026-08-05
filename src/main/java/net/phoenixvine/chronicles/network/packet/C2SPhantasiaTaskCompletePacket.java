package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.ViewMachineTask;
import net.phoenixvine.chronicles.tasks.ViewSceneTask;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2SPhantasiaTaskCompletePacket {

    private final ResourceLocation questId;
    private final ResourceLocation taskId;

    public C2SPhantasiaTaskCompletePacket(ResourceLocation questId, ResourceLocation taskId) {
        this.questId = questId;
        this.taskId = taskId;
    }

    public C2SPhantasiaTaskCompletePacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
        this.taskId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
        buf.writeResourceLocation(taskId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || questId == null || taskId == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null || node.getTasks() == null) return;

            QuestState state = QuestProgressTracker.getQuestState(player, node);
            if (state == QuestState.COMPLETED || state == QuestState.LOCKED) return;

            for (QuestTask task : node.getTasks()) {
                if (!taskId.equals(task.getTaskId())) continue;

                if (!(task instanceof ViewMachineTask) && !(task instanceof ViewSceneTask)) break;
                if (task.isCompletedFor(player)) break;

                TaskProgressAccess.with(player, taskId, nbt -> nbt.putBoolean("completed", true));
                QuestProgressTracker.checkAndTryComplete(player, node);
                QuestProgressTracker.sendProgressSync(player);
                break;
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
