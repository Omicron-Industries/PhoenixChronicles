package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CustomTextureCache {

    private static final String CUSTOM_PREFIX = "textures/custom/";

    private static final Map<String, ResourceLocation> RESOLVED = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private CustomTextureCache() {}

    public static ResourceLocation resolve(ResourceLocation rl) {
        String key = rl.toString();
        if (RESOLVED.containsKey(key)) return rl;
        if (MISSING.contains(key)) return rl; 

        if (!"phoenixcore".equals(rl.getNamespace()) || !rl.getPath().startsWith(CUSTOM_PREFIX)) {
            return rl;
        }

        String rel = rl.getPath().substring(CUSTOM_PREFIX.length());
        Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("textures").resolve(rel);
        if (!Files.exists(file)) {
            MISSING.add(key);
            return rl;
        }

        try (InputStream is = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(is);
            DynamicTexture tex = new DynamicTexture(img);
            Minecraft.getInstance().getTextureManager().register(rl, tex);
            RESOLVED.put(key, rl);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to load custom texture '" + rel + "': " + e.getMessage());
            MISSING.add(key);
        }
        return rl;
    }

    public static void invalidate(ResourceLocation rl) {
        String key = rl.toString();
        if (RESOLVED.remove(key) != null) Minecraft.getInstance().getTextureManager().release(rl);
        MISSING.remove(key);
    }

    public static void invalidateAll() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : RESOLVED.values()) mc.getTextureManager().release(loc);
        RESOLVED.clear();
        MISSING.clear();
    }
}

