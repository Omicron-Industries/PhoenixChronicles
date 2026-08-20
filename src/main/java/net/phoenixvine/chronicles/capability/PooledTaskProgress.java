package net.phoenixvine.chronicles.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class PooledTaskProgress extends SavedData {

    private static final String NAME = "phoenix_chronicles_pooled_progress";

    private final Map<String, Map<ResourceLocation, CompoundTag>> byTeam = new HashMap<>();

    public static PooledTaskProgress get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PooledTaskProgress::load, PooledTaskProgress::new, NAME);
    }

    public CompoundTag getOrCreate(String teamKey, ResourceLocation taskId) {
        setDirty();
        return byTeam.computeIfAbsent(teamKey, k -> new HashMap<>())
                .computeIfAbsent(taskId, id -> new CompoundTag());
    }

    public void clearTaskProgress(ResourceLocation taskId) {
        boolean changed = false;
        for (Map<ResourceLocation, CompoundTag> teamMap : byTeam.values()) {
            if (teamMap.remove(taskId) != null) changed = true;
        }
        if (changed) setDirty();
    }

    public static PooledTaskProgress load(CompoundTag root) {
        PooledTaskProgress data = new PooledTaskProgress();
        ListTag teams = root.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teams.size(); i++) {
            CompoundTag teamEntry = teams.getCompound(i);
            String teamKey = teamEntry.getString("team");
            Map<ResourceLocation, CompoundTag> tasks = new HashMap<>();
            ListTag taskList = teamEntry.getList("tasks", Tag.TAG_COMPOUND);
            for (int j = 0; j < taskList.size(); j++) {
                CompoundTag e = taskList.getCompound(j);
                try {
                    tasks.put(ResourceLocation.parse(e.getString("id")), e.getCompound("progress"));
                } catch (Exception ignored) {}
            }
            data.byTeam.put(teamKey, tasks);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag teams = new ListTag();
        byTeam.forEach((teamKey, tasks) -> {
            CompoundTag teamEntry = new CompoundTag();
            teamEntry.putString("team", teamKey);
            ListTag taskList = new ListTag();
            tasks.forEach((taskId, progress) -> {
                CompoundTag e = new CompoundTag();
                e.putString("id", taskId.toString());
                e.put("progress", progress);
                taskList.add(e);
            });
            teamEntry.put("tasks", taskList);
            teams.add(teamEntry);
        });
        root.put("Teams", teams);
        return root;
    }
}
