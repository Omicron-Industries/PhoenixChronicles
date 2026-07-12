package net.phoenixvine.chronicles.integration.phantasia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SPhantasiaTaskCompletePacket;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.ViewMachineTask;
import net.phoenixvine.chronicles.tasks.ViewSceneTask;
import net.phoenixvine.phantasia.api.PhantasiaAPI;
import net.phoenixvine.phantasia.api.PhantasiaEvents;
import net.phoenixvine.phantasia.api.PhantasiaMachinePreview;

/**
 * Phantasia mod integration for Phoenix Chronicles.
 *
 * Backed by {@code curse.maven:phantasia-1589286:8326565} (see build.gradle). All calls are safe
 * no-ops when Phantasia isn't installed on the running client/server — check {@link #isAvailable()}
 * where it matters, but the individual helpers below already guard themselves.
 *
 * ── WHAT THIS GIVES YOU ──────────────────────────────────────────────────────
 *
 * • view_machine / view_scene quest tasks (see ViewMachineTask / ViewSceneTask) — complete when
 * the player views a Phantasia build guide/scene for at least a configurable time floor.
 * Registered in PhoenixTaskRegistry and selectable from TaskRewardEditorScreen's task dropdown.
 *
 * • "View in Phantasia ▶" button — QuestTasksScreen opens the viewer for any task where
 * {@link #canOpenForTask(Object)} returns true.
 *
 * • Embedded 3D preview widget — see the preview helpers below, used by ChronicleOverviewScreen's
 * node inspector for quests carrying a ViewMachineTask.
 */
public class PhantasiaCompat {

    public static final String PHANTASIA_MOD_ID = "phantasia";

    /** Guard for all compat calls — check this before calling init(). */
    public static boolean isAvailable() {
        return ModList.get().isLoaded(PHANTASIA_MOD_ID);
    }

    /** Call from your mod constructor or FMLCommonSetupEvent, guarded by isAvailable(). */
    public static void init() {
        // Client-side event subscription happens automatically via ClientEvents' own
        // @Mod.EventBusSubscriber annotation below — nothing to do here beyond the isAvailable()
        // gate the caller already applies. Kept as an explicit entry point for symmetry with
        // other compat modules and so future non-event wiring has an obvious home.
    }

    // ── Client-side Phantasia event subscribers ───────────────────────────────

