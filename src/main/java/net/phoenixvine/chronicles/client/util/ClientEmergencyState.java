package net.phoenixvine.chronicles.client.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

public final class ClientEmergencyState {

    private ClientEmergencyState() {}

    public static final String AVAILABLE_KEY = "EmergencyAvailable";
    public static final String REPEATABLE_KEY = "EmergencyRepeatable";
    public static final String COOLDOWN_KEY = "EmergencyCooldownSeconds";
    public static final String SERVER_NOW_KEY = "EmergencyServerNow";

    private static volatile Set<ResourceLocation> available = Set.of();
    private static volatile boolean repeatable = false;
    private static volatile int cooldownSeconds = 0;
    private static volatile long clockOffsetMs = 0;

    public static void update(CompoundTag progressNbt) {
        if (progressNbt == null) return;
        Set<ResourceLocation> next = new HashSet<>();
        for (Tag t : progressNbt.getList(AVAILABLE_KEY, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) next.add(id);
        }
        available = next;
        repeatable = progressNbt.getBoolean(REPEATABLE_KEY);
        cooldownSeconds = Math.max(0, progressNbt.getInt(COOLDOWN_KEY));
        if (progressNbt.contains(SERVER_NOW_KEY)) {
            clockOffsetMs = progressNbt.getLong(SERVER_NOW_KEY) - System.currentTimeMillis();
        }
    }

    public static boolean hasEmergencyItems(ResourceLocation questId) {
        return available.contains(questId);
    }

    public static boolean isRepeatable() {
        return repeatable;
    }

    public static int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public static long serverNow() {
        return System.currentTimeMillis() + clockOffsetMs;
    }
}
