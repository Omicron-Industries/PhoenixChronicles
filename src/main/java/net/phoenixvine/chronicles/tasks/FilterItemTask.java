package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.filter.IItemFilter;
import net.phoenixvine.chronicles.filter.ItemFilters;
import net.phoenixvine.chronicles.model.QuestTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Item task backed by a composable {@link IItemFilter}.
 *
 * Replaces the split between {@code item_check} (exact) and {@code tag_item} (single tag)
 * with a unified task that can express any combination:
 *
 * <pre>
 * // Any copper or gold ingot:
 * filter = anyOf(tag("forge:ingots/copper"), tag("forge:ingots/gold"))
 *
 * // A specific enchanted item (exact item + NBT):
 * filter = exact(Items.DIAMOND_SWORD, nbtWith("Enchantments", ...))
 *
 * // Any Create mod item:
 * filter = mod("create")
 *
 * // Any iron tool that isn't a sword:
 * filter = allOf(tag("forge:tools"), not(exact(Items.IRON_SWORD)))
 * </pre>
 *
 * Quest file NBT shape:
 * 
 * <pre>
 * {
 *   type: "filter_item",
 *   count: 16,
 *   consume: false,
 *   filter: { filter_type: "tag", tag: "forge:ingots/copper" }
 * }
 * </pre>
 *
 * Checks ALL inventory slots: main, offhand, and armor.
 */
public class FilterItemTask extends QuestTask {

    private IItemFilter filter;
    private int count;
    private boolean consume;

    public FilterItemTask(ResourceLocation taskId, Component description,
                          IItemFilter filter, int count, boolean consume) {
        super(taskId, description);
        this.filter = filter;
        this.count = Math.max(1, count);
        this.consume = consume;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    // ── QuestTask ─────────────────────────────────────────────────────────────

    @Override
    public boolean isCompletedFor(Player player) {
        return countMatching(player) >= count;
    }

    @Override
    public String getProgressString(Player player) {
        return Math.min(countMatching(player), count) + "/" + count;
    }

    @Override
    public ResourceLocation getDisplayItemId() {
        ItemStack display = filter.getDisplayStack();
        if (display.isEmpty()) return null;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(display.getItem());
        return id;
    }

    /** Consume exactly {@code count} matching items from the player's inventory. */
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

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "filter_item");
        tag.putInt("count", count);
        tag.putBoolean("consume", consume);
        tag.put("filter", filter.serialize());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.count = Math.max(1, nbt.getInt("count"));
        this.consume = nbt.getBoolean("consume");
        if (nbt.contains("filter")) this.filter = ItemFilters.deserialize(nbt.getCompound("filter"));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

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
}
