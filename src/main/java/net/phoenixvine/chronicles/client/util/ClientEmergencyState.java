package net.phoenixvine.chronicles.client.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.common.model.EmergencyKit;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientEmergencyState {

    private ClientEmergencyState() {}

    public static final String QUESTS_KEY = "EmergencyQuests";
    public static final String SERVER_NOW_KEY = "EmergencyServerNow";

    private record Info(boolean repeatable, int cooldownSeconds, @Nullable String label,
                        @Nullable EmergencyKit kit) {}

    private static volatile Map<ResourceLocation, Info> quests = Map.of();
    private static volatile long clockOffsetMs = 0;

    public static void update(CompoundTag progressNbt) {
        if (progressNbt == null) return;
        Map<ResourceLocation, Info> next = new HashMap<>();
        for (Tag t : progressNbt.getList(QUESTS_KEY, Tag.TAG_COMPOUND)) {
            if (!(t instanceof CompoundTag entry)) continue;
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id != null) {
                boolean station = entry.contains("kit", Tag.TAG_COMPOUND);
                next.put(id, new Info(entry.getBoolean("repeatable"), Math.max(0, entry.getInt("cooldown_seconds")),
                        station ? entry.getString("label") : null,
                        station ? EmergencyKit.fromNBT(entry.getCompound("kit")) : null));
            }
        }
        quests = next;
        if (progressNbt.contains(SERVER_NOW_KEY)) {
            clockOffsetMs = progressNbt.getLong(SERVER_NOW_KEY) - System.currentTimeMillis();
        }
    }

    public static boolean hasEmergencyItems(ResourceLocation questId) {
        return quests.containsKey(questId);
    }

    public static List<ResourceLocation> stationIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Info> e : quests.entrySet())
            if (e.getValue().kit() != null) ids.add(e.getKey());
        return ids;
    }

    @Nullable
    public static String stationLabel(ResourceLocation id) {
        Info info = quests.get(id);
        return info != null ? info.label() : null;
    }

    @Nullable
    public static EmergencyKit stationKit(ResourceLocation id) {
        Info info = quests.get(id);
        return info != null ? info.kit() : null;
    }

    public static boolean isRepeatable(ResourceLocation questId) {
        Info info = quests.get(questId);
        return info != null && info.repeatable();
    }

    public static int getCooldownSeconds(ResourceLocation questId) {
        Info info = quests.get(questId);
        return info != null ? info.cooldownSeconds() : 0;
    }

    public static long serverNow() {
        return System.currentTimeMillis() + clockOffsetMs;
    }

    public enum State {
        READY,
        COOLDOWN,
        USED
    }

    public record Availability(State state, long remainingMs, long totalMs) {

        public float cooldownProgress() {
            return totalMs > 0 ? Math.max(0f, Math.min(1f, 1f - remainingMs / (float) totalMs)) : 1f;
        }
    }

    public static Availability availability(PlayerQuestData data, ResourceLocation questId) {
        if (!data.hasUsedEmergency(questId)) return new Availability(State.READY, 0, 0);
        if (!isRepeatable(questId)) return new Availability(State.USED, 0, 0);
        long totalMs = getCooldownSeconds(questId) * 1000L;
        long remaining = Math.max(0, data.getEmergencyUsedAt(questId) + totalMs - serverNow());
        return remaining > 0 ? new Availability(State.COOLDOWN, remaining, totalMs) :
                new Availability(State.READY, 0, totalMs);
    }

    public static String compactDuration(long ms) {
        long seconds = Math.max(0, (ms + 999) / 1000);
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds + 59) / 60 + "m";
        return (seconds + 3599) / 3600 + "h";
    }
}
