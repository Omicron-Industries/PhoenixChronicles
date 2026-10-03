package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestTask;

public class BiomeTask extends QuestTask {

    private TaskIdMatcher matcher;

    public BiomeTask(ResourceLocation taskId, Component description, ResourceLocation biomeId) {
        super(taskId, description);
        this.matcher = TaskIdMatcher.of(biomeId);
    }

    public BiomeTask(ResourceLocation taskId, Component description, String biomeSpec) {
        super(taskId, description);
        this.matcher = TaskIdMatcher.parse(biomeSpec);
    }

    public ResourceLocation getBiomeId() {
        return matcher.firstId();
    }

    public TaskIdMatcher getMatcher() {
        return matcher;
    }

    public String getSpec() {
        return matcher.spec();
    }

    @Override
    public void onTick(Player player) {
        if (player.level().isClientSide || matcher.isEmpty()) return;
        if (isCompletedFor(player)) return;

        if (matcher.matchesBiome(player.level().getBiome(player.blockPosition()))) {
            TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
        }
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "biome");
        tag.putString("biome_id", matcher.isEmpty() ? "minecraft:plains" : matcher.spec());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("biome_id")) {
            TaskIdMatcher parsed = TaskIdMatcher.parse(nbt.getString("biome_id"));
            if (!parsed.isEmpty()) matcher = parsed;
        }
    }
}
