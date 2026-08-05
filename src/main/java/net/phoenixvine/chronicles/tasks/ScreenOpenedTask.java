package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class ScreenOpenedTask extends QuestTask {

    public ScreenOpenedTask(ResourceLocation taskId, Component description) {
        super(taskId, description);
    }

    public static void complete(Player player, ResourceLocation taskId) {
        TaskProgressAccess.with(player, taskId, nbt -> nbt.putBoolean("completed", true));
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "screen_opened");
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {}
}
