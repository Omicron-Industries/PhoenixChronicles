package net.phoenixvine.chronicles.client.event;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;

import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ChronicleKeyBindings {

    public static final KeyMapping ITEM_LOOKUP = new KeyMapping(
            "key.phoenix_chronicles.item_lookup",
            GLFW.GLFW_KEY_U,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping OPEN_QUESTBOOK = new KeyMapping(
            "key.phoenix_chronicles.open_questbook",
            GLFW.GLFW_KEY_K,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping PIN_QUEST = new KeyMapping(
            "key.phoenix_chronicles.pin_quest",
            GLFW.GLFW_KEY_P,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping SEARCH = new KeyMapping(
            "key.phoenix_chronicles.search",
            GLFW.GLFW_KEY_F,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping FIT_TO_CANVAS = new KeyMapping(
            "key.phoenix_chronicles.fit_to_canvas",
            GLFW.GLFW_KEY_HOME,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping TOGGLE_LINE_STYLE = new KeyMapping(
            "key.phoenix_chronicles.toggle_line_style",
            GLFW.GLFW_KEY_L,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping TOGGLE_MINIMAP = new KeyMapping(
            "key.phoenix_chronicles.toggle_minimap",
            GLFW.GLFW_KEY_M,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping CURSOR_ZOOM = new KeyMapping(
            "key.phoenix_chronicles.cursor_zoom",
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping TOGGLE_VALIDATION = new KeyMapping(
            "key.phoenix_chronicles.toggle_validation",
            GLFW.GLFW_KEY_V,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping TOGGLE_STATS = new KeyMapping(
            "key.phoenix_chronicles.toggle_stats",
            GLFW.GLFW_KEY_S,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping TOGGLE_SUBGRAPH = new KeyMapping(
            "key.phoenix_chronicles.toggle_subgraph",
            GLFW.GLFW_KEY_G,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping IMPORT_FTB = new KeyMapping(
            "key.phoenix_chronicles.import_ftb",
            GLFW.GLFW_KEY_I,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping OPEN_DEV_WIKI = new KeyMapping(
            "key.phoenix_chronicles.open_dev_wiki",
            GLFW.GLFW_KEY_SLASH,
            "key.categories.phoenix_chronicles");

    public static final KeyMapping TOGGLE_QUEST_VIEW = new KeyMapping(
            "key.phoenix_chronicles.toggle_quest_view",
            GLFW.GLFW_KEY_SPACE,
            "key.categories.phoenix_chronicles");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ITEM_LOOKUP);
        event.register(OPEN_QUESTBOOK);
        event.register(PIN_QUEST);
        event.register(SEARCH);
        event.register(FIT_TO_CANVAS);
        event.register(TOGGLE_LINE_STYLE);
        event.register(TOGGLE_MINIMAP);
        event.register(CURSOR_ZOOM);
        event.register(TOGGLE_VALIDATION);
        event.register(TOGGLE_STATS);
        event.register(TOGGLE_SUBGRAPH);
        event.register(IMPORT_FTB);
        event.register(OPEN_DEV_WIKI);
        event.register(TOGGLE_QUEST_VIEW);
    }
}
