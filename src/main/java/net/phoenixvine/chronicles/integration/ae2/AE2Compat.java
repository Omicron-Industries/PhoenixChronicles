package net.phoenixvine.chronicles.integration.ae2;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.ModList;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import org.jetbrains.annotations.Nullable;

/**
 * Applied Energistics 2 integration for Phoenix Chronicles - AE2 is a soft dependency, same
 * convention as {@link net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat}: guard every
 * call behind {@link #isAvailable()}, keep AE2 types off this mod's always-loaded classes.
 *
 * Backs {@link net.phoenixvine.chronicles.tasks.AE2ItemStorageTask} and
 * {@link net.phoenixvine.chronicles.tasks.AE2FluidStorageTask} - "does the player have at least
 * N of this item/fluid stored in their ME network" quests, read through whichever wireless
 * terminal the player is currently holding.
 *
 * Reads go through {@link IStorageService#getCachedInventory()}, NOT
 * {@link MEStorage#extract} with {@code Actionable.SIMULATE} - the naive way to ask "how much of
 * X is stored" is to simulate-extract Long.MAX_VALUE, but that walks the network's live storage
 * graph on every call. AE2 already keeps a {@code KeyCounter} snapshot updated at most once per
 * tick specifically so callers don't have to do that - checked every tick by
 * QuestProgressTracker's poll loop (this task is NOT gated behind the inventory-dirty-check
 * optimization other tasks use, since a network's stored contents can change independently of
 * the player's own inventory - e.g. an autocrafting system depositing items - so there's nothing
 * cheap to fingerprint against here; the cached-inventory read is what keeps that affordable).
 */
public final class AE2Compat {

    private AE2Compat() {}

    public static final String AE2_MOD_ID = "ae2";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(AE2_MOD_ID);
    }

    /**
     * The ME network linked to whichever wireless terminal the player is currently holding
     * (main hand checked first, then offhand), or null if they aren't holding one, or the one
     * they're holding isn't currently linked to a network.
     */
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

    /** 0 if the player isn't holding a linked wireless terminal, or the network has none stored. */
    public static long getStoredAmount(Player player, Item item) {
        return getStoredAmount(player, AEItemKey.of(item));
    }

    /** 0 if the player isn't holding a linked wireless terminal, or the network has none stored. */
    public static long getStoredAmount(Player player, Fluid fluid) {
        return getStoredAmount(player, AEFluidKey.of(fluid));
    }

    private static long getStoredAmount(Player player, @Nullable AEKey key) {
        if (key == null) return 0;
        IGrid grid = getLinkedGrid(player);
        if (grid == null) return 0;
        return grid.getStorageService().getCachedInventory().get(key);
    }

    /**
     * Extracts {@code amount} of the given item/fluid from the player's linked network.
     * Returns true only if the FULL amount was extracted (matching ItemRequirementTask/
     * FluidRequirementTask's own tryConsume contract - a partial drain never happens, either the
     * whole request succeeds or nothing is taken).
     *
     * Less thoroughly verified than the read path above (AE2's extraction/action-source API
     * wasn't confirmed against decompiled 15.0.18 sources the way getCachedInventory was) -
     * worth an in-game smoke test before relying on consume for real quests.
     */
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
