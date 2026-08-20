package net.phoenixvine.chronicles.tasks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class StatTrackerTask extends QuestTask {

    private ResourceLocation statId;
    private int targetValue;
    private boolean consume;

    public StatTrackerTask(ResourceLocation taskId, Component description, ResourceLocation statId, int targetValue,
                           boolean consume) {
        super(taskId, description);
        this.statId = statId;
        this.targetValue = targetValue;
        this.consume = consume;
    }

    public ResourceLocation getStatId() {
        return statId;
    }

    public int getTargetValue() {
        return targetValue;
    }

    public boolean shouldConsume() {
        return consume;
    }

    private StatsCounter statsFor(Player player) {
        if (player instanceof ServerPlayer serverPlayer) return serverPlayer.getStats();
        if (player.level().isClientSide()) return net.phoenixvine.chronicles.client.ClientStatsHelper.getStats(player);
        return null;
    }

    private Integer rawStatValue(Player player) {
        if (statId == null) return null;

        if (!BuiltInRegistries.CUSTOM_STAT.containsKey(statId)) return null;
        ResourceLocation canonical = BuiltInRegistries.CUSTOM_STAT.get(statId);
        StatsCounter counter = statsFor(player);
        if (counter == null) return null;
        Stat<ResourceLocation> stat = Stats.CUSTOM.get(canonical);
        return counter.getValue(stat);
    }

    @Override
    public boolean isCompletedFor(Player player) {
        if (targetValue <= 0) return false;
        if (TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        if (!(player instanceof ServerPlayer)) return false;

        Integer rawStat = rawStatValue(player);
        if (rawStat == null) return false;

        int baselineOffset = TaskProgressAccess.getOrEmpty(player, getTaskId()).getInt("baseline");
        int relativeStatProgress = Math.max(0, rawStat - baselineOffset);
        boolean done = relativeStatProgress >= targetValue;
        int cappedProgress = Math.min(relativeStatProgress, targetValue);
        TaskProgressAccess.with(player, getTaskId(), nbt -> {
            nbt.putInt("current", cappedProgress);
            if (done) nbt.putBoolean("completed", true);
        });
        return done;
    }

    @Override
    public void tryConsume(Player player) {
        if (!consume) return;
        Integer currentRawValue = rawStatValue(player);
        if (currentRawValue != null) {
            TaskProgressAccess.with(player, this.getTaskId(), nbt -> nbt.putInt("baseline", currentRawValue));
        }
    }

    @Override
    public String getProgressString(Player player) {
        CompoundTag cached = TaskProgressAccess.getOrEmpty(player, getTaskId());
        if (cached.getBoolean("completed")) return targetValue + "/" + targetValue;
        if (player instanceof ServerPlayer) {
            Integer rawStat = rawStatValue(player);
            if (rawStat != null) {
                int baselineOffset = cached.getInt("baseline");
                int currentProgress = Math.min(Math.max(0, rawStat - baselineOffset), targetValue);
                return currentProgress + "/" + targetValue;
            }
        }
        return cached.getInt("current") + "/" + targetValue;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "stat");
        tag.putString("stat_id", statId != null ? statId.toString() : "minecraft:jump");
        tag.putInt("target", targetValue);
        tag.putBoolean("consume", consume);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("stat_id")) {
            this.statId = ResourceLocation.parse(nbt.getString("stat_id"));
        }
        this.targetValue = nbt.getInt("target");
        this.consume = nbt.getBoolean("consume");
    }
}
