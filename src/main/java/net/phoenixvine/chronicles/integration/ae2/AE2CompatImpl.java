package net.phoenixvine.chronicles.integration.ae2;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.phoenixvine.chronicles.filter.IFluidFilter;
import net.phoenixvine.chronicles.filter.IItemFilter;
import net.phoenixvine.chronicles.integration.curios.CuriosCompat;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.me.common.MEStorageMenu;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class AE2CompatImpl {

    private AE2CompatImpl() {}

    @Nullable
    private static IGrid getLinkedGrid(Player player) {
        IGrid grid = getLinkedGrid(player, player.getMainHandItem());
        if (grid != null) return grid;
        grid = getLinkedGrid(player, player.getOffhandItem());
        if (grid != null) return grid;

        for (ItemStack stack : player.getInventory().items) {
            grid = getLinkedGrid(player, stack);
            if (grid != null) return grid;
        }

        if (CuriosCompat.isAvailable()) {
            for (ItemStack stack : CuriosCompat.getEquippedCurios(player)) {
                grid = getLinkedGrid(player, stack);
                if (grid != null) return grid;
            }
        }

        if (player.containerMenu instanceof MEStorageMenu meMenu) {
            IGridNode node = meMenu.getNetworkNode();
            if (node != null) return node.getGrid();
        }

        return null;
    }

    @Nullable
    private static IGrid getLinkedGrid(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof WirelessTerminalItem term)) return null;
        return term.getLinkedGrid(stack, player.level(), player);
    }

    static long getStoredAmount(Player player, Item item) {
        return getStoredAmount(player, AEItemKey.of(item));
    }

    static long getStoredAmount(Player player, Fluid fluid) {
        return getStoredAmount(player, AEFluidKey.of(fluid));
    }

    private static long getStoredAmount(Player player, @Nullable AEKey key) {
        if (key == null) return 0;
        IGrid grid = getLinkedGrid(player);
        if (grid == null) return 0;
        return grid.getStorageService().getCachedInventory().get(key);
    }

    static boolean tryConsume(Player player, Item item, long amount) {
        return tryConsume(player, AEItemKey.of(item), amount);
    }

    static boolean tryConsume(Player player, Fluid fluid, long amount) {
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

    static long getStoredAmount(Player player, IItemFilter filter) {
        IGrid grid = getLinkedGrid(player);
        if (grid == null) return 0;
        long total = 0;
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getKey() instanceof AEItemKey itemKey && filter.test(itemKey.toStack(1))) {
                total += entry.getLongValue();
            }
        }
        return total;
    }

    static long getStoredAmount(Player player, IFluidFilter filter) {
        IGrid grid = getLinkedGrid(player);
        if (grid == null) return 0;
        long total = 0;
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getKey() instanceof AEFluidKey fluidKey) {
                FluidStack stack = fluidKey.toStack((int) Math.min(entry.getLongValue(), Integer.MAX_VALUE));
                if (filter.test(stack)) total += entry.getLongValue();
            }
        }
        return total;
    }

    static long tryConsume(Player player, IItemFilter filter, long amount) {
        return tryConsumeMatching(player, key -> key instanceof AEItemKey itemKey && filter.test(itemKey.toStack(1)),
                amount);
    }

    static long tryConsume(Player player, IFluidFilter filter, long amount) {
        return tryConsumeMatching(player,
                key -> key instanceof AEFluidKey fluidKey && filter.test(fluidKey.toStack(1)), amount);
    }

    private static long tryConsumeMatching(Player player, java.util.function.Predicate<AEKey> matcher, long amount) {
        if (amount <= 0) return 0;
        IGrid grid = getLinkedGrid(player);
        if (grid == null) return 0;
        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofPlayer(player);

        List<AEKey> matchingKeys = new ArrayList<>();
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (matcher.test(entry.getKey())) matchingKeys.add(entry.getKey());
        }

        long remaining = amount;
        long consumed = 0;
        for (AEKey key : matchingKeys) {
            if (remaining <= 0) break;
            long extracted = storage.extract(key, remaining, Actionable.MODULATE, source);
            consumed += extracted;
            remaining -= extracted;
        }
        return consumed;
    }
}
