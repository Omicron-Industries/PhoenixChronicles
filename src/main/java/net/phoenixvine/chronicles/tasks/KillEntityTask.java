package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class KillEntityTask extends QuestTask {

    private ResourceLocation entityId;
    private int requiredCount;
    private boolean consume; 

    public KillEntityTask(ResourceLocation taskId, Component description, ResourceLocation entityId, int requiredCount,
                          boolean consume) {
        super(taskId, description);
        this.entityId = entityId;
        this.requiredCount = requiredCount;
        this.consume = consume;
    }

    public ResourceLocation getEntityId() {
        return entityId;
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
        if (this.entityId == null || this.requiredCount <= 0) return;

        TaskProgressAccess.with(player, this.getTaskId(), nbt -> {
            if (nbt.getBoolean("completed")) return;

            if (killedEntityId.equals(entityId)) {
                int current = nbt.getInt("current");
                current = Math.min(current + 1, requiredCount);
                nbt.putInt("current", current);

                if (current >= requiredCount) {
                    nbt.putBoolean("completed", true);
                }
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
        tag.putString("entity_id", entityId != null ? entityId.toString() : "minecraft:pig");
        tag.putInt("required", requiredCount);
        tag.putBoolean("consume", consume); 
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("entity_id")) {
            this.entityId = new ResourceLocation(nbt.getString("entity_id"));
        }
        this.requiredCount = nbt.getInt("required");
        this.consume = nbt.getBoolean("consume"); 
    }
}

