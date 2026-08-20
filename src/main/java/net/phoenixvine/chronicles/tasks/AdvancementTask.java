package net.phoenixvine.chronicles.tasks;

import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class AdvancementTask extends QuestTask {

    private ResourceLocation advancementId;

    public AdvancementTask(ResourceLocation taskId, Component description, ResourceLocation advancementId) {
        super(taskId, description);
        this.advancementId = advancementId;
    }

    public ResourceLocation getAdvancementId() {
        return advancementId;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getBoolean("completed");
    }

    public void onAdvancementEarned(Player player, ResourceLocation earnedId) {
        if (advancementId == null || !advancementId.equals(earnedId)) return;
        TaskProgressAccess.with(player, this.getTaskId(), nbt -> nbt.putBoolean("completed", true));
    }

    @Override
    public void onTick(Player player) {
        if (advancementId == null || !(player instanceof ServerPlayer serverPlayer)) return;
        if (TaskProgressAccess.getOrEmpty(player, this.getTaskId()).getBoolean("completed")) return;

        Advancement adv = serverPlayer.getServer().getAdvancements().getAdvancement(advancementId);
        if (adv != null && serverPlayer.getAdvancements().getOrStartProgress(adv).isDone()) {
            TaskProgressAccess.with(player, this.getTaskId(), nbt -> nbt.putBoolean("completed", true));
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "advancement");
        tag.putString("advancement_id", advancementId != null ? advancementId.toString() : "minecraft:story/root");

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("advancement_id")) {
            this.advancementId = ResourceLocation.parse(nbt.getString("advancement_id"));
        }
    }
}
