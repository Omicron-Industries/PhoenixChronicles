package net.phoenixvine.chronicles.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.chronicles.model.QuestReward.WeightedReward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RewardTable {

    private static final Random RANDOM = new Random();

    private final String id;
    private final String displayName;
    private final List<WeightedReward> entries;
    private final int pickCount;

    public RewardTable(String id, String displayName, List<WeightedReward> entries, int pickCount) {
        this.id = id;
        this.displayName = displayName;
        this.entries = new ArrayList<>(entries);
        this.pickCount = Math.max(0, pickCount);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName.isEmpty() ? id : displayName;
    }

    public int getPickCount() {
        return pickCount;
    }

    public List<QuestReward> getRewards() {
        return Collections.unmodifiableList(entries.stream().map(WeightedReward::reward).toList());
    }

    public List<WeightedReward> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void grant(ServerPlayer player) {
        if (entries.isEmpty()) return;
        if (pickCount <= 0 || pickCount >= entries.size()) {
            for (WeightedReward e : entries) e.reward().grant(player);
        } else {
            for (QuestReward r : QuestReward.pickWeighted(entries, pickCount, RANDOM)) r.grant(player);
        }
    }

    public Component getSummary() {
        String name = getDisplayName();
        if (pickCount > 0) {
            return Component.literal("Table: " + name + " (" + pickCount + "/" + entries.size() + " weighted random)");
        }
        return Component
                .literal("Table: " + name + " (" + entries.size() + " reward" + (entries.size() == 1 ? "" : "s") + ")");
    }

    public static RewardTable deserialize(CompoundTag tag) {
        String id = tag.getString("id");
        if (id.isBlank()) return null;
        String displayName = tag.contains("display_name") ? tag.getString("display_name") : "";
        int pickCount = tag.contains("pick") ? tag.getInt("pick") : 0;
        List<WeightedReward> entries = new ArrayList<>();
        if (tag.contains("rewards")) {
            ListTag list = tag.getList("rewards", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                QuestReward r = QuestReward.deserializeNBT(entryTag);
                if (r != null) {
                    int weight = entryTag.contains("weight") ? entryTag.getInt("weight") : 1;
                    entries.add(new WeightedReward(r, weight));
                }
            }
        }
        return new RewardTable(id, displayName, entries, pickCount);
    }
}
