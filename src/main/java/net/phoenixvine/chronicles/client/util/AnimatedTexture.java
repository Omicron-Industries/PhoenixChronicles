package net.phoenixvine.chronicles.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AnimatedTexture {

    private static final String CUSTOM_PREFIX = "textures/custom/";
    private static final long MS_PER_TICK = 50L;

    private record Layout(int texW, int texH, int frameW, int frameH, int[] frameIndex, int[] frameTicks,
                          int totalTicks) {}

    private static final Layout NOT_ANIMATED = new Layout(0, 0, 0, 0, new int[0], new int[0], 0);
    private static final Map<ResourceLocation, Layout> LAYOUTS = new HashMap<>();

    private AnimatedTexture() {}

    public static void blit(@NotNull GuiGraphics g, @NotNull ResourceLocation tex, int x, int y, int w, int h) {
        Layout layout = LAYOUTS.computeIfAbsent(tex, AnimatedTexture::loadLayout);
        if (layout == NOT_ANIMATED) {
            g.blit(tex, x, y, 0, 0, w, h, w, h);
            return;
        }

        int frame = currentFrame(layout);
        int cols = Math.max(1, layout.texW() / layout.frameW());
        float u = (frame % cols) * layout.frameW();
        float v = (frame / cols) * layout.frameH();
        g.blit(tex, x, y, w, h, u, v, layout.frameW(), layout.frameH(), layout.texW(), layout.texH());
    }

    public static void invalidate(@NotNull ResourceLocation tex) {
        LAYOUTS.remove(tex);
    }

    public static void invalidateAll() {
        LAYOUTS.clear();
    }

    private static int currentFrame(Layout layout) {
        long tick = (System.currentTimeMillis() / MS_PER_TICK) % layout.totalTicks();
        for (int i = 0; i < layout.frameIndex().length; i++) {
            tick -= layout.frameTicks()[i];
            if (tick < 0) return layout.frameIndex()[i];
        }
        return layout.frameIndex()[layout.frameIndex().length - 1];
    }

    private static Layout loadLayout(ResourceLocation tex) {
        try {
            JsonObject animation = readAnimationSection(tex);
            if (animation == null) return NOT_ANIMATED;

            int[] size = readTextureSize(tex);
            if (size == null) return NOT_ANIMATED;
            int texW = size[0], texH = size[1];

            int declaredW = animation.has("width") ? animation.get("width").getAsInt() : -1;
            int declaredH = animation.has("height") ? animation.get("height").getAsInt() : -1;
            int frameW, frameH;
            if (declaredH != -1) {
                frameW = declaredW != -1 ? declaredW : texW;
                frameH = declaredH;
            } else if (declaredW != -1) {
                frameW = declaredW;
                frameH = texH;
            } else {
                frameW = frameH = Math.min(texW, texH);
            }
            if (frameW <= 0 || frameH <= 0 || texW < frameW || texH < frameH) return NOT_ANIMATED;

            int defaultTime = animation.has("frametime") ? Math.max(1, animation.get("frametime").getAsInt()) : 1;
            int frameCount = (texW / frameW) * (texH / frameH);

            List<int[]> frames = new ArrayList<>();
            if (animation.has("frames") && animation.get("frames").isJsonArray()) {
                JsonArray listed = animation.getAsJsonArray("frames");
                for (JsonElement element : listed) {
                    if (element.isJsonObject()) {
                        JsonObject entry = element.getAsJsonObject();
                        int index = entry.get("index").getAsInt();
                        int time = entry.has("time") ? Math.max(1, entry.get("time").getAsInt()) : defaultTime;
                        if (index >= 0 && index < frameCount) frames.add(new int[] { index, time });
                    } else {
                        int index = element.getAsInt();
                        if (index >= 0 && index < frameCount) frames.add(new int[] { index, defaultTime });
                    }
                }
            } else {
                for (int i = 0; i < frameCount; i++) frames.add(new int[] { i, defaultTime });
            }
            if (frames.size() < 2) return NOT_ANIMATED;

            int[] frameIndex = new int[frames.size()];
            int[] frameTicks = new int[frames.size()];
            int total = 0;
            for (int i = 0; i < frames.size(); i++) {
                frameIndex[i] = frames.get(i)[0];
                frameTicks[i] = frames.get(i)[1];
                total += frameTicks[i];
            }
            return new Layout(texW, texH, frameW, frameH, frameIndex, frameTicks, total);
        } catch (Exception e) {
            return NOT_ANIMATED;
        }
    }

    private static @Nullable JsonObject readAnimationSection(ResourceLocation tex) throws Exception {
        try (InputStream is = openMcmeta(tex)) {
            if (is == null) return null;
            JsonElement root = JsonParser.parseReader(new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8)));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("animation")) return null;
            JsonElement animation = root.getAsJsonObject().get("animation");
            return animation.isJsonObject() ? animation.getAsJsonObject() : new JsonObject();
        }
    }

    private static @Nullable InputStream openMcmeta(ResourceLocation tex) throws Exception {
        Path customFile = customFile(tex, ".mcmeta");
        if (customFile != null) return Files.exists(customFile) ? Files.newInputStream(customFile) : null;

        ResourceLocation meta = ResourceLocation.fromNamespaceAndPath(tex.getNamespace(), tex.getPath() + ".mcmeta");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(meta);
        return resource.isPresent() ? resource.get().open() : null;
    }

    private static @Nullable int[] readTextureSize(ResourceLocation tex) throws Exception {
        Path customFile = customFile(tex, "");
        if (customFile != null) {
            if (!Files.exists(customFile)) return null;
            try (InputStream is = Files.newInputStream(customFile); NativeImage image = NativeImage.read(is)) {
                return new int[] { image.getWidth(), image.getHeight() };
            }
        }

        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (resource.isEmpty()) return null;
        try (InputStream is = resource.get().open(); NativeImage image = NativeImage.read(is)) {
            return new int[] { image.getWidth(), image.getHeight() };
        }
    }

    private static @Nullable Path customFile(ResourceLocation tex, String suffix) {
        if (!"phoenix_chronicles".equals(tex.getNamespace()) || !tex.getPath().startsWith(CUSTOM_PREFIX)) return null;
        String rel = tex.getPath().substring(CUSTOM_PREFIX.length());
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("textures").resolve(rel + suffix);
    }
}
