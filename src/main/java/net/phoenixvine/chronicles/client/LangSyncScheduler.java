package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.client.screen.LangEditorScreen;

import java.nio.file.Path;

/**
 * Coalesces lang/en_us.json writes so any screen that persists quest/task text
 * (TaskRewardEditorScreen, QuestTasksScreen, etc.) keeps translations in sync without a separate
 * trip through LangEditorScreen's own "Save all" button. Callers call {@link #markDirty()} right
 * after their own save-to-disk completes; the actual write (and the resource-pack reload that can
 * follow it) is delayed a few seconds so several saves in quick succession - editing multiple
 * tasks back-to-back, say - collapse into a single write instead of one per save. A guaranteed
 * {@link #flushNow()} is wired into world logout / game shutdown (see ChronicleClientEvents) and
 * into the editor screens' own onClose, so nothing is lost short of a hard process kill.
 */
@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LangSyncScheduler {

    private static final long DEBOUNCE_MS = 4000;

    private static volatile boolean dirty = false;
    private static volatile long dueAtMs = 0;

    /** Call right after persisting a quest/task text change to disk. */
    public static void markDirty() {
        dirty = true;
        dueAtMs = System.currentTimeMillis() + DEBOUNCE_MS;
    }

    /** Writes immediately if a sync is pending; a no-op otherwise. Safe to call speculatively. */
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
