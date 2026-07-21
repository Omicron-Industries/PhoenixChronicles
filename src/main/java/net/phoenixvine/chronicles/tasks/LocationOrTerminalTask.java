package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class LocationOrTerminalTask extends QuestTask {

    private ResourceLocation targetTerminalId;
    private boolean consume; 

    public LocationOrTerminalTask(ResourceLocation taskId, Component description, ResourceLocation targetTerminalId,
                                  boolean consume) {
        super(taskId, description);
        this.targetTerminalId = targetTerminalId;
        this.consume = consume;
    }

    public ResourceLocation getTargetTerminalId() {
        return targetTerminalId;
    }

    public boolean shouldConsume() {
        return consume;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getBoolean("completed");
    }

    public void checkTerminalInteraction(Player player, ResourceLocation interactedTerminal) {
        if (this.targetTerminalId != null && this.targetTerminalId.equals(interactedTerminal)) {
            TaskProgressAccess.with(player, this.getTaskId(), taskNbt -> {
                if (!taskNbt.getBoolean("completed")) {
                    taskNbt.putBoolean("completed", true);
                }
            });
        }
    }

    @Override
    public void tryConsume(Player player) {
        if (!consume) return; 

        TaskProgressAccess.with(player, this.getTaskId(), nbt -> nbt.putBoolean("completed", false));
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", "location_terminal");
        nbt.putString("TargetTerminal",
                this.targetTerminalId != null ? this.targetTerminalId.toString() : "minecraft:air");
        nbt.putBoolean("consume", consume); 
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("TargetTerminal")) {
            this.targetTerminalId = new ResourceLocation(nbt.getString("TargetTerminal"));
        }
        this.consume = nbt.getBoolean("consume"); 
    }
}

