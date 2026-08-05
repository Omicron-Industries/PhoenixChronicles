package net.phoenixvine.chronicles.integration.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Collections;
import java.util.List;

public final class CuriosCompat {

    private CuriosCompat() {}

    public static final String CURIOS_MOD_ID = "curios";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(CURIOS_MOD_ID);
    }

    public static List<ItemStack> getEquippedCurios(Player player) {
        return isAvailable() ? CuriosCompatImpl.getEquippedCurios(player) : Collections.emptyList();
    }
}
