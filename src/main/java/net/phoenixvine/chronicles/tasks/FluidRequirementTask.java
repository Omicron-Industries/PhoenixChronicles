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
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestEngineConfig;

public class FluidRequirementTask extends QuestTask {

    private ResourceLocation fluidId;
    private int requiredAmount;
    private boolean consume; // NEW: Toggle rule variable
    /** See ItemRequirementTask#sticky. */
    private boolean sticky = true;

    public FluidRequirementTask(ResourceLocation taskId, Component description, ResourceLocation fluidId,
                                int requiredAmount, boolean consume) {
        super(taskId, description);
        this.fluidId = fluidId;
        this.requiredAmount = requiredAmount;
        this.consume = consume;
    }

    public ResourceLocation getFluidId() {
        return fluidId;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public boolean shouldConsume() {
        return consume;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    /** See ItemRequirementTask#checksAe2Storage - same rationale, no nbtFilter to worry about here. */
    private boolean checksAe2Storage() {
        return QuestEngineConfig.isAe2StorageForItemFluidTasksEnabled() && AE2Compat.isAvailable();
    }

    /** See ItemRequirementTask#dependsOnInventory for the full rationale. */
    @Override
    public boolean dependsOnInventory() {
        return !checksAe2Storage();
    }

    @Override
    public boolean isCompletedFor(Player player) {
        // Sticky by default - see ItemRequirementTask#isCompletedFor for the full rationale.
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        if (fluidId == null || requiredAmount <= 0) return false;
        long found = checksAe2Storage() ? AE2Compat.getStoredAmount(player, getFluid()) : 0;
        if (found >= requiredAmount) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        if (found + getTotalFluidInInventory(player) >= requiredAmount) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        return false;
    }

    private net.minecraft.world.level.material.Fluid getFluid() {
        return fluidId == null ? null : ForgeRegistries.FLUIDS.getValue(fluidId);
    }

    private int getTotalFluidInInventory(Player player) {
        int totalFound = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;

            var fluidHandlerCap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            if (fluidHandlerCap.isPresent()) {
                IFluidHandlerItem handler = fluidHandlerCap.orElseThrow(IllegalStateException::new);
                for (int i = 0; i < handler.getTanks(); i++) {
                    FluidStack fluidStack = handler.getFluidInTank(i);
                    ResourceLocation currentFluidId = ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid());

                    if (currentFluidId != null && currentFluidId.equals(this.fluidId)) {
                        totalFound += fluidStack.getAmount();
                        if (totalFound >= requiredAmount) return totalFound;
                    }
                }
            }
        }
        return totalFound;
    }

    /**
     * Call this when claiming rewards. It explicitly respects the 'consume' config toggle.
     */
    @Override
    public void tryConsume(Player player) {
        if (fluidId == null || !consume || requiredAmount <= 0) return; // Skip completely if consume is false

        int remainingToDrain = requiredAmount;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || remainingToDrain <= 0) break;

            var fluidHandlerCap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            if (fluidHandlerCap.isPresent()) {
                IFluidHandlerItem handler = fluidHandlerCap.orElseThrow(IllegalStateException::new);

                FluidStack simulatedDrain = handler.drain(remainingToDrain, IFluidHandler.FluidAction.SIMULATE);
                if (simulatedDrain.isEmpty()) continue;

                ResourceLocation drainedId = ForgeRegistries.FLUIDS.getKey(simulatedDrain.getFluid());
                if (drainedId != null && drainedId.equals(this.fluidId)) {
                    FluidStack actualDrain = handler.drain(remainingToDrain, IFluidHandler.FluidAction.EXECUTE);
                    remainingToDrain -= actualDrain.getAmount();

                    // Safely preserves container states/mutations (Drums update levels, Buckets empty)
                    player.getInventory().setItem(i, handler.getContainer());
                }
            }
        }
        player.getInventory().setChanged();
        // Only reaches into the network for whatever physical inventory couldn't cover.
        if (remainingToDrain > 0 && checksAe2Storage()) {
            AE2Compat.tryConsume(player, getFluid(), remainingToDrain);
        }
    }

    @Override
    public String getProgressString(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed"))
            return String.format("%,d / %,d mB", requiredAmount, requiredAmount);
        long found = checksAe2Storage() ? AE2Compat.getStoredAmount(player, getFluid()) : 0;
        found = Math.min(found + getTotalFluidInInventory(player), requiredAmount);
        return String.format("%,d / %,d mB", found, requiredAmount);
    }

    /** See FilterFluidTask's equivalent override - shows the fluid's bucket item as its icon. */
    @Override
    public ResourceLocation getDisplayItemId() {
        if (fluidId == null) return null;
        var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid == null) return null;
        var bucket = fluid.getBucket();
        if (bucket == null || bucket == net.minecraft.world.item.Items.AIR) return null;
        return ForgeRegistries.ITEMS.getKey(bucket);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "fluid_check");
        tag.putString("fluid_id", fluidId != null ? fluidId.toString() : "minecraft:empty");
        tag.putInt("amount", requiredAmount);
        tag.putBoolean("consume", consume); // Save parameter config
        tag.putBoolean("sticky", sticky);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("fluid_id")) {
            this.fluidId = new ResourceLocation(nbt.getString("fluid_id"));
        }
        this.requiredAmount = nbt.getInt("amount");
        this.consume = nbt.getBoolean("consume"); // Load parameter config
        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
    }
}
