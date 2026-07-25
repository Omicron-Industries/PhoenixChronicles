package net.phoenixvine.chronicles.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;

import java.lang.ref.WeakReference;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EmiReturnScreenFix {

    private EmiReturnScreenFix() {}

    private static WeakReference<Screen> armedEphemeralScreen = new WeakReference<>(null);
    private static WeakReference<Screen> armedReturnToScreen = new WeakReference<>(null);

    public static void armReturnTo(Screen ephemeralScreen, Screen returnToScreen) {
        armedEphemeralScreen = new WeakReference<>(ephemeralScreen);
        armedReturnToScreen = new WeakReference<>(returnToScreen);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen ephemeral = armedEphemeralScreen.get();
        if (ephemeral == null) return;
        if (event.getNewScreen() == ephemeral) {
            event.setNewScreen(armedReturnToScreen.get());
            armedEphemeralScreen = new WeakReference<>(null);
            armedReturnToScreen = new WeakReference<>(null);
        }
    }
}
