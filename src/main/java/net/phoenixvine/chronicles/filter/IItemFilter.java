package net.phoenixvine.chronicles.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.tasks.FilterItemTask;

public interface IItemFilter {

    boolean test(ItemStack stack);

    String describe();

    CompoundTag serialize();

    default ItemStack getDisplayStack() {
        return ItemStack.EMPTY;
    }
}

