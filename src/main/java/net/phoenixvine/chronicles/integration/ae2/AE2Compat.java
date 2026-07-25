package net.phoenixvine.chronicles.integration.ae2;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.ModList;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import org.jetbrains.annotations.Nullable;

public final class AE2Compat {

    private AE2Compat() {}

    public static final String AE2_MOD_ID = "ae2";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(AE2_MOD_ID);
    }

    @Nullable
    private static IGrid getLinkedGrid(Player player) {
        IGrid grid = getLinkedGrid(player, player.getMainHandItem());
        return grid != null ? grid : getLinkedGrid(player, player.getOffhandItem());
    }

    @Nullable
    private static IGrid getLinkedGrid(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof WirelessTerminalItem term)) return null;
        return term.getLinkedGrid(stack, player.level(), player);
    }

    public static long getStoredAmount(Player player, Item item) {
        return getStoredAmount(player, AEItemKey.of(item));
    }

    public static long getStoredAmount(Player player, Fluid fluid) {
        return getStoredAmount(player, AEFluidKey.of(fluid));
    }

    private static long getStoredAmount(Player player, @Nullable AEKey key) {
        if (key == null) return 0;
        IGrid grid = getLinkedGrid(player);
        if (grid == null) return 0;
        return grid.getStorageService().getCachedInventory().get(key);
    }

    public static boolean tryConsume(Player player, Item item, long amount) {
        return tryConsume(player, AEItemKey.of(item), amount);
    }

    public static boolean tryConsume(Player player, Fluid fluid, long amount) {
        return tryConsume(player, AEFluidKey.of(fluid), amount);
    }

    private static boolean tryConsume(Player player, @Nullable AEKey key, long amount) {
        if (key == null || amount <= 0) return false;
        IGrid grid = getLinkedGrid(player);
        if (grid == null) return false;
        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofPlayer(player);
        long extracted = storage.extract(key, amount, Actionable.SIMULATE, source);
        if (extracted < amount) return false;
        storage.extract(key, amount, Actionable.MODULATE, source);
        return true;
    }
}
