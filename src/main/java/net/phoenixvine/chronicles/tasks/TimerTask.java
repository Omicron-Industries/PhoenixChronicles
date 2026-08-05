package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class TimerTask extends QuestTask {

    private int durationSeconds;

    public TimerTask(ResourceLocation taskId, Component description, int durationSeconds) {
        super(taskId, description);
        this.durationSeconds = Math.max(1, durationSeconds);
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    @Override
    public void onTick(Player player) {
        if (player.level().isClientSide) return;
        if (isCompletedFor(player)) return;

        TaskProgressAccess.with(player, getTaskId(), nbt -> {
            long now = System.currentTimeMillis();
            if (!nbt.contains("start_time")) {
                nbt.putLong("start_time", now);
                return;
            }
            long start = nbt.getLong("start_time");
            if (now - start >= durationSeconds * 1000L) {
                nbt.putBoolean("completed", true);
            }
        });
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    @Override
    public String getProgressString(Player player) {
        CompoundTag nbt = TaskProgressAccess.getOrEmpty(player, getTaskId());
        if (nbt.getBoolean("completed")) return "Done";
        if (!nbt.contains("start_time")) return "0/" + durationSeconds + "s";
        long elapsedSeconds = (System.currentTimeMillis() - nbt.getLong("start_time")) / 1000L;
        long clamped = Math.max(0, Math.min(durationSeconds, elapsedSeconds));
        return clamped + "/" + durationSeconds + "s";
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "timer");
        tag.putInt("duration_seconds", durationSeconds);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("duration_seconds")) durationSeconds = Math.max(1, nbt.getInt("duration_seconds"));
    }
}
