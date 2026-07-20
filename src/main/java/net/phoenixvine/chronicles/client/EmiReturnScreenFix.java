package net.phoenixvine.chronicles.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;

import java.lang.ref.WeakReference;

/**
 * Fixes EMI's recipe-viewer "Escape returns to the wrong screen" behavior when opened from the
 * quest book (see QuestTasksScreen#tryOpenInRecipeViewer).
 *
 * EMI's own EmiApi#displayRecipes() only recognizes an AbstractContainerScreen (or its own
 * RecipeScreen/BoMScreen) as something worth returning to on close (see EmiApi#getHandledScreen
 * in EMI's source). QuestTasksScreen is a plain Screen, so EMI silently discards it, opens a
 * throwaway InventoryScreen first, and points its own RecipeScreen.old at THAT instead - that
 * field is strictly typed AbstractContainerScreen<?>, so it can't be made to hold a reference to
 * our custom screen even via reflection. The player never sees that throwaway inventory screen
 * (it's replaced by the RecipeScreen in the same call before a frame renders), but pressing
 * Escape on the recipe screen navigates BACK to it, and Escape again exits to gameplay entirely -
 * the quest book is gone. That's the "kicks us out of both" behavior reported.
 *
 * The fix: watch for that EXACT throwaway screen instance (identity, not just type - so a real
 * inventory screen the player opens some other way is never touched) becoming active via Forge's
 * ScreenEvent.Opening, which fires before the new screen is actually set and lets us swap it out,
 * and substitute the screen the player was really on instead.
 */
@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EmiReturnScreenFix {

    private EmiReturnScreenFix() {}

    private static WeakReference<Screen> armedEphemeralScreen = new WeakReference<>(null);
    private static WeakReference<Screen> armedReturnToScreen = new WeakReference<>(null);

    /**
     * Call right after opening a recipe viewer that silently swapped in a throwaway screen it
     * plans to return to instead of the one the player was actually on.
     *
     * @param ephemeralScreen the throwaway screen instance the recipe viewer will try to switch
     *                        to when its own screen closes
     * @param returnToScreen  the screen to open instead when that happens
     */
    public static void armReturnTo(Screen ephemeralScreen, Screen returnToScreen) {
        armedEphemeralScreen = new WeakReference<>(ephemeralScreen);
        armedReturnToScreen = new WeakReference<>(returnToScreen);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen ephemeral = armedEphemeralScreen.get();
        if (ephemeral == null) return; // nothing armed, or it's already been collected
        if (event.getNewScreen() == ephemeral) {
            event.setNewScreen(armedReturnToScreen.get());
            armedEphemeralScreen = new WeakReference<>(null);
            armedReturnToScreen = new WeakReference<>(null);
        }
    }
}
