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
import java.util.concurrent.ThreadLocalRandom;

public class RewardTable {

    private final String id;
    private final String displayName;
    private final List<WeightedReward> entries;
    private final int pickCount;

    public RewardTable(String id, String displayName, List<WeightedReward> entries, int pickCount) {
        this.id = id != null ? id : "";
        this.displayName = displayName != null ? displayName : "";
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
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
        return Collections.unmodifiableList(
                entries.stream().map(WeightedReward::reward).filter(r -> r != null).toList());
    }

    public List<WeightedReward> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void grant(ServerPlayer player) {
        if (player == null || entries.isEmpty()) return;
        if (pickCount == 0) {
            for (WeightedReward e : entries) {
                if (e != null && e.reward() != null) {
                    e.reward().grant(player);
                }
            }
        } else {
            List<QuestReward> picked = QuestReward.pickWeighted(entries, pickCount, ThreadLocalRandom.current());
            for (QuestReward r : picked) {
                if (r != null) {
                    r.grant(player);
                }
            }
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

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        if (!displayName.isEmpty()) tag.putString("display_name", displayName);
        if (pickCount > 0) tag.putInt("pick", pickCount);
        ListTag list = new ListTag();
        for (WeightedReward e : entries) {
            if (e != null && e.reward() != null) {
                CompoundTag entryTag = e.reward().serializeNBT();
                if (e.weight() != 1) entryTag.putInt("weight", e.weight());
                list.add(entryTag);
            }
        }
        tag.put("rewards", list);
        return tag;
    }

    public static RewardTable deserialize(CompoundTag tag) {
        if (tag == null || !tag.contains("id")) return null;
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
