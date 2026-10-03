package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestTask;

public class DimensionTask extends QuestTask {

    private TaskIdMatcher matcher;

    public DimensionTask(ResourceLocation taskId, Component description, ResourceKey<Level> targetDimension) {
        super(taskId, description);
        this.matcher = TaskIdMatcher.of(targetDimension != null ? targetDimension.location() : null);
    }

    public DimensionTask(ResourceLocation taskId, Component description, String dimensionSpec) {
        super(taskId, description);
        this.matcher = TaskIdMatcher.parse(dimensionSpec, false);
    }

    public ResourceKey<Level> getTargetDimension() {
        ResourceLocation first = matcher.firstId();
        return first != null ? ResourceKey.create(Registries.DIMENSION, first) : null;
    }

    public TaskIdMatcher getMatcher() {
        return matcher;
    }

    public String getSpec() {
        return matcher.spec();
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getBoolean("completed");
    }

    public void onChangedDimension(Player player, ResourceKey<Level> dimension) {
        if (matcher.isEmpty()) return;

        if (matcher.matchesId(dimension.location())) {
            TaskProgressAccess.with(player, this.getTaskId(), taskNbt -> {
                if (!taskNbt.getBoolean("completed")) {
                    taskNbt.putBoolean("completed", true);
                }
            });
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "dimension");
        tag.putString("target", matcher.isEmpty() ? "minecraft:overworld" : matcher.spec());

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        String raw = nbt.contains("target") ? nbt.getString("target") :
                nbt.contains("dimension_id") ? nbt.getString("dimension_id") : null;
        if (raw != null) {
            TaskIdMatcher parsed = TaskIdMatcher.parse(raw, false);
            if (!parsed.isEmpty()) this.matcher = parsed;
        }
    }
}
