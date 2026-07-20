package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestEngineConfig;

import java.util.ArrayList;
import java.util.List;

public class ItemRequirementTask extends QuestTask {

    private Item item;
    private int requiredCount;
    private boolean consume;
    /**
     * Optional NBT filter: if non-null, each stack must contain ALL keys from this tag
     * (subset match — extra NBT on the stack is allowed).
     * Null means match any stack of the right item type.
     */
    private CompoundTag nbtFilter = null;
    /**
     * When true (default), once this task is satisfied it stays satisfied even if the item is
     * later placed/consumed/dropped - see isCompletedFor. When false, restores the original
     * always-live-checked behavior (task un-completes the moment you no longer hold enough).
     */
    private boolean sticky = true;

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

    @Override
    public ResourceLocation getDisplayItemId() {
        return item == null ? null : ForgeRegistries.ITEMS.getKey(item);
    }

    private boolean stackMatches(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != item) return false;
        if (nbtFilter == null || nbtFilter.isEmpty()) return true;
        CompoundTag stackTag = stack.getTag();
        if (stackTag == null) return false;
        // Subset match: every key in nbtFilter must be present and equal in stackTag
        for (String key : nbtFilter.getAllKeys()) {
            if (!stackTag.contains(key)) return false;
            if (!stackTag.get(key).equals(nbtFilter.get(key))) return false;
        }
        return true;
    }

    /** All slots we scan: main inventory + offhand + armour. */
    private Iterable<ItemStack> allSlots(Player player) {
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.addAll(player.getInventory().offhand);
        all.addAll(player.getInventory().armor);
        return all;
    }

    /**
     * True if this task should also count AE2 network storage on top of physical inventory -
     * gated on the pack-wide QuestEngineConfig toggle, AE2 actually being installed, AND no
     * nbtFilter being set. AE2's storage keys are exact-identity matches (a distinct key per
     * NBT variant), not the subset match nbtFilter does against physical stacks, so replicating
     * nbtFilter's semantics against network storage isn't a clean fit - scoped to the plain
     * "any stack of this item" case, which is the common one.
     */
    private boolean checksAe2Storage() {
        return (nbtFilter == null || nbtFilter.isEmpty()) && QuestEngineConfig.isAe2StorageForItemFluidTasksEnabled() &&
                AE2Compat.isAvailable();
    }

    /**
     * Only inventory-only when AE2 storage isn't ALSO being checked - when it is, the network's
     * contents can change independent of the player's own inventory (e.g. an autocrafting system
     * depositing items), so the tracker's "skip re-checking while inventory is unchanged"
     * shortcut would incorrectly miss a genuine network-side change. See QuestProgressTracker's
     * dependsOnInventory()-gated dirty-check for what this actually controls.
     */
    @Override
    public boolean dependsOnInventory() {
        return !checksAe2Storage();
    }

    @Override
    public boolean isCompletedFor(Player player) {
        // Sticky (default): once satisfied, stays satisfied - without this, placing/using the
        // item (e.g. building with the crafted block) before some OTHER unrelated task/quest
        // requirement is met would silently un-complete this one, forcing the player to
        // re-obtain an item they'd already legitimately gathered. Mirrors how
        // CraftItemTask/KillEntityTask latch permanently instead of re-deriving from live,
        // mutable state every check. Non-sticky restores the original always-live-checked
        // behavior for packs that deliberately want "must currently hold X" semantics.
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
        // Consume from main inventory first, then offhand, then armour
        for (ItemStack stack : allSlots(player)) {
            if (!stackMatches(stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (remaining <= 0) break;
        }
        player.getInventory().setChanged();
        // Only reaches into the network for whatever physical inventory couldn't cover.
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
        if (nbtFilter != null && !nbtFilter.isEmpty()) tag.put("nbt_filter", nbtFilter);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        String itemKey = nbt.contains("item_id") ? "item_id" : nbt.contains("item") ? "item" : null;
        if (itemKey != null)
            this.item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(nbt.getString(itemKey)));
        this.requiredCount = nbt.contains("count") ? nbt.getInt("count") : nbt.getInt("amount");
        this.consume = nbt.getBoolean("consume");
        // Default true for quests saved before this field existed, matching the sticky-by-default
        // behavior already shipped.
        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
        if (nbt.contains("nbt_filter")) this.nbtFilter = nbt.getCompound("nbt_filter");
    }
}
