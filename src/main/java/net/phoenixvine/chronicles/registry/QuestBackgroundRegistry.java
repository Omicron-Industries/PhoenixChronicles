package net.phoenixvine.chronicles.registry;

import net.phoenixvine.chronicles.client.render.IQuestBackground;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestBackgroundRegistry {

    private static final Map<String, IQuestBackground> REGISTRY = new LinkedHashMap<>();

    public static void register(String typeId, IQuestBackground background) {
        REGISTRY.put(typeId, background);
    }

    @Nullable
    public static IQuestBackground get(String typeId) {
        return typeId != null ? REGISTRY.get(typeId) : null;
    }

    public static Map<String, IQuestBackground> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    private static boolean builtinsRegistered = false;

    public static void registerBuiltins() {
        if (builtinsRegistered) return;
        builtinsRegistered = true;

        register("sun", new net.phoenixvine.chronicles.client.render.background.SunBackground(1.4f, 1f));
        register("glitch", new net.phoenixvine.chronicles.client.render.background.GlitchShearBackground(1.4f, 1f));

        register("aurora", net.phoenixvine.chronicles.client.render.background.QuestBackgroundBuilder.create()
                .layer(net.phoenixvine.chronicles.client.render.background.BackgroundEffects
                        .radialGradient(0xFF0A1A22, 0x000A1A22))
                .layer(net.phoenixvine.chronicles.client.render.background.BackgroundEffects
                        .colorCycle(0.15f, 0xFF33FFAA, 0xFF33AAFF, 0xFFAA55FF),
                        0.8f, net.phoenixvine.chronicles.client.render.background.QuestBackgroundBuilder.BlendMode.ADD)
                .layer(net.phoenixvine.chronicles.client.render.background.BackgroundEffects
                        .sparkle(0xFFFFFFFF, 0.5f, 2f), 0.6f)
                .speed(1f)
                .build());
    }

    private QuestBackgroundRegistry() {}
}
