package net.phoenixvine.chronicles.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EmiReturnScreenFix {

    private EmiReturnScreenFix() {}

    private static Screen armedEphemeralScreen = null;
    private static Screen armedReturnToScreen = null;

    public static void armReturnTo(Screen ephemeralScreen, Screen returnToScreen) {
        armedEphemeralScreen = ephemeralScreen;
        armedReturnToScreen = returnToScreen;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen ephemeral = armedEphemeralScreen;
        if (ephemeral == null) return;
        if (event.getNewScreen() == ephemeral) {
            Screen returnTo = armedReturnToScreen;
            armedEphemeralScreen = null;
            armedReturnToScreen = null;
            if (returnTo != null) event.setNewScreen(returnTo);
        }
    }
}
