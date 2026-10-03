package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestTask;

public class StructureTask extends QuestTask {

    private TaskIdMatcher matcher;

    public StructureTask(ResourceLocation taskId, Component description, ResourceLocation structureId) {
        super(taskId, description);
        this.matcher = TaskIdMatcher.of(structureId);
    }

    public StructureTask(ResourceLocation taskId, Component description, String structureSpec) {
        super(taskId, description);
        this.matcher = TaskIdMatcher.parse(structureSpec);
    }

    public ResourceLocation getStructureId() {
        return matcher.firstId();
    }

    public TaskIdMatcher getMatcher() {
        return matcher;
    }

    public String getSpec() {
        return matcher.spec();
    }

    @Override
    public void onTick(Player player) {
        if (player.level().isClientSide || matcher.isEmpty()) return;
        if (isCompletedFor(player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        var structures = serverLevel.structureManager();
        BlockPos pos = player.blockPosition();

        boolean inside = false;
        for (ResourceLocation id : matcher.ids()) {
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
            if (structures.getStructureWithPieceAt(pos, key).isValid()) {
                inside = true;
                break;
            }
        }
        if (!inside) {
            for (ResourceLocation tag : matcher.tags()) {
                if (structures.getStructureWithPieceAt(pos, TagKey.create(Registries.STRUCTURE, tag)).isValid()) {
                    inside = true;
                    break;
                }
            }
        }
        if (inside) {
            TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
        }
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "structure");
        tag.putString("structure_id", matcher.isEmpty() ? "minecraft:village" : matcher.spec());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("structure_id")) {
            TaskIdMatcher parsed = TaskIdMatcher.parse(nbt.getString("structure_id"));
            if (!parsed.isEmpty()) matcher = parsed;
        }
    }
}
