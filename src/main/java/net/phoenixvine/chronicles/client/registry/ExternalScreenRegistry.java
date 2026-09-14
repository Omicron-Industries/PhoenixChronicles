package net.phoenixvine.chronicles.client.registry;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.model.QuestNode;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class ExternalScreenRegistry {

    private static final Map<ResourceLocation, Function<QuestNode, Screen>> REGISTRY = new LinkedHashMap<>();

    public static void register(ResourceLocation id, Function<QuestNode, Screen> factory) {
        REGISTRY.put(id, factory);
    }

    public static boolean isRegistered(@Nullable ResourceLocation id) {
        return id != null && REGISTRY.containsKey(id);
    }

    public static @Nullable Screen open(ResourceLocation id, QuestNode node) {
        Function<QuestNode, Screen> factory = REGISTRY.get(id);
        return factory != null ? factory.apply(node) : null;
    }

    private ExternalScreenRegistry() {}
}
