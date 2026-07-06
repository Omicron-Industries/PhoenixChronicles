package net.phoenixvine.chronicles.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;
import net.phoenixvine.chronicles.tasks.FilterFluidTask;

/**
 * Composable fluid matching predicate used by {@link FilterFluidTask}.
 *
 * Tested against {@link FluidStack} instances found in item fluid handlers in
 * the player's inventory. Amount checking is handled by the task itself; the
 * filter only decides whether a given FluidStack's fluid type qualifies.
 */
public interface IFluidFilter {

    /** True if this filter accepts the fluid TYPE of {@code stack}. Amount is ignored. */
    boolean test(FluidStack stack);

    /** Short human-readable description for the task editor and HUD. */
    String describe();

    /**
     * A representative fluid this filter would accept - the exact fluid for an exact filter,
     * the first fluid in the tag for a tag filter, etc. Used to pick an icon (that fluid's
     * bucket item) instead of showing nothing. Null if there's no sensible representative
     * (e.g. an empty tag, or a NOT filter).
     */
    default net.minecraft.world.level.material.Fluid getDisplayFluid() {
        return null;
    }

    /** Serialize to CompoundTag. Must include a {@code "filter_type"} key. */
    CompoundTag serialize();
}
