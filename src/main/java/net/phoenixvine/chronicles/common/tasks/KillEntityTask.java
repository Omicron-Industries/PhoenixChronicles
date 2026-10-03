package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestTask;

public class KillEntityTask extends QuestTask {

    private TaskIdMatcher matcher;
    private int requiredCount;
    private boolean consume;

    public KillEntityTask(ResourceLocation taskId, Component description, ResourceLocation entityId, int requiredCount,
                          boolean consume) {
        this(taskId, description, TaskIdMatcher.of(entityId), requiredCount, consume);
    }

    public KillEntityTask(ResourceLocation taskId, Component description, String entitySpec, int requiredCount,
                          boolean consume) {
        this(taskId, description, TaskIdMatcher.parse(entitySpec), requiredCount, consume);
    }

    private KillEntityTask(ResourceLocation taskId, Component description, TaskIdMatcher matcher, int requiredCount,
                           boolean consume) {
        super(taskId, description);
        this.matcher = matcher;
        this.requiredCount = requiredCount;
        this.consume = consume;
    }

    public ResourceLocation getEntityId() {
        return matcher.firstId();
    }

    public TaskIdMatcher getMatcher() {
        return matcher;
    }

    public String getSpec() {
        return matcher.spec();
    }

    public int getRequiredCount() {
        return requiredCount;
    }

    public boolean shouldConsume() {
        return consume;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getBoolean("completed");
    }

    public void onEntityKilled(Player player, ResourceLocation killedEntityId) {
        recordKill(player, matcher.matchesId(killedEntityId));
    }

    public void onEntityKilled(Player player, EntityType<?> killedType) {
        recordKill(player, matcher.matchesEntity(killedType));
    }

    private void recordKill(Player player, boolean matched) {
        if (matcher.isEmpty() || this.requiredCount <= 0 || !matched) return;

        TaskProgressAccess.with(player, this.getTaskId(), nbt -> {
            if (nbt.getBoolean("completed")) return;

            int current = Math.min(nbt.getInt("current") + 1, requiredCount);
            nbt.putInt("current", current);

            if (current >= requiredCount) {
                nbt.putBoolean("completed", true);
            }
        });
    }

    @Override
    public void tryConsume(Player player) {
        if (!consume) return;

        TaskProgressAccess.with(player, this.getTaskId(), nbt -> {
            nbt.putInt("current", 0);
            nbt.putBoolean("completed", false);
        });
    }

    @Override
    public String getProgressString(Player player) {
        int current = TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getInt("current");
        return current + "/" + requiredCount;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "kill_entity");
        tag.putString("entity_id", matcher.isEmpty() ? "minecraft:pig" : matcher.spec());
        tag.putInt("required", requiredCount);
        tag.putBoolean("consume", consume);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("entity_id")) {
            TaskIdMatcher parsed = TaskIdMatcher.parse(nbt.getString("entity_id"));
            if (!parsed.isEmpty()) this.matcher = parsed;
        }
        this.requiredCount = nbt.getInt("required");
        this.consume = nbt.getBoolean("consume");
    }
}
