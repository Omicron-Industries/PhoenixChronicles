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

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ITEM_LOOKUP);
        event.register(OPEN_QUESTBOOK);
        event.register(PIN_QUEST);
    }
}
