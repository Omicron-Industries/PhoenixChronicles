package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class InfoTask extends QuestTask {

    private String body;

    public InfoTask(ResourceLocation taskId, Component description, String body) {
        super(taskId, description);
        this.body = body != null ? body : "";
    }

    public String getBody() {
        return body;
    }

    public static void acknowledge(Player player, ResourceLocation taskId) {
        TaskProgressAccess.with(player, taskId, nbt -> nbt.putBoolean("acknowledged", true));
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("acknowledged");
    }

    @Override
    public String getProgressString(Player player) {
        return isCompletedFor(player) ? "Read" : "Unread";
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "info");
        tag.putString("body", body);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("body")) body = nbt.getString("body");
    }
}
