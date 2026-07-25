package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;

import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DiagnosticKeyListener {

    private DiagnosticKeyListener() {}

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        int key = event.getKey();
        int mods = event.getModifiers();
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;

        if (key == GLFW.GLFW_KEY_P && ctrl && shift) {
            String msg = ProfilerSession.toggle();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(msg), true);
            }
            return;
        }

        if (ProfilerSession.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            String screenName = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
            ProfilerSession.logKeyEvent(key, mods, screenName);
        }
    }
}
