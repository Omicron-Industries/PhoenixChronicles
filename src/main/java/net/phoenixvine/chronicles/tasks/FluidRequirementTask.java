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
import net.phoenixvine.chronicles.integration.curios.CuriosCompat;
import net.phoenixvine.chronicles.model.QuestTask;

import java.util.ArrayList;
import java.util.List;

public class FluidRequirementTask extends QuestTask {

    private ResourceLocation fluidId;
    private int requiredAmount;
    private boolean consume;

    private CompoundTag nbtFilter = null;

    private boolean sticky = true;

    private boolean checkAe2Storage = AE2Compat.isAvailable();

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

    public CompoundTag getNbtFilter() {
        return nbtFilter;
    }

    public void setNbtFilter(CompoundTag filter) {
        this.nbtFilter = filter;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public boolean isCheckAe2Storage() {
        return checkAe2Storage;
    }

    public void setCheckAe2Storage(boolean checkAe2Storage) {
        this.checkAe2Storage = checkAe2Storage;
    }

    private boolean fluidMatches(FluidStack fs) {
        if (fs.isEmpty()) return false;
        ResourceLocation candidateId = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
        if (candidateId == null || !candidateId.equals(fluidId)) return false;
        return net.phoenixvine.chronicles.filter.NbtMatchUtil.matches(fs.getTag(), nbtFilter);
    }

    private boolean checksAe2Storage() {
        return checkAe2Storage && (nbtFilter == null || nbtFilter.isEmpty()) && AE2Compat.isAvailable();
    }

    @Override
    public boolean dependsOnInventory() {
        return !checksAe2Storage();
    }

    @Override
    public boolean isCompletedFor(Player player) {
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

    private Iterable<ItemStack> fluidContainerSlots(Player player) {
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.addAll(CuriosCompat.getEquippedCurios(player));
        return all;
    }

    private int getTotalFluidInInventory(Player player) {
        int totalFound = 0;
        for (ItemStack stack : fluidContainerSlots(player)) {
            if (stack.isEmpty()) continue;

            var fluidHandlerCap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            if (fluidHandlerCap.isPresent()) {
                IFluidHandlerItem handler = fluidHandlerCap.orElseThrow(IllegalStateException::new);
                for (int i = 0; i < handler.getTanks(); i++) {
                    FluidStack fluidStack = handler.getFluidInTank(i);

                    if (fluidMatches(fluidStack)) {
                        totalFound += fluidStack.getAmount();
                        if (totalFound >= requiredAmount) return totalFound;
                    }
                }
            }
        }
        return totalFound;
    }

    @Override
    public void tryConsume(Player player) {
        if (fluidId == null || !consume || requiredAmount <= 0) return;

        int remainingToDrain = requiredAmount;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || remainingToDrain <= 0) break;

            var fluidHandlerCap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            if (fluidHandlerCap.isPresent()) {
                IFluidHandlerItem handler = fluidHandlerCap.orElseThrow(IllegalStateException::new);

                FluidStack simulatedDrain = handler.drain(remainingToDrain, IFluidHandler.FluidAction.SIMULATE);
                if (simulatedDrain.isEmpty()) continue;

                if (fluidMatches(simulatedDrain)) {
                    FluidStack actualDrain = handler.drain(remainingToDrain, IFluidHandler.FluidAction.EXECUTE);
                    remainingToDrain -= actualDrain.getAmount();

                    player.getInventory().setItem(i, handler.getContainer());
                }
            }
        }
        player.getInventory().setChanged();

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
        tag.putBoolean("consume", consume);
        tag.putBoolean("sticky", sticky);
        tag.putBoolean("check_ae2_storage", checkAe2Storage);
        if (nbtFilter != null && !nbtFilter.isEmpty()) tag.put("nbt_filter", nbtFilter);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("nbt_filter")) this.nbtFilter = nbt.getCompound("nbt_filter");
        if (nbt.contains("fluid_id")) {
            this.fluidId = new ResourceLocation(nbt.getString("fluid_id"));
        }
        this.requiredAmount = nbt.getInt("amount");
        this.consume = nbt.getBoolean("consume");
        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
        this.checkAe2Storage = !nbt.contains("check_ae2_storage") || nbt.getBoolean("check_ae2_storage");
    }
}
