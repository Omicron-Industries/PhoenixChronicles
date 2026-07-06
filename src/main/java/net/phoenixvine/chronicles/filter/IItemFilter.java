package net.phoenixvine.chronicles.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.tasks.FilterItemTask;

/**
 * Composable item matching predicate used by {@link FilterItemTask}.
 *
 * Implementations must be serializable to/from CompoundTag for quest file persistence.
 * All built-in implementations live in {@link ItemFilters}.
 */
public interface IItemFilter {

    /** True if this filter accepts {@code stack}. Never called on empty stacks. */
    boolean test(ItemStack stack);

    /** Short human-readable description for the task editor and HUD (e.g. "#forge:ingots/iron"). */
    String describe();

    /**
     * Serialize to NBT. Must include a {@code "filter_type"} string key identifying
     * the implementation so {@link ItemFilters#deserialize} can reconstruct it.
     */
    CompoundTag serialize();

    /**
     * Pick the "best" single ItemStack to use as the display icon in the quest UI.
     * Returns ItemStack.EMPTY if no concrete item can be determined.
     */
    default ItemStack getDisplayStack() {
        return ItemStack.EMPTY;
    }
}
