package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

/**
 * Manual checkmark task — completed by an operator via a server command
 * or by another system calling .
 *
 * Useful for "talk to the admin", "attend the event", or "read the lore book" objectives.
 * SNBT shape: { type: "checkmark" }
 *
 * Command to complete: /chronicle task complete <player> <taskId>
 * (wired up in ChronicleCommands)
 */
public class CheckmarkTask extends QuestTask {

    public CheckmarkTask(ResourceLocation taskId, Component description) {
        super(taskId, description);
    }

    /** Called by the server command. */
    public static void complete(Player player, ResourceLocation taskId) {
        TaskProgressAccess.with(player, taskId, nbt -> nbt.putBoolean("completed", true));
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "checkmark");
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // no fields to restore
    }
}
