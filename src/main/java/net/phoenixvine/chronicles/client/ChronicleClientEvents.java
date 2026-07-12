package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.codec.QuestFileSaver;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ChronicleClientEvents {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("chronicles")
                        .executes(context -> {
                            Minecraft.getInstance()
                                    .tell(() -> Minecraft.getInstance().setScreen(new ChronicleOverviewScreen()));
                            return 1;
                        }));
    }

    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (Minecraft.getInstance().player != null && event.getEntity() == Minecraft.getInstance().player) {
            QuestFileSaver.saveAllQuestsToDisk();
            // So the next login's initial full-progress sync is correctly treated as "establishing
            // baseline" again instead of replaying a toast for every already-completed quest.
            net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket.resetForNewSession();
        }
    }

    @SubscribeEvent
    public static void onClientStopping(net.minecraftforge.event.GameShuttingDownEvent event) {
        QuestFileSaver.saveAllQuestsToDisk();
    }
    // QuestHudOverlay is a @Mod.EventBusSubscriber itself — it self-registers.
    // No explicit registration needed here; Forge scans the annotation automatically.
}
