package net.phoenixvine.chronicles.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

public interface IFluidFilter {

    boolean test(FluidStack stack);

    String describe();

    default net.minecraft.world.level.material.Fluid getDisplayFluid() {
        return null;
    }

    CompoundTag serialize();
}
