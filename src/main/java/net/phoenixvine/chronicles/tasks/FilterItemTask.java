package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.filter.IItemFilter;
import net.phoenixvine.chronicles.filter.ItemFilters;
import net.phoenixvine.chronicles.model.QuestTask;

import java.util.ArrayList;
import java.util.List;

public class FilterItemTask extends QuestTask {

    private IItemFilter filter;
    private int count;
    private boolean consume;

    private boolean sticky = true;

    public FilterItemTask(ResourceLocation taskId, Component description,
                          IItemFilter filter, int count, boolean consume) {
        super(taskId, description);
        this.filter = filter;
        this.count = Math.max(1, count);
        this.consume = consume;
    }

    private List<ItemStack> allSlots(Player player) {
        Inventory inv = player.getInventory();
        List<ItemStack> all = new ArrayList<>(inv.items);
        all.addAll(inv.offhand);
        all.addAll(inv.armor);
        return all;
    }

    private int countMatching(Player player) {
        int found = 0;
        for (ItemStack stack : allSlots(player)) {
            if (!stack.isEmpty() && filter.test(stack)) found += stack.getCount();
        }
        return found;
    }

    @Override
    public boolean dependsOnInventory() {
        return true;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        if (countMatching(player) >= count) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        return false;
    }

    @Override
    public String getProgressString(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed"))
            return count + "/" + count;
        return Math.min(countMatching(player), count) + "/" + count;
    }

    @Override
    public ResourceLocation getDisplayItemId() {
        ItemStack display = filter.getDisplayStack();
        if (display.isEmpty()) return null;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(display.getItem());
        return id;
    }

    @Override
    public void tryConsume(Player player) {
        if (!consume) return;
        int remaining = count;
        for (ItemStack stack : allSlots(player)) {
            if (remaining <= 0) break;
            if (stack.isEmpty() || !filter.test(stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        player.getInventory().setChanged();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "filter_item");
        tag.putInt("count", count);
        tag.putBoolean("consume", consume);
        tag.putBoolean("sticky", sticky);
        tag.put("filter", filter.serialize());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.count = Math.max(1, nbt.getInt("count"));
        this.consume = nbt.getBoolean("consume");
        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
        if (nbt.contains("filter")) this.filter = ItemFilters.deserialize(nbt.getCompound("filter"));
    }

    public IItemFilter getFilter() {
        return filter;
    }

    public int getCount() {
        return count;
    }

    public boolean isConsume() {
        return consume;
    }

    public void setFilter(IItemFilter f) {
        this.filter = f;
    }

    public void setCount(int c) {
        this.count = Math.max(1, c);
    }

    public void setConsume(boolean v) {
        this.consume = v;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean v) {
        this.sticky = v;
    }
}
