package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

/**
 * Task: Interact with a terminal or step into a location.
 * Migrated to player-capability data layers to safely isolate multi-player progress states.
 * SNBT shape: { type: "location_terminal", TargetTerminal: "gtceu:sub_terminal_alpha", consume: true }
 */
public class LocationOrTerminalTask extends QuestTask {

    private ResourceLocation targetTerminalId;
    private boolean consume; // NEW: Uniform toggle rule (controls progress resetting on reward claim)

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

    /**
     * Checks if this player has completed this specific location/terminal task.
     */
    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getBoolean("completed");
    }

    /**
     * Checks if the scanned terminal matches our target objective.
     * Passes the acting player context to isolate progression state data safely.
     */
    public void checkTerminalInteraction(Player player, ResourceLocation interactedTerminal) {
        if (this.targetTerminalId != null && this.targetTerminalId.equals(interactedTerminal)) {
            TaskProgressAccess.with(player, this.getTaskId(), taskNbt -> {
                if (!taskNbt.getBoolean("completed")) {
                    taskNbt.putBoolean("completed", true);
                }
            });
        }
    }

    /**
     * Call this when claiming rewards.
     * If 'consume' is true, it wipes the milestone confirmation flag for repeatable/daily terminal cycles.
     */
    @Override
    public void tryConsume(Player player) {
        if (!consume) return; // Retain checked milestone flag state if consumption is disabled

        TaskProgressAccess.with(player, this.getTaskId(), nbt -> nbt.putBoolean("completed", false));
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", "location_terminal");
        nbt.putString("TargetTerminal",
                this.targetTerminalId != null ? this.targetTerminalId.toString() : "minecraft:air");
        nbt.putBoolean("consume", consume); // Save parameter config
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("TargetTerminal")) {
            this.targetTerminalId = new ResourceLocation(nbt.getString("TargetTerminal"));
        }
        this.consume = nbt.getBoolean("consume"); // Load parameter config
    }
}
