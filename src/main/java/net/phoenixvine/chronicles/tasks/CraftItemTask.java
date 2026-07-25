package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class CraftItemTask extends QuestTask {

    private ResourceLocation itemId;
    private int requiredCount;

    public CraftItemTask(ResourceLocation taskId, Component description, ResourceLocation itemId, int requiredCount) {
        super(taskId, description);
        this.itemId = itemId;
        this.requiredCount = requiredCount;
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    @Override
    public ResourceLocation getDisplayItemId() {
        return itemId;
    }

    public int getRequiredCount() {
        return requiredCount;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getBoolean("completed");
    }

    public void onItemCrafted(Player player, ResourceLocation craftedItemId, int amount) {
        if (this.itemId == null || this.requiredCount <= 0) return;

        TaskProgressAccess.with(player, this.getTaskId(), nbt -> {
            if (nbt.getBoolean("completed")) return;

            if (craftedItemId.equals(itemId)) {
                int currentCount = nbt.getInt("current");
                currentCount = Math.min(currentCount + amount, requiredCount);

                nbt.putInt("current", currentCount);
                if (currentCount >= requiredCount) {
                    nbt.putBoolean("completed", true);
                }
            }
        });
    }

    @Override
    public String getProgressString(Player player) {
        int currentCount = TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getInt("current");
        return currentCount + "/" + requiredCount;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "craft_item");
        tag.putString("item_id", itemId != null ? itemId.toString() : "minecraft:air");
        tag.putInt("required", requiredCount);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("item_id")) {
            this.itemId = new ResourceLocation(nbt.getString("item_id"));
        }
        this.requiredCount = nbt.getInt("required");
    }
}
