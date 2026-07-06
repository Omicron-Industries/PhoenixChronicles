package net.phoenixvine.chronicles.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.model.QuestState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class PlayerQuestData {

    // Quest state per quest id
    private final Map<ResourceLocation, QuestState> questStates = new HashMap<>();
    // Task-specific progress blobs
    private final Map<ResourceLocation, CompoundTag> taskProgress = new HashMap<>();
    // Last-completed epoch-millis per quest (for cooldown / daily repeat logic)
    private final Map<ResourceLocation, Long> lastCompleted = new HashMap<>();
    // Rewards the player has already claimed (prevents double-granting on reconnect)
    private final Set<ResourceLocation> claimedRewards = new HashSet<>();
    // Which reward index was chosen in a choice group, keyed by quest id
    private final Map<ResourceLocation, Integer> chosenRewardIndex = new HashMap<>();
    // Pinned quests shown stacked on the HUD, in pin order (LinkedHashSet keeps that order
    // stable so the HUD stack doesn't reshuffle every time something else changes).
    private final Set<ResourceLocation> pinnedQuestIds = new LinkedHashSet<>();

    // ── Quest state ───────────────────────────────────────────────────────────

    public QuestState getQuestState(ResourceLocation questId, QuestState defaultState) {
        return questStates.getOrDefault(questId, defaultState);
    }

    public Map<ResourceLocation, QuestState> getAllStates() {
        return java.util.Collections.unmodifiableMap(questStates);
    }

    public void setQuestState(ResourceLocation questId, QuestState state) {
        questStates.put(questId, state);
    }

    // ── Task progress ─────────────────────────────────────────────────────────

    public CompoundTag getOrCreateTaskProgress(ResourceLocation taskId) {
        return taskProgress.computeIfAbsent(taskId, id -> new CompoundTag());
    }

    // ── Repeat / cooldown ─────────────────────────────────────────────────────

    public long getLastCompletedTime(ResourceLocation questId) {
        return lastCompleted.getOrDefault(questId, 0L);
    }

    public void recordCompletion(ResourceLocation questId) {
        lastCompleted.put(questId, System.currentTimeMillis());
    }

    // ── Rewards ───────────────────────────────────────────────────────────────

    public boolean hasClaimedRewards(ResourceLocation questId) {
        return claimedRewards.contains(questId);
    }

    public void markRewardsClaimed(ResourceLocation questId) {
        claimedRewards.add(questId);
    }

    public void clearClaimedRewards(ResourceLocation questId) {
        claimedRewards.remove(questId);
    }

    public int getChosenRewardIndex(ResourceLocation questId) {
        return chosenRewardIndex.getOrDefault(questId, -1);
    }

    public void setChosenRewardIndex(ResourceLocation questId, int index) {
        chosenRewardIndex.put(questId, index);
    }

    public void clearChosenRewardIndex(ResourceLocation questId) {
        chosenRewardIndex.remove(questId);
    }

    /** Wipes all accumulated task progress for a single task (used on repeat reset). */
    public void clearTaskProgress(ResourceLocation taskId) {
        taskProgress.remove(taskId);
    }

    // ── Pinned quests ─────────────────────────────────────────────────────────

    /** All currently pinned quest ids, in the order they were pinned. */
    public Set<ResourceLocation> getPinnedQuestIds() {
        return java.util.Collections.unmodifiableSet(pinnedQuestIds);
    }

    /** Pins or unpins a quest without affecting any other pinned quest. */
    public void togglePin(ResourceLocation id) {
        if (!pinnedQuestIds.remove(id)) pinnedQuestIds.add(id);
    }

    public void pin(ResourceLocation id) {
        pinnedQuestIds.add(id);
    }

    /** Unpins a single quest (e.g. because its node no longer exists), leaving others untouched. */
    public void unpin(ResourceLocation id) {
        pinnedQuestIds.remove(id);
    }

    public boolean isPinned(ResourceLocation questId) {
        return pinnedQuestIds.contains(questId);
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();

        // Quest states
        ListTag questsList = new ListTag();
        questStates.forEach((id, state) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            e.putString("state", state.name());
            questsList.add(e);
        });
        root.put("Quests", questsList);

        // Task progress
        ListTag tasksList = new ListTag();
        taskProgress.forEach((id, tag) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            e.put("progress", tag);
            tasksList.add(e);
        });
        root.put("Tasks", tasksList);

        // Last completed timestamps
        ListTag completedList = new ListTag();
        lastCompleted.forEach((id, time) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            e.putLong("time", time);
            completedList.add(e);
        });
        root.put("LastCompleted", completedList);

        // Claimed rewards
        ListTag claimedList = new ListTag();
        for (ResourceLocation id : claimedRewards) {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            claimedList.add(e);
        }
        root.put("ClaimedRewards", claimedList);

        // Chosen reward indices
        ListTag chosenList = new ListTag();
        chosenRewardIndex.forEach((id, idx) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            e.putInt("index", idx);
            chosenList.add(e);
        });
        root.put("ChosenRewards", chosenList);

        // Pinned quests
        ListTag pinnedList = new ListTag();
        for (ResourceLocation id : pinnedQuestIds) {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            pinnedList.add(e);
        }
        root.put("PinnedQuests", pinnedList);

        return root;
    }

    public void deserializeNBT(CompoundTag root) {
        questStates.clear();
        taskProgress.clear();
        lastCompleted.clear();
        claimedRewards.clear();
        chosenRewardIndex.clear();
        pinnedQuestIds.clear();

        for (int i = 0; i < root.getList("Quests", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("Quests", Tag.TAG_COMPOUND).getCompound(i);
            try {
                questStates.put(new ResourceLocation(e.getString("id")),
                        QuestState.valueOf(e.getString("state")));
            } catch (Exception ignored) {}
        }

        for (int i = 0; i < root.getList("Tasks", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("Tasks", Tag.TAG_COMPOUND).getCompound(i);
            taskProgress.put(new ResourceLocation(e.getString("id")), e.getCompound("progress"));
        }

        for (int i = 0; i < root.getList("LastCompleted", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("LastCompleted", Tag.TAG_COMPOUND).getCompound(i);
            lastCompleted.put(new ResourceLocation(e.getString("id")), e.getLong("time"));
        }

        for (int i = 0; i < root.getList("ClaimedRewards", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("ClaimedRewards", Tag.TAG_COMPOUND).getCompound(i);
            claimedRewards.add(new ResourceLocation(e.getString("id")));
        }

        for (int i = 0; i < root.getList("ChosenRewards", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("ChosenRewards", Tag.TAG_COMPOUND).getCompound(i);
            chosenRewardIndex.put(new ResourceLocation(e.getString("id")), e.getInt("index"));
        }

        for (int i = 0; i < root.getList("PinnedQuests", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("PinnedQuests", Tag.TAG_COMPOUND).getCompound(i);
            try {
                pinnedQuestIds.add(new ResourceLocation(e.getString("id")));
            } catch (Exception ignored) {}
        }
        // Migrate old single-pin saves (pre-multi-pin) so existing playthroughs don't lose their pin.
        if (root.contains("PinnedQuest")) {
            try {
                pinnedQuestIds.add(new ResourceLocation(root.getString("PinnedQuest")));
            } catch (Exception ignored) {}
        }
    }
}
