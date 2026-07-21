package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;
import net.phoenixvine.chronicles.model.QuestTask;

public class AE2ItemStorageTask extends QuestTask {

    private Item item;
    private long requiredCount;
    private boolean consume;
    
    private boolean sticky = true;

    public AE2ItemStorageTask(ResourceLocation taskId, Component description, Item item, long requiredCount,
                              boolean consume) {
        super(taskId, description);
        this.item = item;
        this.requiredCount = requiredCount;
        this.consume = consume;
    }

    public Item getItem() {
        return item;
    }

    public long getRequiredCount() {
        return requiredCount;
    }

    public boolean shouldConsume() {
        return consume;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    @Override
    public ResourceLocation getDisplayItemId() {
        return item == null ? null : ForgeRegistries.ITEMS.getKey(item);
    }

    @Override
    public boolean isCompletedFor(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        if (item == null || requiredCount <= 0 || !AE2Compat.isAvailable()) return false;
        if (AE2Compat.getStoredAmount(player, item) >= requiredCount) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        return false;
    }

    @Override
    public void tryConsume(Player player) {
        if (item == null || !consume || requiredCount <= 0 || !AE2Compat.isAvailable()) return;
        AE2Compat.tryConsume(player, item, requiredCount);
    }

    @Override
    public String getProgressString(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed"))
            return requiredCount + "/" + requiredCount;
        if (item == null || !AE2Compat.isAvailable()) return "0/" + requiredCount;
        long found = Math.min(AE2Compat.getStoredAmount(player, item), requiredCount);
        return found + "/" + requiredCount;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "ae2_item_storage");
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        tag.putString("item_id", id != null ? id.toString() : "minecraft:air");
        tag.putLong("count", requiredCount);
        tag.putBoolean("consume", consume);
        tag.putBoolean("sticky", sticky);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        String itemKey = nbt.contains("item_id") ? "item_id" : nbt.contains("item") ? "item" : null;
        if (itemKey != null)
            this.item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(nbt.getString(itemKey)));
        this.requiredCount = nbt.contains("count") ? nbt.getLong("count") : nbt.getInt("amount");
        this.consume = nbt.getBoolean("consume");
        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
    }
}

