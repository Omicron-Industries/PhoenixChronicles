package net.phoenixvine.chronicles.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;

import org.lwjgl.glfw.GLFW;

/**
 * FTB Quests' well-known "item lookup" feature: hover/hold an item and press a key to jump
 * the questbook to whichever quest(s) require it. See {@link ItemLookup} for the actual logic -
 * this class only owns the keybinding itself.
 */
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

    /**
     * Pins/unpins the quest currently under the mouse while the overview screen is open - see
     * ChronicleOverviewScreen#keyPressed. Not a global KeyMapping.consumeClick() binding since
     * screens generally don't route input through KeyMapping while they have focus; instead the
     * screen checks matches() against its own keyPressed() key/scancode.
     */
    public static final KeyMapping PIN_QUEST = new KeyMapping(
            "key.phoenix_chronicles.pin_quest",
            GLFW.GLFW_KEY_P,
            "key.categories.phoenix_chronicles");

    // ── Canvas shortcuts - previously raw GLFW keycode checks in ChronicleOverviewScreen's own
    // keyPressed(), not rebindable and not shown in Minecraft's Controls menu. Converting to real
    // KeyMapping means they show up there and can be reassigned like any other key, same as the
    // three above; the screen still checks matches() manually since these fire while a Screen has
    // focus rather than during normal in-game play. Some of these previously required holding
    // Ctrl/Shift together with a letter (e.g. Ctrl+F for search) - a KeyMapping is a single key
    // with no baked-in modifier, so those became standalone rebindable keys instead; the chosen
    // defaults avoid colliding with each other or with the three bindings above.
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

    /** Dev-mode only - see ChronicleOverviewScreen#keyPressed for the isDevMode gate. */
    public static final KeyMapping TOGGLE_VALIDATION = new KeyMapping(
            "key.phoenix_chronicles.toggle_validation",
            GLFW.GLFW_KEY_V,
            "key.categories.phoenix_chronicles");

    /** Dev-mode only. */
    public static final KeyMapping TOGGLE_STATS = new KeyMapping(
            "key.phoenix_chronicles.toggle_stats",
            GLFW.GLFW_KEY_S,
            "key.categories.phoenix_chronicles");

    /** Dev-mode only. */
    public static final KeyMapping TOGGLE_SUBGRAPH = new KeyMapping(
            "key.phoenix_chronicles.toggle_subgraph",
            GLFW.GLFW_KEY_G,
            "key.categories.phoenix_chronicles");

    /** Dev-mode only. */
    public static final KeyMapping IMPORT_FTB = new KeyMapping(
            "key.phoenix_chronicles.import_ftb",
            GLFW.GLFW_KEY_I,
            "key.categories.phoenix_chronicles");

    /** Dev-mode only. */
    public static final KeyMapping OPEN_DEV_WIKI = new KeyMapping(
            "key.phoenix_chronicles.open_dev_wiki",
            GLFW.GLFW_KEY_SLASH,
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
        event.register(TOGGLE_VALIDATION);
        event.register(TOGGLE_STATS);
        event.register(TOGGLE_SUBGRAPH);
        event.register(IMPORT_FTB);
        event.register(OPEN_DEV_WIKI);
    }
}
