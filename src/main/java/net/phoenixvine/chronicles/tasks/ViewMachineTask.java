package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class ViewMachineTask extends QuestTask {

    private String machineId;   
    private float minSeconds;

    public ViewMachineTask(ResourceLocation taskId, Component description, String machineId, float minSeconds) {
        super(taskId, description);
        this.machineId = machineId;
        this.minSeconds = minSeconds;
    }

    public String getMachineId() {
        return machineId;
    }

    public float getMinSeconds() {
        return minSeconds;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    public void markCompletedClient(Player player) {
        TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
    }

    @Override
    public String getProgressString(Player player) {
        return isCompletedFor(player) ? "Viewed" : (minSeconds > 0 ? "View for " + (int) minSeconds + "s" : "View");
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "view_machine");
        tag.putString("machine_id", machineId != null ? machineId : "");
        tag.putFloat("min_seconds", minSeconds);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.machineId = nbt.getString("machine_id");
        this.minSeconds = nbt.contains("min_seconds") ? nbt.getFloat("min_seconds") : 3.0f;
    }
}

