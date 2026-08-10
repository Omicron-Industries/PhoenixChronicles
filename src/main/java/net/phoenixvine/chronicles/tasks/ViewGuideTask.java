package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

public class ViewGuideTask extends QuestTask {

    private String guideId;

    public ViewGuideTask(ResourceLocation taskId, Component description, String guideId) {
        super(taskId, description);
        this.guideId = guideId;
    }

    public String getGuideId() {
        return guideId;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    public void markCompletedClient(Player player) {
        TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
    }

    @Override
    public String getProgressString(Player player) {
        return isCompletedFor(player) ? "Viewed" : "View";
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "view_guide");
        tag.putString("guide_id", guideId != null ? guideId : "");
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.guideId = nbt.getString("guide_id");
    }
}
