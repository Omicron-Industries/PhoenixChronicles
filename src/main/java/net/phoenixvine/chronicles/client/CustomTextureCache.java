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

/**
 * Resolves "phoenixcore:textures/custom/&lt;relative-path&gt;" resource locations - the RL format
 * TextureBrowserScreen hands out for pack-dev-dropped PNGs under
 * config/phoenix_chronicles/textures/ - into an actually-renderable texture.
 *
 * Those files were never renderable on their own: Minecraft's ResourceManager only loads assets
 * from assets/&lt;namespace&gt;/... inside a jar or resource pack, never from config/. Every caller
 * that blitted one of these resource locations directly (CategoryConfig's CUSTOM background style,
 * quest description [img:] tags) was therefore always drawing the missing-texture checker instead
 * of the picture a pack dev picked in the browser - the picker looked like it worked (its own grid
 * thumbnails go through this same resolve() call) but the choice silently failed everywhere else.
 *
 * Mirrors QuestIconCache's already-working dynamic-texture-registration pattern for per-quest
 * icons, generalized to any RL under the "custom" bucket instead of one fixed quest-icon naming
 * scheme.
 */
public final class CustomTextureCache {

    private static final String CUSTOM_PREFIX = "textures/custom/";

    private static final Map<String, ResourceLocation> RESOLVED = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private CustomTextureCache() {}

    /**
     * Registers the on-disk PNG backing this resource location with Minecraft's TextureManager
     * (once, cached thereafter) and returns the same RL, now actually bindable. Resource
     * locations outside the "custom" bucket (built-in phoenixcore:textures/gui/... assets, or
     * anything from another mod/resource pack) pass through unchanged - those are already real
     * bundled resources and don't need dynamic registration.
     */
    public static ResourceLocation resolve(ResourceLocation rl) {
        String key = rl.toString();
        if (RESOLVED.containsKey(key)) return rl;
        if (MISSING.contains(key)) return rl; // let it render as the missing-texture checker, don't retry every frame

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

    /** Force a specific custom texture to be re-read from disk (e.g. after a pack dev overwrites the file). */
    public static void invalidate(ResourceLocation rl) {
        String key = rl.toString();
        if (RESOLVED.remove(key) != null) Minecraft.getInstance().getTextureManager().release(rl);
        MISSING.remove(key);
    }

    /** Release every dynamically-registered custom texture — call on world unload. */
    public static void invalidateAll() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : RESOLVED.values()) mc.getTextureManager().release(loc);
        RESOLVED.clear();
        MISSING.clear();
    }
}
