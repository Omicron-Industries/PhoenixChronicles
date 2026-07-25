package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class BlockInteractTask extends QuestTask {

    private Block targetBlock;
    private String mode;

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
        return TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getBoolean("completed");
    }

    public void onBlockEvent(Player player, Block block, String action) {
        if (targetBlock == null || mode == null) return;

        if (block == targetBlock && this.mode.equalsIgnoreCase(action)) {
            TaskProgressAccess.with(player, this.getTaskId(), taskNbt -> {
                if (!taskNbt.getBoolean("completed")) {
                    taskNbt.putBoolean("completed", true);
                }
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

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("block_id")) {
            this.targetBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(nbt.getString("block_id")));
        } else {
            this.targetBlock = Blocks.AIR;
        }
        this.mode = nbt.getString("mode").toUpperCase();
    }
}
