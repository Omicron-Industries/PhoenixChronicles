package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class TagItemTask extends QuestTask {

    private TagKey<Item> tag;
    private int required = 1;
    
    private boolean sticky = true;

    public TagItemTask(ResourceLocation taskId, Component description, TagKey<Item> tag, int required) {
        super(taskId, description);
        this.tag = tag;
        this.required = Math.max(1, required);
    }

    public TagKey<Item> getTag() {
        return tag;
    }

    public int getRequired() {
        return required;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    private int countMatching(Player player) {
        if (tag == null) return 0;
        Inventory inv = player.getInventory();
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(tag)) count += stack.getCount();
        }
        return count;
    }

    @Override
    public boolean dependsOnInventory() {
        return true;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        if (countMatching(player) >= required) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        return false;
    }

    @Override
    public String getProgressString(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed"))
            return required + "/" + required;
        return countMatching(player) + "/" + required;
    }

    @Override
    public ResourceLocation getDisplayItemId() {
        if (tag == null) return null;
        var iter = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator();
        return iter.hasNext() ? net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(iter.next().value()) : null;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag t = new CompoundTag();
        t.putString("type", "tag_item");
        t.putString("tag", tag != null ? tag.location().toString() : "");
        t.putInt("required", required);
        t.putBoolean("sticky", sticky);
        return t;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("tag")) {
            String raw = nbt.getString("tag");
            if (!raw.isBlank()) tag = ItemTags.create(new ResourceLocation(raw));
        }
        if (nbt.contains("required")) required = Math.max(1, nbt.getInt("required"));
        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
    }
}

