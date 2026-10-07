package net.phoenixvine.chronicles.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.client.registry.LangSyncScheduler;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.util.ClientPooledProgress;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.TutorialProgressTracker;

import com.mojang.brigadier.arguments.StringArgumentType;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ChronicleClientEvents {

    @SubscribeEvent
    public static void onRegisterClientCommands(@NotNull RegisterClientCommandsEvent event) {
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
                                                    () -> Component.translatable(
                                                            "phoenix_chronicles.command.tutorial_reset_all"),
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
                                                            .translatable(
                                                                    "phoenix_chronicles.command.tutorial_reset_specfic" +
                                                                            questId),
                                                            false);
                                                    return 1;
                                                })))));
    }

    @SubscribeEvent
    public static void onRegisterGotoCommand(@NotNull RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("chronicles")
                .then(Commands.literal("goto")
                        .then(Commands.argument("quest", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
                                        builder.suggest(n.getId().toString());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String raw = StringArgumentType.getString(ctx, "quest").trim();
                                    net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation
                                            .tryParse(raw);
                                    QuestNode target = id != null ? QuestTreeRegistry.getQuest(id) : null;
                                    if (target == null) {
                                        ctx.getSource().sendFailure(Component.literal("No quest with id " + raw));
                                        return 0;
                                    }
                                    Minecraft.getInstance().tell(() -> {
                                        ChronicleOverviewScreen screen = new ChronicleOverviewScreen();
                                        Minecraft.getInstance().setScreen(screen);
                                        screen.navigateToNode(target);
                                    });
                                    return 1;
                                }))));
    }

    private static void ensureTutorialTrackerInit() {
        if (TutorialProgressTracker.isInitialized()) return;
        Path cfg = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve(PhoenixChronicles.MOD_ID);
        TutorialProgressTracker.init(cfg);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.@NotNull PlayerLoggedOutEvent event) {
        if (Minecraft.getInstance().player != null && event.getEntity() == Minecraft.getInstance().player) {
            QuestFileSaver.saveAllQuestsToDisk();
            LangSyncScheduler.flushNow();
        }
        ClientPooledProgress.clear();
    }

    @SubscribeEvent
    public static void onClientStopping(GameShuttingDownEvent event) {
        QuestFileSaver.saveAllQuestsToDisk();
        LangSyncScheduler.flushNow();
    }
}