    @Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID,
                            bus = Mod.EventBusSubscriber.Bus.FORGE,
                            value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onMachineViewerClose(PhantasiaEvents.ViewerClose event) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            String closedMachineId = event.getMachineId();
            float secondsViewed = event.getSecondsViewed();

            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                for (Object taskObj : node.getTasks()) {
                    if (!(taskObj instanceof ViewMachineTask vmt)) continue;
                    if (vmt.isCompletedFor(player)) continue;
                    if (!vmt.getMachineId().equals(closedMachineId)) continue;
                    if (secondsViewed < vmt.getMinSeconds()) continue;

                    // Mark locally so the UI updates immediately without waiting for a server round-trip
                    vmt.markCompletedClient(player);

                    // Notify the server so it persists and triggers quest completion checks
                    ChronicleNetwork.CHANNEL.sendToServer(
                            new C2SPhantasiaTaskCompletePacket(node.getId(), vmt.getTaskId()));
                }
            }
        }

        @SubscribeEvent
        public static void onSceneViewerClose(PhantasiaEvents.SceneViewerClose event) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            String closedSceneId = event.getSceneId();
            float secondsViewed = event.getSecondsViewed();

            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                for (Object taskObj : node.getTasks()) {
                    if (!(taskObj instanceof ViewSceneTask vst)) continue;
                    if (vst.isCompletedFor(player)) continue;
                    if (!vst.getSceneId().equals(closedSceneId)) continue;
                    if (secondsViewed < vst.getMinSeconds()) continue;

                    vst.markCompletedClient(player);

                    ChronicleNetwork.CHANNEL.sendToServer(
                            new C2SPhantasiaTaskCompletePacket(node.getId(), vst.getTaskId()));
                }
            }
        }
    }

    // ── "View in Phantasia" button helper ─────────────────────────────────────

    /**
     * Returns true if the given task is a ViewMachineTask/ViewSceneTask whose target id is
     * known to Phantasia. Safe to call even when Phantasia is absent — returns false.
     */
    public static boolean canOpenForTask(Object task) {
        if (!isAvailable()) return false;
        if (task instanceof ViewMachineTask vmt) {
            return PhantasiaAPI.hasScript(vmt.getMachineId());
        }
        if (task instanceof ViewSceneTask vst) {
            return PhantasiaAPI.hasScene(vst.getSceneId());
        }
        return false;
    }

    /**
     * Opens the Phantasia viewer for a ViewMachineTask or ViewSceneTask.
     * Safe no-op when Phantasia is absent or the machine/scene isn't registered.
     *
     * @param task   the task to open a viewer for
     * @param parent the Screen Phantasia should return to on close
     */
    public static void openForTask(Object task, Screen parent) {
        if (!isAvailable()) return;
        if (task instanceof ViewMachineTask vmt) {
            PhantasiaAPI.openForMachine(vmt.getMachineId(), parent);
        } else if (task instanceof ViewSceneTask vst) {
            PhantasiaAPI.openScene(vst.getSceneId(), parent);
        }
    }

    // ── Embedded 3D preview widget ────────────────────────────────────────────
    //
    // The preview is stored as Object in callers to avoid a hard compile-time reference to
    // PhantasiaMachinePreview leaking into screens that don't otherwise need the Phantasia dep
    // (matters for anyone building without CurseMaven access) — cast happens only inside here.

    /**
     * Creates a preview for the first ViewMachineTask on the given quest node,
     * or returns null if Phantasia is absent / no qualifying task exists.
     *
     * Call this when selectedNode changes (not on every rebuild — recreating the
     * preview resets the camera and triggers an async reload).
     */
    public static Object createPreviewForNode(QuestNode node) {
        if (!isAvailable() || node == null) return null;
        // A quest's own content-level preview (set directly on the node, independent of any
        // view_machine task requirement) takes priority over one implied by a task.
        String contentMachineId = node.getPreviewMachineId();
        if (contentMachineId != null && !contentMachineId.isBlank()) {
            Object preview = createPreview(contentMachineId);
            if (preview != null) return preview;
        }
        for (Object taskObj : node.getTasks()) {
            if (taskObj instanceof ViewMachineTask vmt) {
                return PhantasiaAPI.createPreview(vmt.getMachineId());
            }
        }
        return null;
    }

    /**
     * Creates a preview for an arbitrary machine id, independent of any quest/task. Used by the
     * quest group ("popup") editor and by quest content previews that aren't tied to a
     * ViewMachineTask. Returns null if Phantasia is absent or the id isn't a known machine.
     */
    public static Object createPreview(String machineId) {
        if (!isAvailable() || machineId == null || machineId.isBlank()) return null;
        if (!PhantasiaAPI.hasScript(machineId)) return null;
        return PhantasiaAPI.createPreview(machineId);
    }

    /** Advances the preview camera animation. Call once per render frame (before rendering). */
    public static void tickPreview(Object preview) {
        if (preview == null) return;
        ((PhantasiaMachinePreview) preview).tick();
    }

    /** True once Phantasia has given up on this preview's pattern load (distinct from "still loading"). */
    public static boolean isPreviewLoadFailed(Object preview) {
        if (preview == null) return false;
        return ((PhantasiaMachinePreview) preview).isLoadFailed();
    }

    /** Renders the preview into the given screen rectangle. */
    public static void renderPreview(Object preview, GuiGraphics g, int x, int y, int w, int h,
                                     float partialTick) {
        if (preview == null) return;
        ((PhantasiaMachinePreview) preview).render(g, x, y, w, h, partialTick);
    }

    /** Returns true once the async pattern load is complete and the 3D view is live. */
    public static boolean isPreviewReady(Object preview) {
        if (preview == null) return false;
        return ((PhantasiaMachinePreview) preview).isReady();
    }

    /**
     * Forwards a mouse click to the preview (lets the player drag-to-rotate).
     * Returns true if the preview consumed the click.
     */
    public static boolean previewMouseClicked(Object preview, double mx, double my, int x, int y, int w, int h,
                                              Screen parent) {
        if (preview == null) return false;
        return ((PhantasiaMachinePreview) preview).mouseClicked(mx, my, x, y, w, h, parent);
    }

    /**
     * Releases GL resources. Call from Screen.onClose() and whenever the selected
     * node changes so the old preview doesn't leak a dummy world.
     */
    public static void closePreview(Object preview) {
        if (preview == null) return;
        ((PhantasiaMachinePreview) preview).close();
    }
}
