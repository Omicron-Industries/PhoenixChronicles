package net.phoenixvine.chronicles.client.registry;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

public final class SeenQuestTracker {

    private static final Set<ResourceLocation> seen = new HashSet<>();

    private SeenQuestTracker() {}

    public static void markSeen(ResourceLocation questId) {
        seen.add(questId);
    }

    public static boolean isSeen(ResourceLocation questId) {
        return seen.contains(questId);
    }

    public static void clear() {
        seen.clear();
    }
}
