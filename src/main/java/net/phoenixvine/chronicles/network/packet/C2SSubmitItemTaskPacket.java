package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.ItemRequirementTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import java.util.function.Supplier;

public class C2SSubmitItemTaskPacket {

    private final ResourceLocation taskId;

    public C2SSubmitItemTaskPacket(ResourceLocation taskId) {
        this.taskId = taskId;
    }

    public C2SSubmitItemTaskPacket(FriendlyByteBuf buf) {
        this.taskId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(taskId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || taskId == null) return;

            QuestNode node = QuestTreeRegistry.getTaskOwner(taskId);
            if (node == null) return;

            QuestTask task = node.getEffectiveTasks(player.getServer(), player).stream()
                    .filter(t -> taskId.equals(t.getTaskId()))
                    .findFirst().orElse(null);
            if (!(task instanceof ItemRequirementTask itemTask) || !itemTask.shouldConsume()) return;
            if (itemTask.isCompletedFor(player)) return;

            QuestState state = QuestProgressTracker.getQuestState(player, node);
            if (state != QuestState.UNLOCKED && state != QuestState.ACTIVE) return;

            if (!itemTask.submit(player)) {
                ItemStack shown = itemTask.getItem() != null ? itemTask.getItem().getDefaultInstance() :
                        ItemStack.EMPTY;
                player.sendSystemMessage(Component.literal("§cYou need " + itemTask.getRequiredCount() + "x ")
                        .append(shown.isEmpty() ? Component.literal("the required item") : shown.getHoverName()
                                .copy().withStyle(s -> s.withColor(0xFF5555)))
                        .append(Component.literal("§c to hand this in.")));
                return;
            }

            QuestProgressTracker.checkAndTryComplete(player, node);
            QuestProgressTracker.sendProgressSync(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
