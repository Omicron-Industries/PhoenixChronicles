package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestTask;

public class BlockInteractTask extends QuestTask {

    private Block targetBlock;
    private String mode;
    private int required = 1;

    public BlockInteractTask(ResourceLocation taskId, Component description, Block targetBlock, String mode,
                             int required) {
        this(taskId, description, targetBlock, mode);
        this.required = Math.max(1, required);
    }

    public int getRequired() {
        return required;
    }

    public BlockInteractTask(ResourceLocation taskId, Component description, Block targetBlock, String mode) {
        super(taskId, description);
        this.targetBlock = targetBlock;
        this.mode = mode.toUpperCase();
    }

    public Block getTargetBlock() {
        return targetBlock;
    }

    public String getMode() {
        return mode;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        CompoundTag progress = TaskProgressAccess.getOrEmpty(player, this.getTaskId());
        return progress.getBoolean("completed") || (required > 1 && progress.getInt("current") >= required);
    }

    @Override
    public String getProgressString(Player player) {
        if (required <= 1) return null;
        int current = TaskProgressAccess.getOrEmpty(player, getTaskId()).getInt("current");
        return Math.min(current, required) + "/" + required;
    }

    public void onBlockEvent(Player player, Block block, String action) {
        if (targetBlock == null || mode == null) return;

        if (block == targetBlock && this.mode.equalsIgnoreCase(action)) {
            TaskProgressAccess.with(player, this.getTaskId(), taskNbt -> {
                if (taskNbt.getBoolean("completed")) return;
                int current = taskNbt.getInt("current") + 1;
                taskNbt.putInt("current", Math.min(current, required));
                if (current >= required) taskNbt.putBoolean("completed", true);
            });
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "block_interact");
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(targetBlock);
        tag.putString("block_id", id != null ? id.toString() : "minecraft:air");
        tag.putString("mode", mode != null ? mode : "PLACE");
        if (required > 1) tag.putInt("required", required);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("block_id")) {
            this.targetBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(nbt.getString("block_id")));
        } else {
            this.targetBlock = Blocks.AIR;
        }
        this.mode = nbt.getString("mode").toUpperCase();
        this.required = nbt.contains("required") ? Math.max(1, nbt.getInt("required")) : 1;
    }
}
