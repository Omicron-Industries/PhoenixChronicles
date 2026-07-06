package net.phoenixvine.chronicles.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.model.QuestTask;

/**
 * Task: View a multi-machine scene in Phantasia for at least {@code minSeconds} seconds.
 *
 * Mirrors ViewMachineTask but listens to PhantasiaEvents.SceneViewerClose instead.
 * See PhantasiaCompat for the client-side event wiring.
 *
 * SNBT shape:
 * { type: "view_scene",
 * scene_id: "phoenixcore:ore_processing_line",
 * min_seconds: 5.0,
 * description: "Watch the full ore processing walkthrough" }
 *
 * Scenes are typically multi-step and longer; the default min_seconds of 5.0 reflects that.
 */
public class ViewSceneTask extends QuestTask {

    private String sceneId;
    private float minSeconds;

    public ViewSceneTask(ResourceLocation taskId, Component description, String sceneId, float minSeconds) {
        super(taskId, description);
        this.sceneId = sceneId;
        this.minSeconds = minSeconds;
    }

    public String getSceneId() {
        return sceneId;
    }

    public float getMinSeconds() {
        return minSeconds;
    }

    // ── Completion ────────────────────────────────────────────────────────────

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    /** Called from PhantasiaCompat's SceneViewerClose subscriber on the CLIENT. */
    public void markCompletedClient(Player player) {
        TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
    }

    @Override
    public String getProgressString(Player player) {
        return isCompletedFor(player) ? "Viewed" : (minSeconds > 0 ? "View for " + (int) minSeconds + "s" : "View");
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "view_scene");
        tag.putString("scene_id", sceneId != null ? sceneId : "");
        tag.putFloat("min_seconds", minSeconds);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.sceneId = nbt.getString("scene_id");
        this.minSeconds = nbt.contains("min_seconds") ? nbt.getFloat("min_seconds") : 5.0f;
    }
}
