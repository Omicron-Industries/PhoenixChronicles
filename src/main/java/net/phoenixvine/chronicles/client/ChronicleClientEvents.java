package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.TutorialProgressTracker;

import com.mojang.brigadier.arguments.StringArgumentType;

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
                        })
                        .then(Commands.literal("tutorial")
                                .then(Commands.literal("reset")
                                        .executes(ctx -> {
                                            ensureTutorialTrackerInit();
                                            TutorialProgressTracker.resetAll();
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("§aReset progress for all tutorials."),
                                                    false);
                                            return 1;
                                        })
                                        .then(Commands.argument("quest", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
                                                        if (!n.getTutorialSteps().isEmpty()) {
                                                            builder.suggest(n.getId().getPath());
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    ensureTutorialTrackerInit();
                                                    String questId = StringArgumentType.getString(ctx, "quest");
                                                    TutorialProgressTracker.reset(questId);
                                                    ctx.getSource().sendSuccess(() -> Component
                                                            .literal("§aReset tutorial progress for §f" + questId),
                                                            false);
                                                    return 1;
                                                })))));
    }

    private static void ensureTutorialTrackerInit() {
        if (TutorialProgressTracker.isInitialized()) return;
        java.nio.file.Path cfg = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve(PhoenixChronicles.MOD_ID);
        TutorialProgressTracker.init(cfg);
    }

    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (Minecraft.getInstance().player != null && event.getEntity() == Minecraft.getInstance().player) {
            QuestFileSaver.saveAllQuestsToDisk();
            LangSyncScheduler.flushNow();
        }
        ClientPooledProgress.clear();
    }

    @SubscribeEvent
    public static void onClientStopping(net.minecraftforge.event.GameShuttingDownEvent event) {
        QuestFileSaver.saveAllQuestsToDisk();
        LangSyncScheduler.flushNow();
    }
}
