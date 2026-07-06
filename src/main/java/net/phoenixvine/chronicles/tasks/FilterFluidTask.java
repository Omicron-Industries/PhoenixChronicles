package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.phoenixvine.chronicles.filter.FluidFilters;
import net.phoenixvine.chronicles.filter.IFluidFilter;
import net.phoenixvine.chronicles.model.QuestTask;

/**
 * Fluid task backed by a composable {@link IFluidFilter}.
 *
 * Replaces {@code fluid_check} (exact fluid ID only) with a unified task that
 * can express:
 *
 * <pre>
 * // Any water-tagged fluid (including modded variants):
 * filter = tag("minecraft:water")
 *
 * // Exact lava, 1000 mB:
 * filter = exact(new ResourceLocation("minecraft","lava"))
 *
 * // Any thermal fluid:
 * filter = mod("thermal")
 *
 * // Any lava OR any water:
 * filter = anyOf(exact("minecraft:lava"), tag("minecraft:water"))
 * </pre>
 *
 * Quest file NBT shape:
 * 
 * <pre>
 * {
 *   type: "filter_fluid",
 *   amount: 4000,
 *   consume: true,
 *   filter: { filter_type: "tag", tag: "forge:milk" }
 * }
 * </pre>
 *
 * Scans all fluid-handler items in main + offhand inventory.
 */
public class FilterFluidTask extends QuestTask {

    private IFluidFilter filter;
    private int amount;
    private boolean consume;

    public FilterFluidTask(ResourceLocation taskId, Component description,
                           IFluidFilter filter, int amount, boolean consume) {
        super(taskId, description);
        this.filter = filter;
        this.amount = Math.max(1, amount);
        this.consume = consume;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getTotalMatchingFluid(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            var cap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            if (!cap.isPresent()) continue;
            IFluidHandlerItem handler = cap.orElseThrow(IllegalStateException::new);
            for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack fluid = handler.getFluidInTank(i);
                if (!fluid.isEmpty() && filter.test(fluid)) {
                    total += fluid.getAmount();
                    if (total >= amount) return total;
                }
            }
        }
        // Also check offhand
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.isEmpty()) continue;
            var cap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            if (!cap.isPresent()) continue;
            IFluidHandlerItem handler = cap.orElseThrow(IllegalStateException::new);
            for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack fluid = handler.getFluidInTank(i);
                if (!fluid.isEmpty() && filter.test(fluid)) {
                    total += fluid.getAmount();
                    if (total >= amount) return total;
                }
            }
        }
        return total;
    }

    // ── QuestTask ─────────────────────────────────────────────────────────────

    @Override
    public boolean isCompletedFor(Player player) {
        return getTotalMatchingFluid(player) >= amount;
    }

    @Override
    public String getProgressString(Player player) {
        int found = Math.min(getTotalMatchingFluid(player), amount);
        return String.format("%,d / %,d mB", found, amount);
    }

    /**
     * Fluids have no plain-item icon of their own, so shows that fluid's bucket item instead -
     * the standard way modded UIs represent a fluid in an item-icon slot. Previously this
     * returned nothing at all for every fluid task, exact or tag-based.
     */
    @Override
    public ResourceLocation getDisplayItemId() {
        if (filter == null) return null;
        var fluid = filter.getDisplayFluid();
        if (fluid == null) return null;
        var bucket = fluid.getBucket();
        if (bucket == null || bucket == net.minecraft.world.item.Items.AIR) return null;
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(bucket);
    }

    /** Drain {@code amount} mB of matching fluid from inventory items, respecting consume flag. */
    public void tryConsume(Player player) {
        if (!consume) return;
        int remaining = amount;
        var items = player.getInventory().items;
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            var cap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            if (!cap.isPresent()) continue;
            IFluidHandlerItem handler = cap.orElseThrow(IllegalStateException::new);
            // Check the first matching fluid in this handler
            for (int t = 0; t < handler.getTanks() && remaining > 0; t++) {
                FluidStack fluid = handler.getFluidInTank(t);
                if (fluid.isEmpty() || !filter.test(fluid)) continue;
                FluidStack drained = handler.drain(remaining, IFluidHandler.FluidAction.EXECUTE);
                if (!drained.isEmpty()) {
                    remaining -= drained.getAmount();
                    items.set(i, handler.getContainer());
                }
            }
        }
        player.getInventory().setChanged();
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "filter_fluid");
        tag.putInt("amount", amount);
        tag.putBoolean("consume", consume);
        tag.put("filter", filter.serialize());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.amount = Math.max(1, nbt.getInt("amount"));
        this.consume = nbt.getBoolean("consume");
        if (nbt.contains("filter")) this.filter = FluidFilters.deserialize(nbt.getCompound("filter"));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public IFluidFilter getFilter() {
        return filter;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isConsume() {
        return consume;
    }

    public void setFilter(IFluidFilter f) {
        this.filter = f;
    }

    public void setAmount(int a) {
        this.amount = Math.max(1, a);
    }

    public void setConsume(boolean v) {
        this.consume = v;
    }
}
