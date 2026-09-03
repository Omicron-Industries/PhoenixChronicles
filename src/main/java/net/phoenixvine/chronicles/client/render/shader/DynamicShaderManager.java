package net.phoenixvine.chronicles.client.render.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.phoenixvine.chronicles.PhoenixChronicles;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class DynamicShaderManager {

    private DynamicShaderManager() {}

    private static final Path SHADER_DIR = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config").resolve(PhoenixChronicles.MOD_ID).resolve("shaders");

    private static final ResourceLocation ANCHOR_RESOURCE = ResourceLocation.fromNamespaceAndPath(
            PhoenixChronicles.MOD_ID,
            "shaders/core/quest_bg_sun.vsh");

    private static final String VERTEX_SOURCE = """
            #version 150
            in vec3 Position;
            in vec2 UV0;
            uniform mat4 ModelViewMat;
            uniform mat4 ProjMat;
            out vec2 texCoord;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                texCoord = UV0;
            }
            """;

    private record Compiled(long sourceMtime, long lastChecked, ShaderInstance instance) {}

    private static final Map<String, Compiled> CACHE = new HashMap<>();
    private static final long HOT_RELOAD_COOLDOWN_MS = 1000L;

    private static final Map<String, Boolean> COMPILE_OK = new HashMap<>();

    public static boolean lastCompileFailed(String id) {
        if (id == null || id.isBlank()) return false;
        Boolean ok = COMPILE_OK.get(id);
        return ok != null && !ok;
    }

    public static List<String> listAvailable() {
        if (!Files.isDirectory(SHADER_DIR)) return List.of();

        try (var stream = Files.list(SHADER_DIR)) {
            return stream.filter(p -> p.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".frag"))
                    .map(p -> {
                        String name = p.getFileName().toString();
                        return name.substring(0, name.length() - 5);
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            PhoenixChronicles.LOGGER.error(
                    "[Phoenix Chronicles] Failed to list available shaders", e);
            return List.of();
        }
    }

    public static ShaderInstance get(String id) {
        if (id == null || id.isBlank()) return null;
        Path file = SHADER_DIR.resolve(id + ".frag");
        if (!Files.isRegularFile(file)) return null;

        Compiled cached = CACHE.get(id);
        long currentTime = System.currentTimeMillis();
        long mtime;

        String userSource;

        if (cached != null && (currentTime - cached.lastChecked()) < HOT_RELOAD_COOLDOWN_MS) {
            return cached.instance();
        }

        try {
            mtime = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return cached != null ? cached.instance() : null;
        }

        if (cached != null && cached.sourceMtime() == mtime) {

            CACHE.put(id, new Compiled(mtime, currentTime, cached.instance()));
            return cached.instance();
        }

        try {
            userSource = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return cached != null ? cached.instance() : null;
        }

        ShaderInstance newInstance = compile(id, userSource);
        COMPILE_OK.put(id, newInstance != null);
        if (newInstance == null) {
            PhoenixChronicles.LOGGER.error(
                    "[Phoenix Chronicles] Custom shader '{}' failed to compile.", id);
            return cached != null ? cached.instance() : null;
        }

        if (cached != null && cached.instance() != null) {
            cached.instance().close();
        }

        newInstance.safeGetUniform("iTime");
        newInstance.safeGetUniform("iResolution");
        PhoenixChronicles.LOGGER.info(
                "[Phoenix Chronicles] Custom shader '{}' compiled: hasITime={} hasIResolution={}",
                id,
                true,
                true);

        CACHE.put(id, new Compiled(mtime, currentTime, newInstance));
        return newInstance;
    }

    private static ShaderInstance compile(String id, String userSource) {
        String fragmentSource = buildFragmentSource(userSource);
        String name = "dyn_" + sanitize(id) + "_" + Integer.toHexString(fragmentSource.hashCode());
        String jsonSource = buildJsonSource(name);

        ResourceLocation jsonLoc = coreResLoc(name, ".json");
        ResourceLocation vshLoc = coreResLoc(name, ".vsh");
        ResourceLocation fshLoc = coreResLoc(name, ".fsh");

        PackResources anchorSource;

        try {
            anchorSource = Minecraft.getInstance().getResourceManager()
                    .getResourceOrThrow(ANCHOR_RESOURCE).source();
        } catch (IOException e) {

            throw new IllegalStateException(
                    "[Phoenix Chronicles] Bundled shader resource '" + ANCHOR_RESOURCE +
                            "' is missing. The mod jar is corrupted or incorrectly packaged.",
                    e);
        }

        Map<ResourceLocation, Resource> resources = Map.of(
                jsonLoc, memResource(anchorSource, jsonSource),
                vshLoc, memResource(anchorSource, VERTEX_SOURCE),
                fshLoc, memResource(anchorSource, fragmentSource));
        ResourceProvider provider = ResourceProvider.fromMap(resources);

        try {
            return new ShaderInstance(provider, ResourceLocation.fromNamespaceAndPath(PhoenixChronicles.MOD_ID, name),
                    DefaultVertexFormat.POSITION_TEX);
        } catch (Exception e) {
            PhoenixChronicles.LOGGER.error(
                    "[Phoenix Chronicles] Failed to compile custom shader '{}'", id, e);
            return null;
        }
    }

    private static ResourceLocation coreResLoc(String name, String ext) {
        return ResourceLocation.fromNamespaceAndPath(PhoenixChronicles.MOD_ID,
                "shaders/core/" + name + ext);
    }

    private static Resource memResource(PackResources source, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new Resource(source, () -> new ByteArrayInputStream(bytes));
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String buildFragmentSource(String userSource) {
        if (!userSource.contains("mainImage")) {
            return userSource;
        }
        return """
                #version 150
                uniform float iTime;
                uniform vec3 iResolution;
                uniform vec4 iMouse;
                in vec2 texCoord;
                out vec4 outColor;

                %s

                void main() {
                    vec4 fragColor = vec4(0.0);
                    vec2 fragCoord = vec2(texCoord.x, 1.0 - texCoord.y) * iResolution.xy;
                    mainImage(fragColor, fragCoord);
                    outColor = fragColor;
                }
                """.formatted(userSource);
    }

    private static String buildJsonSource(String name) {
        return """
                {
                  "blend": { "func": "add", "srcrgb": "srcalpha", "dstrgb": "one_minus_srcalpha" },
                  "vertex": "%1$s:%2$s",
                  "fragment": "%1$s:%2$s",
                  "attributes": ["Position", "UV0"],
                  "uniforms": [
                    { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0] },
                    { "name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0] },
                    { "name": "iTime", "type": "float", "count": 1, "values": [0.0] },
                    { "name": "iResolution", "type": "float", "count": 3, "values": [1.0, 1.0, 1.0] },
                    { "name": "iMouse", "type": "float", "count": 4, "values": [0.0, 0.0, 0.0, 0.0] }
                  ]
                }
                """
                .formatted(PhoenixChronicles.MOD_ID, name);
    }
}
