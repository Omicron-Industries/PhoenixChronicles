package net.phoenixvine.chronicles.client.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPooledProgress {

    private static final Map<ResourceLocation, CompoundTag> cache = new ConcurrentHashMap<>();

    private ClientPooledProgress() {}

    public static void put(ResourceLocation taskId, CompoundTag tag) {
        cache.put(taskId, tag);
    }

    public static CompoundTag get(ResourceLocation taskId) {
        return cache.getOrDefault(taskId, new CompoundTag());
    }

    public static void clear() {
        cache.clear();
    }
}
