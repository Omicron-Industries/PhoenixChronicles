package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.filter.NbtMatchUtil;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;
import net.phoenixvine.chronicles.integration.curios.CuriosCompat;
import net.phoenixvine.chronicles.model.QuestTask;

import java.util.ArrayList;
import java.util.List;

public class ItemRequirementTask extends QuestTask {

    private Item item;
    private int requiredCount;
    private boolean consume;

    private CompoundTag nbtFilter = null;

    private boolean sticky = true;

    private boolean checkAe2Storage = AE2Compat.isAvailable();

    public ItemRequirementTask(ResourceLocation taskId, Component description, Item item, int requiredCount,
                               boolean consume) {
        super(taskId, description);
        this.item = item;
        this.requiredCount = requiredCount;
        this.consume = consume;
    }

    public Item getItem() {
        return item;
    }

    public int getRequiredCount() {
        return requiredCount;
    }

    public boolean shouldConsume() {
        return consume;
    }

    public CompoundTag getNbtFilter() {
        return nbtFilter;
    }

    public void setNbtFilter(CompoundTag filter) {
        this.nbtFilter = filter;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public boolean isCheckAe2Storage() {
        return checkAe2Storage;
    }

    public void setCheckAe2Storage(boolean checkAe2Storage) {
        this.checkAe2Storage = checkAe2Storage;
    }

    @Override
    public ResourceLocation getDisplayItemId() {
        return item == null ? null : ForgeRegistries.ITEMS.getKey(item);
    }

    private boolean stackMatches(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != item) return false;
        return NbtMatchUtil.matches(stack.getTag(), nbtFilter);
    }

    private Iterable<ItemStack> allSlots(Player player) {
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.addAll(player.getInventory().offhand);
        all.addAll(player.getInventory().armor);

        if (player.containerMenu != null && !player.containerMenu.getCarried().isEmpty()) {
            all.add(player.containerMenu.getCarried());
        }

        all.addAll(CuriosCompat.getEquippedCurios(player));
        return all;
    }

    private boolean checksAe2Storage() {
        return checkAe2Storage && (nbtFilter == null || nbtFilter.isEmpty()) && AE2Compat.isAvailable();
    }

    @Override
    public boolean dependsOnInventory() {
        return !checksAe2Storage();
    }

    @Override
    public boolean isCompletedFor(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        if (item == null || requiredCount <= 0) return false;
        long found = checksAe2Storage() ? AE2Compat.getStoredAmount(player, item) : 0;
        if (found >= requiredCount) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        for (ItemStack stack : allSlots(player)) {
            if (stackMatches(stack)) {
                found += stack.getCount();
                if (found >= requiredCount) {
                    if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void tryConsume(Player player) {
        if (item == null || !consume) return;
        int remaining = requiredCount;

        for (ItemStack stack : allSlots(player)) {
            if (!stackMatches(stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (remaining <= 0) break;
        }
        player.getInventory().setChanged();

        if (remaining > 0 && checksAe2Storage()) {
            AE2Compat.tryConsume(player, item, remaining);
        }
    }

    @Override
    public String getProgressString(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed"))
            return requiredCount + "/" + requiredCount;
        if (item == null) return "0/" + requiredCount;
        long found = checksAe2Storage() ? AE2Compat.getStoredAmount(player, item) : 0;
        for (ItemStack stack : allSlots(player)) {
            if (stackMatches(stack)) found += stack.getCount();
        }
        return Math.min(found, requiredCount) + "/" + requiredCount;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "item_check");
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        tag.putString("item_id", id != null ? id.toString() : "minecraft:air");
        tag.putInt("count", requiredCount);
        tag.putBoolean("consume", consume);
        tag.putBoolean("sticky", sticky);
        tag.putBoolean("check_ae2_storage", checkAe2Storage);
        if (nbtFilter != null && !nbtFilter.isEmpty()) tag.put("nbt_filter", nbtFilter);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        String itemKey = nbt.contains("item_id") ? "item_id" : nbt.contains("item") ? "item" : null;
        if (itemKey != null)
            this.item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(nbt.getString(itemKey)));
        this.requiredCount = nbt.contains("count") ? nbt.getInt("count") : nbt.getInt("amount");
        this.consume = nbt.getBoolean("consume");

        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
        this.checkAe2Storage = !nbt.contains("check_ae2_storage") || nbt.getBoolean("check_ae2_storage");
        if (nbt.contains("nbt_filter")) this.nbtFilter = nbt.getCompound("nbt_filter");
    }
}
