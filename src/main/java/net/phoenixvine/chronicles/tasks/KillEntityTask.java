package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

/**
 * Task: Kill a certain number of a specific entity type.
 * Migrated to player-capability data layers to safely isolate multi-player progress states.
 * SNBT shape: { type: "kill_entity", entity_id: "minecraft:zombie", required: 10, consume: true }
 */
public class KillEntityTask extends QuestTask {

    private ResourceLocation entityId;
    private int requiredCount;
    private boolean consume; // NEW: Uniform toggle rule (controls progress resetting on reward claim)

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

    /** Call this from the LivingDeathEvent subscriber for the owning player. */
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

    /**
     * Call this when claiming rewards.
     * If 'consume' is true, it resets the player's kill count tracking for repeatable/bounty stability.
     */
    public void tryConsume(Player player) {
        if (!consume) return; // Retain completion progress flags if consume is turned off

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
        tag.putBoolean("consume", consume); // Save parameter config
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("entity_id")) {
            this.entityId = new ResourceLocation(nbt.getString("entity_id"));
        }
        this.requiredCount = nbt.getInt("required");
        this.consume = nbt.getBoolean("consume"); // Load parameter config
    }
}
