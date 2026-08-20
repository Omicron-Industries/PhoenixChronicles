package net.phoenixvine.chronicles.registry;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.render.line.IDependencyLineStyle;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DependencyLineStyleRegistry {

    private static final Map<ResourceLocation, IDependencyLineStyle> REGISTRY = new LinkedHashMap<>();

    public static void register(ResourceLocation id, IDependencyLineStyle style) {
        REGISTRY.put(id, style);
    }

    @Nullable
    public static IDependencyLineStyle get(ResourceLocation id) {
        return id != null ? REGISTRY.get(id) : null;
    }

    public static boolean isRegistered(ResourceLocation id) {
        return id != null && REGISTRY.containsKey(id);
    }

    public static Map<ResourceLocation, IDependencyLineStyle> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    private static boolean builtinsRegistered = false;

    public static void registerBuiltins() {
        if (builtinsRegistered) return;
        builtinsRegistered = true;

        register(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "solid"),
                new net.phoenixvine.chronicles.client.render.line.SolidLineStyle());
        register(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "textured"),
                new net.phoenixvine.chronicles.client.render.line.TexturedLineStyle(
                        ResourceLocation.fromNamespaceAndPath("phoenix_chronicles",
                                "textures/gui/sprites/dep_line_dashed.png"),
                        5, 1, 4f));
        register(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "flowing_particles"),
                new net.phoenixvine.chronicles.client.render.line.FlowingParticleLineStyle());
    }

    private DependencyLineStyleRegistry() {}
}
