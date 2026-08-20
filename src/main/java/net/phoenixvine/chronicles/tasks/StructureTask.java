package net.phoenixvine.chronicles.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class StructureTask extends QuestTask {

    private ResourceLocation structureId;

    public StructureTask(ResourceLocation taskId, Component description, ResourceLocation structureId) {
        super(taskId, description);
        this.structureId = structureId;
    }

    public ResourceLocation getStructureId() {
        return structureId;
    }

    @Override
    public void onTick(Player player) {
        if (player.level().isClientSide || structureId == null) return;
        if (isCompletedFor(player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        var structReg = serverLevel.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        Structure structure = structReg.get(structureId);
        if (structure == null) return;

        var key = structReg.getResourceKey(structure).orElse(null);
        if (key == null) return;

        BlockPos pos = player.blockPosition();
        boolean inside = serverLevel.structureManager()
                .getStructureWithPieceAt(pos, key)
                .isValid();
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
        tag.putString("structure_id", structureId != null ? structureId.toString() : "minecraft:village");
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("structure_id")) structureId = ResourceLocation.parse(nbt.getString("structure_id"));
    }
}
