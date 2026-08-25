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

    private final Map<ResourceLocation, QuestState> questStates = new HashMap<>();

    private final Map<ResourceLocation, CompoundTag> taskProgress = new HashMap<>();

    private final Map<ResourceLocation, Long> lastCompleted = new HashMap<>();

    private final Set<ResourceLocation> claimedRewards = new HashSet<>();

    private final Map<ResourceLocation, Set<Integer>> chosenRewardIndices = new HashMap<>();

    private final Map<ResourceLocation, Map<Integer, Integer>> resolvedChoiceBoxes = new HashMap<>();

    private final Set<ResourceLocation> pinnedQuestIds = new LinkedHashSet<>();

    public QuestState getQuestState(ResourceLocation questId, QuestState defaultState) {
        return questStates.getOrDefault(questId, defaultState);
    }

    public Map<ResourceLocation, QuestState> getAllStates() {
        return java.util.Collections.unmodifiableMap(questStates);
    }

    public void setQuestState(ResourceLocation questId, QuestState state) {
        questStates.put(questId, state);
    }

    public CompoundTag getOrCreateTaskProgress(ResourceLocation taskId) {
        return taskProgress.computeIfAbsent(taskId, id -> new CompoundTag());
    }

    public long getLastCompletedTime(ResourceLocation questId) {
        return lastCompleted.getOrDefault(questId, 0L);
    }

    public void recordCompletion(ResourceLocation questId) {
        lastCompleted.put(questId, System.currentTimeMillis());
    }

    public boolean hasClaimedRewards(ResourceLocation questId) {
        return claimedRewards.contains(questId);
    }

    public void markRewardsClaimed(ResourceLocation questId) {
        claimedRewards.add(questId);
    }

    public void clearClaimedRewards(ResourceLocation questId) {
        claimedRewards.remove(questId);
    }

    public Set<Integer> getChosenRewardIndices(ResourceLocation questId) {
        return java.util.Collections.unmodifiableSet(chosenRewardIndices.getOrDefault(questId, Set.of()));
    }

    public boolean hasChosenRewardIndex(ResourceLocation questId, int index) {
        return chosenRewardIndices.getOrDefault(questId, Set.of()).contains(index);
    }

    public void addChosenRewardIndex(ResourceLocation questId, int index) {
        chosenRewardIndices.computeIfAbsent(questId, id -> new HashSet<>()).add(index);
    }

    public void clearChosenRewardIndices(ResourceLocation questId) {
        chosenRewardIndices.remove(questId);
    }

    public boolean isChoiceBoxResolved(ResourceLocation questId, int boxIndex) {
        Map<Integer, Integer> boxes = resolvedChoiceBoxes.get(questId);
        return boxes != null && boxes.containsKey(boxIndex);
    }

    public int getResolvedChoiceBoxOption(ResourceLocation questId, int boxIndex) {
        Map<Integer, Integer> boxes = resolvedChoiceBoxes.get(questId);
        return boxes == null ? -1 : boxes.getOrDefault(boxIndex, -1);
    }

    public void resolveChoiceBox(ResourceLocation questId, int boxIndex, int optionIndex) {
        resolvedChoiceBoxes.computeIfAbsent(questId, id -> new HashMap<>()).put(boxIndex, optionIndex);
    }

    public void clearChoiceBoxes(ResourceLocation questId) {
        resolvedChoiceBoxes.remove(questId);
    }

    public void clearTaskProgress(ResourceLocation taskId) {
        taskProgress.remove(taskId);
    }

    public void resetQuestProgress(ResourceLocation questId, java.util.Collection<ResourceLocation> taskIds) {
        questStates.remove(questId);
        for (ResourceLocation taskId : taskIds) taskProgress.remove(taskId);
        lastCompleted.remove(questId);
        claimedRewards.remove(questId);
        chosenRewardIndices.remove(questId);
        resolvedChoiceBoxes.remove(questId);
    }

    public Set<ResourceLocation> getPinnedQuestIds() {
        return java.util.Collections.unmodifiableSet(pinnedQuestIds);
    }

    public void togglePin(ResourceLocation id) {
        if (!pinnedQuestIds.remove(id)) pinnedQuestIds.add(id);
    }

    public void pin(ResourceLocation id) {
        pinnedQuestIds.add(id);
    }

    public void unpin(ResourceLocation id) {
        pinnedQuestIds.remove(id);
    }

    public boolean isPinned(ResourceLocation questId) {
        return pinnedQuestIds.contains(questId);
    }

    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();

        ListTag questsList = new ListTag();
        questStates.forEach((id, state) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            e.putString("state", state.name());
            questsList.add(e);
        });
        root.put("Quests", questsList);

        ListTag tasksList = new ListTag();
        taskProgress.forEach((id, tag) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            e.put("progress", tag);
            tasksList.add(e);
        });
        root.put("Tasks", tasksList);

        ListTag completedList = new ListTag();
        lastCompleted.forEach((id, time) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            e.putLong("time", time);
            completedList.add(e);
        });
        root.put("LastCompleted", completedList);

        ListTag claimedList = new ListTag();
        for (ResourceLocation id : claimedRewards) {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            claimedList.add(e);
        }
        root.put("ClaimedRewards", claimedList);

        ListTag chosenList = new ListTag();
        chosenRewardIndices.forEach((id, indices) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            e.putIntArray("indices", indices.stream().mapToInt(Integer::intValue).toArray());
            chosenList.add(e);
        });
        root.put("ChosenRewards", chosenList);

        ListTag boxesList = new ListTag();
        resolvedChoiceBoxes.forEach((id, boxes) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            ListTag entries = new ListTag();
            boxes.forEach((boxIndex, optionIndex) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("box", boxIndex);
                entry.putInt("option", optionIndex);
                entries.add(entry);
            });
            e.put("boxes", entries);
            boxesList.add(e);
        });
        root.put("ResolvedChoiceBoxes", boxesList);

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
        chosenRewardIndices.clear();
        resolvedChoiceBoxes.clear();
        pinnedQuestIds.clear();

        for (int i = 0; i < root.getList("Quests", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("Quests", Tag.TAG_COMPOUND).getCompound(i);
            try {
                questStates.put(ResourceLocation.parse(e.getString("id")),
                        QuestState.valueOf(e.getString("state")));
            } catch (Exception ignored) {}
        }

        for (int i = 0; i < root.getList("Tasks", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("Tasks", Tag.TAG_COMPOUND).getCompound(i);
            taskProgress.put(ResourceLocation.parse(e.getString("id")), e.getCompound("progress"));
        }

        for (int i = 0; i < root.getList("LastCompleted", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("LastCompleted", Tag.TAG_COMPOUND).getCompound(i);
            lastCompleted.put(ResourceLocation.parse(e.getString("id")), e.getLong("time"));
        }

        for (int i = 0; i < root.getList("ClaimedRewards", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("ClaimedRewards", Tag.TAG_COMPOUND).getCompound(i);
            claimedRewards.add(ResourceLocation.parse(e.getString("id")));
        }

        for (int i = 0; i < root.getList("ChosenRewards", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("ChosenRewards", Tag.TAG_COMPOUND).getCompound(i);
            Set<Integer> indices = new HashSet<>();
            if (e.contains("indices")) {
                for (int idx : e.getIntArray("indices")) indices.add(idx);
            } else if (e.contains("index")) {

                indices.add(e.getInt("index"));
            }
            if (!indices.isEmpty()) chosenRewardIndices.put(ResourceLocation.parse(e.getString("id")), indices);
        }

        for (int i = 0; i < root.getList("ResolvedChoiceBoxes", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("ResolvedChoiceBoxes", Tag.TAG_COMPOUND).getCompound(i);
            Map<Integer, Integer> boxes = new HashMap<>();
            ListTag entries = e.getList("boxes", Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag entry = entries.getCompound(j);
                boxes.put(entry.getInt("box"), entry.getInt("option"));
            }
            if (!boxes.isEmpty()) resolvedChoiceBoxes.put(ResourceLocation.parse(e.getString("id")), boxes);
        }

        for (int i = 0; i < root.getList("PinnedQuests", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag e = root.getList("PinnedQuests", Tag.TAG_COMPOUND).getCompound(i);
            try {
                pinnedQuestIds.add(ResourceLocation.parse(e.getString("id")));
            } catch (Exception ignored) {}
        }

        if (root.contains("PinnedQuest")) {
            try {
                pinnedQuestIds.add(ResourceLocation.parse(root.getString("PinnedQuest")));
            } catch (Exception ignored) {}
        }
    }
}
