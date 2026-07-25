package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.client.screen.LangEditorScreen;

import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LangSyncScheduler {

    private static final long DEBOUNCE_MS = 4000;

    private static volatile boolean dirty = false;
    private static volatile long dueAtMs = 0;

    public static void markDirty() {
        dirty = true;
        dueAtMs = System.currentTimeMillis() + DEBOUNCE_MS;
    }

    public static void flushNow() {
        if (!dirty) return;
        dirty = false;
        Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        LangEditorScreen.writeEnUsJson(base);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (dirty && System.currentTimeMillis() >= dueAtMs) {
            flushNow();
        }
    }
}
