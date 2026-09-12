package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestTask;

/**
 * Completes when the player views a specific Phoenix Archive lore entry -- the Chronicles-side half
 * of the Archive/Chronicles deep link (Archive's own "chronicles_quest" condition + View Lore menu
 * item is the reverse direction). Structurally a copy of {@link ViewGuideTask} (same sticky-complete-
 * on-view semantics, same NBT shape), just pointing at an Archive entry id instead of a Phantasia
 * guide id.
 */
public class ArchiveEntryTask extends QuestTask {

    private String archiveEntryId;

    public ArchiveEntryTask(ResourceLocation taskId, Component description, String archiveEntryId) {
        super(taskId, description);
        this.archiveEntryId = archiveEntryId;
    }

    public String getArchiveEntryId() {
        return archiveEntryId;
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
        tag.putString("type", "archive_entry");
        tag.putString("archive_entry_id", archiveEntryId != null ? archiveEntryId : "");
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.archiveEntryId = nbt.getString("archive_entry_id");
    }
}
