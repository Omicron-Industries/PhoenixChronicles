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

public class BlockBreakTask extends QuestTask {

    private Block targetBlock;
    private int required;

    public BlockBreakTask(ResourceLocation taskId, Component description, Block targetBlock, int required) {
        super(taskId, description);
        this.targetBlock = targetBlock;
        this.required = Math.max(1, required);
    }

    public Block getTargetBlock() {
        return targetBlock;
    }

    public int getRequired() {
        return required;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getInt("count") >= required;
    }

    public void onBlockBroken(Player player, Block broken) {
        if (targetBlock == null || broken != targetBlock) return;
        TaskProgressAccess.with(player, getTaskId(), prog -> {
            int current = prog.getInt("count");
            if (current < required) {
                prog.putInt("count", current + 1);
            }
        });
    }

    public int getProgress(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getInt("count");
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "block_break");
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(targetBlock);
        tag.putString("block_id", id != null ? id.toString() : "minecraft:air");
        tag.putInt("required", required);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("block_id")) {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(nbt.getString("block_id")));
            this.targetBlock = b != null ? b : Blocks.AIR;
        }
        if (nbt.contains("required")) this.required = Math.max(1, nbt.getInt("required"));
    }
}

