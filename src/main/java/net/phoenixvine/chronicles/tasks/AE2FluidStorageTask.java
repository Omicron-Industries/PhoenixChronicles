package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;
import net.phoenixvine.chronicles.model.QuestTask;

public class AE2FluidStorageTask extends QuestTask {

    private ResourceLocation fluidId;
    private long requiredAmount;
    private boolean consume;

    private boolean sticky = true;

    public AE2FluidStorageTask(ResourceLocation taskId, Component description, ResourceLocation fluidId,
                               long requiredAmount, boolean consume) {
        super(taskId, description);
        this.fluidId = fluidId;
        this.requiredAmount = requiredAmount;
        this.consume = consume;
    }

    public ResourceLocation getFluidId() {
        return fluidId;
    }

    public long getRequiredAmount() {
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

    private Fluid resolveFluid() {
        return fluidId == null ? null : ForgeRegistries.FLUIDS.getValue(fluidId);
    }

    @Override
    public boolean isCompletedFor(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        Fluid fluid = resolveFluid();
        if (fluid == null || requiredAmount <= 0 || !AE2Compat.isAvailable()) return false;
        if (AE2Compat.getStoredAmount(player, fluid) >= requiredAmount) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        return false;
    }

    @Override
    public void tryConsume(Player player) {
        Fluid fluid = resolveFluid();
        if (fluid == null || !consume || requiredAmount <= 0 || !AE2Compat.isAvailable()) return;
        AE2Compat.tryConsume(player, fluid, requiredAmount);
    }

    @Override
    public String getProgressString(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed"))
            return String.format("%,d / %,d mB", requiredAmount, requiredAmount);
        Fluid fluid = resolveFluid();
        long found = fluid == null || !AE2Compat.isAvailable() ? 0 :
                Math.min(AE2Compat.getStoredAmount(player, fluid), requiredAmount);
        return String.format("%,d / %,d mB", found, requiredAmount);
    }

    @Override
    public ResourceLocation getDisplayItemId() {
        Fluid fluid = resolveFluid();
        if (fluid == null) return null;
        var bucket = fluid.getBucket();
        if (bucket == null || bucket == Items.AIR) return null;
        return ForgeRegistries.ITEMS.getKey(bucket);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "ae2_fluid_storage");
        tag.putString("fluid_id", fluidId != null ? fluidId.toString() : "minecraft:empty");
        tag.putLong("amount", requiredAmount);
        tag.putBoolean("consume", consume);
        tag.putBoolean("sticky", sticky);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("fluid_id")) {
            this.fluidId = new ResourceLocation(nbt.getString("fluid_id"));
        }
        this.requiredAmount = nbt.getLong("amount");
        this.consume = nbt.getBoolean("consume");
        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
    }
}
