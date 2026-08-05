package net.phoenixvine.chronicles.client;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public final class ChronicleShaders {

    private ChronicleShaders() {}

    private static final Logger LOGGER = LogManager.getLogger();

    public static ShaderInstance QUEST_BG_SUN;
    public static ShaderInstance QUEST_BG_GLITCH;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        register(event, "phoenix_chronicles:quest_bg_sun", DefaultVertexFormat.POSITION_TEX, s -> QUEST_BG_SUN = s);
        register(event, "phoenix_chronicles:quest_bg_glitch", DefaultVertexFormat.POSITION_TEX,
                s -> QUEST_BG_GLITCH = s);
    }

    private static void register(RegisterShadersEvent event, String name,
                                 com.mojang.blaze3d.vertex.VertexFormat format,
                                 java.util.function.Consumer<ShaderInstance> onLoad) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), name, format), onLoad);
        } catch (IOException e) {
            LOGGER.error("[PhoenixChronicles/ChronicleShaders] Failed to register shader '{}': {}", name,
                    e.getMessage());
        }
    }
}
