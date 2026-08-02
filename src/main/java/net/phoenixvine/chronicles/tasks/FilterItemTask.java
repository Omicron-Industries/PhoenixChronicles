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
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;
import net.phoenixvine.chronicles.integration.curios.CuriosCompat;
import net.phoenixvine.chronicles.model.QuestTask;

import java.util.ArrayList;
import java.util.List;

public class FilterItemTask extends QuestTask {

    private IItemFilter filter;
    private int count;
    private boolean consume;

    private boolean sticky = true;

    private boolean checkAe2Storage = AE2Compat.isAvailable();

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
        all.addAll(CuriosCompat.getEquippedCurios(player));
        return all;
    }

    private int countMatching(Player player) {
        int found = 0;
        for (ItemStack stack : allSlots(player)) {
            if (!stack.isEmpty() && filter.test(stack)) found += stack.getCount();
        }
        return found;
    }

    private boolean checksAe2Storage() {
        return checkAe2Storage && AE2Compat.isAvailable();
    }

    private long countMatchingWithAe2(Player player) {
        long found = checksAe2Storage() ? AE2Compat.getStoredAmount(player, filter) : 0;
        return found + countMatching(player);
    }

    @Override
    public boolean dependsOnInventory() {
        return !checksAe2Storage();
    }

    @Override
    public boolean isCompletedFor(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        if (countMatchingWithAe2(player) >= count) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        return false;
    }

    @Override
    public String getProgressString(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed"))
            return count + "/" + count;
        return Math.min(countMatchingWithAe2(player), count) + "/" + count;
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

        if (remaining > 0 && checksAe2Storage()) {
            AE2Compat.tryConsume(player, filter, remaining);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "filter_item");
        tag.putInt("count", count);
        tag.putBoolean("consume", consume);
        tag.putBoolean("sticky", sticky);
        tag.putBoolean("check_ae2_storage", checkAe2Storage);
        tag.put("filter", filter.serialize());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.count = Math.max(1, nbt.getInt("count"));
        this.consume = nbt.getBoolean("consume");
        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
        this.checkAe2Storage = !nbt.contains("check_ae2_storage") || nbt.getBoolean("check_ae2_storage");
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

    public boolean isCheckAe2Storage() {
        return checkAe2Storage;
    }

    public void setCheckAe2Storage(boolean v) {
        this.checkAe2Storage = v;
    }
}
