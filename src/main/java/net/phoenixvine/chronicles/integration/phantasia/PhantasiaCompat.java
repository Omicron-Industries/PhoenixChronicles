package net.phoenixvine.chronicles.integration.phantasia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SPhantasiaTaskCompletePacket;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.ViewMachineTask;
import net.phoenixvine.chronicles.tasks.ViewSceneTask;
import net.phoenixvine.phantasia.api.PhantasiaAPI;
import net.phoenixvine.phantasia.api.PhantasiaEvents;
import net.phoenixvine.phantasia.api.PhantasiaMachinePreview;
import net.phoenixvine.phantasia.api.PhantasiaScenePreview;

public class PhantasiaCompat {

    public static final String PHANTASIA_MOD_ID = "phantasia";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(PHANTASIA_MOD_ID);
    }

    public static void init() {
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> MinecraftForge.EVENT_BUS.register(ClientEvents.class));
    }

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

                    vmt.markCompletedClient(player);

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

    public static void openForTask(Object task, Screen parent) {
        if (!isAvailable()) return;
        if (task instanceof ViewMachineTask vmt) {
            PhantasiaAPI.openForMachine(vmt.getMachineId(), parent);
        } else if (task instanceof ViewSceneTask vst) {
            PhantasiaAPI.openScene(vst.getSceneId(), parent);
        }
    }

    public static Object createPreviewForNode(QuestNode node) {
        if (!isAvailable() || node == null) return null;

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

    public static Object createPreview(String machineId) {
        if (!isAvailable() || machineId == null || machineId.isBlank()) return null;
        if (!PhantasiaAPI.hasScript(machineId)) return null;
        PhantasiaMachinePreview preview = PhantasiaAPI.createPreview(machineId);
        if (preview != null) applyAutoSpinSetting(preview);
        return preview;
    }

    public static Object createScenePreview(String sceneId) {
        if (!isAvailable() || sceneId == null || sceneId.isBlank()) return null;
        if (!PhantasiaAPI.hasScene(sceneId)) return null;
        PhantasiaScenePreview preview = PhantasiaAPI.createScenePreview(sceneId);
        if (preview != null) applyAutoSpinSetting(preview);
        return preview;
    }

    private static void applyAutoSpinSetting(Object preview) {
        float degPerSec = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().isPhantasiaAutoSpin() ?
                20f : 0f;
        if (preview instanceof PhantasiaMachinePreview p) p.setAutoSpin(degPerSec);
        else if (preview instanceof PhantasiaScenePreview p) p.setAutoSpin(degPerSec);
    }

    public static void tickPreview(Object preview) {
        if (preview instanceof PhantasiaMachinePreview p) p.tick();
        else if (preview instanceof PhantasiaScenePreview p) p.tick();
    }

    public static boolean isPreviewLoadFailed(Object preview) {
        if (preview instanceof PhantasiaMachinePreview p) return p.isLoadFailed();
        if (preview instanceof PhantasiaScenePreview p) return p.isLoadFailed();
        return false;
    }

    public static void renderPreview(Object preview, GuiGraphics g, int x, int y, int w, int h,
                                     float partialTick) {
        if (preview instanceof PhantasiaMachinePreview p) p.render(g, x, y, w, h, partialTick);
        else if (preview instanceof PhantasiaScenePreview p) p.render(g, x, y, w, h, partialTick);
    }

    public static boolean isPreviewReady(Object preview) {
        if (preview instanceof PhantasiaMachinePreview p) return p.isReady();
        if (preview instanceof PhantasiaScenePreview p) return p.isReady();
        return false;
    }

    public static boolean previewMouseClicked(Object preview, double mx, double my, int x, int y, int w, int h,
                                              Screen parent) {
        if (preview instanceof PhantasiaMachinePreview p) return p.mouseClicked(mx, my, x, y, w, h, parent);
        if (preview instanceof PhantasiaScenePreview p) return p.mouseClicked(mx, my, x, y, w, h, parent);
        return false;
    }

    public static void closePreview(Object preview) {
        if (preview instanceof PhantasiaMachinePreview p) p.close();
        else if (preview instanceof PhantasiaScenePreview p) p.close();
    }
}
