package net.phoenixvine.chronicles.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.model.QuestState;

import java.util.*;

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
        return Collections.unmodifiableMap(questStates);
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
        return Collections.unmodifiableSet(chosenRewardIndices.getOrDefault(questId, Set.of()));
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
        return Optional.ofNullable(resolvedChoiceBoxes.get(questId))
                .map(boxes -> boxes.getOrDefault(boxIndex, -1))
                .orElse(-1);
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

    public void resetQuestProgress(ResourceLocation questId, Collection<ResourceLocation> taskIds) {
        questStates.remove(questId);
        taskIds.forEach(taskProgress::remove);
        lastCompleted.remove(questId);
        claimedRewards.remove(questId);
        chosenRewardIndices.remove(questId);
        resolvedChoiceBoxes.remove(questId);
    }

    public Set<ResourceLocation> getPinnedQuestIds() {
        return Collections.unmodifiableSet(pinnedQuestIds);
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
        var root = new CompoundTag();

        var questsList = new ListTag();
        questStates.forEach((id, state) -> {
            var e = new CompoundTag();
            e.putString("id", id.toString());
            e.putString("state", state.name());
            questsList.add(e);
        });
        root.put("Quests", questsList);

        var tasksList = new ListTag();
        taskProgress.forEach((id, tag) -> {
            var e = new CompoundTag();
            e.putString("id", id.toString());
            e.put("progress", tag);
            tasksList.add(e);
        });
        root.put("Tasks", tasksList);

        var completedList = new ListTag();
        lastCompleted.forEach((id, time) -> {
            var e = new CompoundTag();
            e.putString("id", id.toString());
            e.putLong("time", time);
            completedList.add(e);
        });
        root.put("LastCompleted", completedList);

        var claimedList = new ListTag();
        for (var id : claimedRewards) {
            var e = new CompoundTag();
            e.putString("id", id.toString());
            claimedList.add(e);
        }
        root.put("ClaimedRewards", claimedList);

        var chosenList = new ListTag();
        chosenRewardIndices.forEach((id, indices) -> {
            var e = new CompoundTag();
            e.putString("id", id.toString());
            e.putIntArray("indices", indices.stream().mapToInt(Integer::intValue).toArray());
            chosenList.add(e);
        });
        root.put("ChosenRewards", chosenList);

        var boxesList = new ListTag();
        resolvedChoiceBoxes.forEach((id, boxes) -> {
            var e = new CompoundTag();
            e.putString("id", id.toString());
            var entries = new ListTag();
            boxes.forEach((boxIndex, optionIndex) -> {
                var entry = new CompoundTag();
                entry.putInt("box", boxIndex);
                entry.putInt("option", optionIndex);
                entries.add(entry);
            });
            e.put("boxes", entries);
            boxesList.add(e);
        });
        root.put("ResolvedChoiceBoxes", boxesList);

        var pinnedList = new ListTag();
        for (var id : pinnedQuestIds) {
            var e = new CompoundTag();
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

        readCompoundList(root, "Quests", tag -> {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id != null) {
                try {
                    questStates.put(id, QuestState.valueOf(tag.getString("state")));
                } catch (IllegalArgumentException ignored) {}
            }
        });

        readCompoundList(root, "Tasks", tag -> {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id != null) {
                taskProgress.put(id, tag.getCompound("progress"));
            }
        });

        readCompoundList(root, "LastCompleted", tag -> {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id != null) {
                lastCompleted.put(id, tag.getLong("time"));
            }
        });

        readCompoundList(root, "ClaimedRewards", tag -> {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id != null) {
                claimedRewards.add(id);
            }
        });

        readCompoundList(root, "ChosenRewards", tag -> {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id != null) {
                Set<Integer> indices = new HashSet<>();
                if (tag.contains("indices")) {
                    for (int idx : tag.getIntArray("indices")) indices.add(idx);
                } else if (tag.contains("index")) {
                    indices.add(tag.getInt("index"));
                }
                if (!indices.isEmpty()) chosenRewardIndices.put(id, indices);
            }
        });

        readCompoundList(root, "ResolvedChoiceBoxes", tag -> {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id != null) {
                Map<Integer, Integer> boxes = new HashMap<>();
                readCompoundList(tag, "boxes", entry -> boxes.put(entry.getInt("box"), entry.getInt("option")));
                if (!boxes.isEmpty()) resolvedChoiceBoxes.put(id, boxes);
            }
        });

        readCompoundList(root, "PinnedQuests", tag -> {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id != null) {
                pinnedQuestIds.add(id);
            }
        });

        if (root.contains("PinnedQuest")) {
            ResourceLocation id = ResourceLocation.tryParse(root.getString("PinnedQuest"));
            if (id != null) {
                pinnedQuestIds.add(id);
            }
        }
    }

    private static void readCompoundList(CompoundTag root, String key,
                                         java.util.function.Consumer<CompoundTag> consumer) {
        for (Tag rawTag : root.getList(key, Tag.TAG_COMPOUND)) {
            if (rawTag instanceof CompoundTag compound) {
                consumer.accept(compound);
            }
        }
    }
}
