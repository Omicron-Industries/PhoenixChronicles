package net.phoenixvine.chronicles.integration.ae2;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.chronicles.filter.IFluidFilter;
import net.phoenixvine.chronicles.filter.IItemFilter;

public final class AE2Compat {

    private AE2Compat() {}

    public static final String AE2_MOD_ID = "ae2";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(AE2_MOD_ID);
    }

    public static long getStoredAmount(Player player, Item item) {
        return isAvailable() ? AE2CompatImpl.getStoredAmount(player, item) : 0;
    }

    public static long getStoredAmount(Player player, Fluid fluid) {
        return isAvailable() ? AE2CompatImpl.getStoredAmount(player, fluid) : 0;
    }

    public static boolean tryConsume(Player player, Item item, long amount) {
        return isAvailable() && AE2CompatImpl.tryConsume(player, item, amount);
    }

    public static boolean tryConsume(Player player, Fluid fluid, long amount) {
        return isAvailable() && AE2CompatImpl.tryConsume(player, fluid, amount);
    }

    public static long getStoredAmount(Player player, IItemFilter filter) {
        return isAvailable() ? AE2CompatImpl.getStoredAmount(player, filter) : 0;
    }

    public static long getStoredAmount(Player player, IFluidFilter filter) {
        return isAvailable() ? AE2CompatImpl.getStoredAmount(player, filter) : 0;
    }

    public static long tryConsume(Player player, IItemFilter filter, long amount) {
        return isAvailable() ? AE2CompatImpl.tryConsume(player, filter, amount) : 0;
    }

    public static long tryConsume(Player player, IFluidFilter filter, long amount) {
        return isAvailable() ? AE2CompatImpl.tryConsume(player, filter, amount) : 0;
    }
}
