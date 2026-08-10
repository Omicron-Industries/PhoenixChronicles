package net.phoenixvine.chronicles.event;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.PooledTaskProgress;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.codec.ChronicleDataLoader;
import net.phoenixvine.chronicles.codec.KubeJsTaskTypeLoader;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.chronicles.network.packet.S2CSyncPooledProgressPacket;
import net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket;
import net.phoenixvine.chronicles.registry.ChapterFlagRegistry;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.registry.RewardTableRegistry;
import net.phoenixvine.chronicles.tasks.*;
import net.phoenixvine.chronicles.tasks.BlockBreakTask;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;
import net.phoenixvine.chronicles.tracker.TeamKeyResolver;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChronicleEvents {

    public static MinecraftServer getCachedServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    private static net.minecraft.resources.ResourceLocation parseQuestArg(String raw) {
        return raw.indexOf(':') >= 0 ? new net.minecraft.resources.ResourceLocation(raw) :
                new net.minecraft.resources.ResourceLocation("phoenix_chronicles", raw);
    }

    private static volatile boolean hasServerStarted = false;

    public static boolean hasServerFullyStarted() {
        return hasServerStarted;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ChronicleDataLoader());
    }

    public static java.nio.file.Path resolveConfigDir(MinecraftServer server) {
        try {
            java.nio.file.Path worldSpecific = server
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("phoenix_chronicles");
            if (java.nio.file.Files.exists(worldSpecific)) return worldSpecific;
        } catch (Exception ignored) {}
        return server.getServerDirectory().toPath().resolve("config").resolve("phoenix_chronicles");
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        java.nio.file.Path configDir = resolveConfigDir(event.getServer());

        try {
            java.nio.file.Files.createDirectories(configDir.resolve("ftb_import"));
        } catch (java.io.IOException ignored) {}

        PhoenixTaskRegistry.registerBuiltins();
        KubeJsTaskTypeLoader.load(configDir);
        PhoenixQuestFlags.invalidateCaches();
        net.phoenixvine.chronicles.codec.ChronicleDataMigration.migrate(configDir);
        ChapterFlagRegistry.load(configDir);
        net.phoenixvine.chronicles.registry.ChapterPrereqDefaults.load(configDir);
        net.phoenixvine.chronicles.registry.QuestEngineConfig.load(configDir);
        RewardTableRegistry.load(configDir);
        net.phoenixvine.chronicles.registry.CategoryRegistry.load(configDir);
        QuestFileLoader.loadAdditiveFromDisk(configDir);
        net.phoenixvine.chronicles.codec.QuestFileWatcher.start(event.getServer(), configDir);
        hasServerStarted = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new QuestEvent.TreeReloaded());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        hasServerStarted = false;
        net.phoenixvine.chronicles.codec.QuestFileWatcher.stop();
    }

    @SubscribeEvent
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer())) {
                    if (task instanceof ItemRequirementTask ||
                            task instanceof net.phoenixvine.chronicles.tasks.TagItemTask) {
                        changed = true;
                    }
                }

                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }
            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(event.getCrafting().getItem());
        if (itemId == null) return;
        int amount = event.getCrafting().getCount();

        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer())) {
                    if (task instanceof CraftItemTask craftTask) {
                        craftTask.onItemCrafted(player, itemId, amount);
                        changed = true;
                    } else if (task instanceof ItemRequirementTask ||
                            task instanceof net.phoenixvine.chronicles.tasks.TagItemTask) {
                                changed = true;
                            }
                }

                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onEntityKilled(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            if (player.level().isClientSide) return;

            ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
            if (entityId == null) return;

            player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                boolean needSync = false;
                for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                    QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                    if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                    boolean changed = false;
                    for (Object task : node.getEffectiveTasks(player.getServer())) {
                        if (task instanceof KillEntityTask killTask) {
                            killTask.onEntityKilled(player, entityId);
                            changed = true;
                        }
                    }
                    if (changed) {
                        QuestProgressTracker.checkAndTryComplete(player, node);
                        needSync = true;
                    }
                }

                if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    QuestProgressTracker.sendProgressSync(sp);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ResourceLocation advancementId = event.getAdvancement().getId();

        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer())) {
                    if (task instanceof AdvancementTask advTask) {
                        advTask.onAdvancementEarned(player, advancementId);
                        changed = true;
                    }
                }
                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        net.minecraft.world.level.block.Block broken = event.getState().getBlock();
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;
                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer())) {
                    if (task instanceof BlockBreakTask breakTask) {
                        breakTask.onBlockBroken(player, broken);
                        changed = true;
                    }
                }
                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync) QuestProgressTracker.sendProgressSync(player);
        });
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        Block placed = event.getPlacedBlock().getBlock();
        handleBlockEvent(player, placed, "PLACE");
    }

    @SubscribeEvent
    public static void onBlockRightClicked(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Block clicked = event.getLevel().getBlockState(event.getPos()).getBlock();

        if (net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat.isAvailable()) {
            EnergyStorageTask.onBlockRightClicked(
                    player, event.getLevel(), event.getPos());
        }

        if (player.level().isClientSide) return;
        handleBlockEvent(player, clicked, "RIGHT_CLICK");
    }

    private static void handleBlockEvent(Player player, Block block, String action) {
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;
                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer())) {
                    if (task instanceof BlockInteractTask blockTask) {
                        blockTask.onBlockEvent(player, block, action);
                        changed = true;
                    }
                }
                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        var dimension = event.getTo();
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;
                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer())) {
                    if (task instanceof DimensionTask dimTask) {
                        dimTask.onChangedDimension(player, dimension);
                        changed = true;
                    }
                }
                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var questArg = com.mojang.brigadier.arguments.StringArgumentType.string();

        dispatcher.register(Commands.literal("chronicles")

                .then(Commands.literal("status")
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> {
                                    if (!(ctx.getSource()
                                            .getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                                        ctx.getSource().sendFailure(Component.literal("Must be run by a player."));
                                        return 0;
                                    }
                                    String qStr = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                            "quest");
                                    net.minecraft.resources.ResourceLocation questId;
                                    try {
                                        questId = parseQuestArg(qStr);
                                    } catch (Exception e) {
                                        ctx.getSource().sendFailure(Component.literal("Invalid quest id: " + qStr));
                                        return 0;
                                    }
                                    QuestNode node = QuestTreeRegistry.getQuest(questId);
                                    if (node == null) {
                                        ctx.getSource().sendFailure(Component.literal("Quest not found: " + qStr));
                                        return 0;
                                    }
                                    QuestState state = sp.getCapability(
                                            QuestCapabilityProvider.PLAYER_QUESTS)
                                            .map(d -> d.getQuestState(questId, QuestState.LOCKED))
                                            .orElse(QuestState.LOCKED);
                                    String stateLabel = switch (state) {
                                        case COMPLETED -> "§aCompleted";
                                        case ACTIVE -> "§eActive";
                                        case UNLOCKED -> "§bAvailable";
                                        default -> "§7Locked";
                                    };
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Quest \"" + node.getTitle().getString() + "\": " + stateLabel), false);
                                    return 1;
                                })))

                .then(Commands.literal("emergency")
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> {
                                    if (!(ctx.getSource()
                                            .getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                                        ctx.getSource().sendFailure(Component.literal("Must be run by a player."));
                                        return 0;
                                    }
                                    String qStr = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                            "quest");
                                    net.minecraft.resources.ResourceLocation questId;
                                    try {
                                        questId = parseQuestArg(qStr);
                                    } catch (Exception e) {
                                        ctx.getSource().sendFailure(Component.literal("Invalid quest id: " + qStr));
                                        return 0;
                                    }
                                    QuestNode node = QuestTreeRegistry.getQuest(questId);
                                    if (node == null) {
                                        ctx.getSource().sendFailure(Component.literal("Quest not found: " + qStr));
                                        return 0;
                                    }
                                    QuestState state = sp.getCapability(
                                            QuestCapabilityProvider.PLAYER_QUESTS)
                                            .map(d -> d.getQuestState(questId, QuestState.LOCKED))
                                            .orElse(QuestState.LOCKED);
                                    if (state != QuestState.ACTIVE) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "Emergency items are only available while the quest is active."));
                                        return 0;
                                    }
                                    List<ItemStack> items = node.getEmergencyItems();
                                    if (items.isEmpty()) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("This quest has no emergency items configured."));
                                        return 0;
                                    }
                                    for (ItemStack stack : items) {
                                        if (!sp.addItem(stack.copy())) sp.drop(stack.copy(), false);
                                    }
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§aGave " + items.size() + " emergency item(s)."),
                                            false);
                                    return 1;
                                })))

                .then(Commands.literal("complete")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> devSetState(ctx, QuestState.COMPLETED, null))
                                .then(Commands
                                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> devSetState(ctx, QuestState.COMPLETED,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                        "player"))))))

                .then(Commands.literal("forcetask")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("task",
                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> devForceTask(ctx, null))
                                .then(Commands
                                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> devForceTask(ctx,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                        "player"))))))

                .then(Commands.literal("unlock")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> devSetState(ctx, QuestState.UNLOCKED, null))
                                .then(Commands
                                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> devSetState(ctx, QuestState.UNLOCKED,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                        "player"))))))

                .then(Commands.literal("reset")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> devResetQuest(ctx, null))
                                .then(Commands
                                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> devResetQuest(ctx,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                        "player"))))))

                .then(Commands.literal("active")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> devSetState(ctx, QuestState.ACTIVE, null))
                                .then(Commands
                                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> devSetState(ctx, QuestState.ACTIVE,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                        "player"))))))

                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            MinecraftServer server = getCachedServer();
                            if (server == null) {
                                ctx.getSource().sendFailure(Component.literal("Server not available."));
                                return 0;
                            }
                            java.nio.file.Path configDir = resolveConfigDir(server);
                            QuestTreeRegistry.clearConfigQuests();
                            ChapterFlagRegistry.load(configDir);
                            net.phoenixvine.chronicles.registry.ChapterPrereqDefaults.load(configDir);
                            net.phoenixvine.chronicles.registry.QuestEngineConfig.load(configDir);
                            RewardTableRegistry.load(configDir);
                            net.phoenixvine.chronicles.registry.CategoryRegistry.load(configDir);
                            QuestFileLoader.loadAdditiveFromDisk(configDir);
                            int questCount = QuestTreeRegistry.getAllQuests().size();
                            S2CSyncQuestsPacket syncPacket = new S2CSyncQuestsPacket(QuestTreeRegistry.getAllQuests(),
                                    server);
                            int playerCount = 0;
                            for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                                ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), syncPacket);
                                playerCount++;
                            }
                            final int fp = playerCount;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§a✔ Reloaded " + questCount + " quest(s) from config, synced to " + fp +
                                            " player(s)."),
                                    true);
                            return 1;
                        }))

                .then(Commands.literal("export")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            MinecraftServer server = getCachedServer();
                            if (server == null) {
                                ctx.getSource().sendFailure(Component.literal("Server not available."));
                                return 0;
                            }
                            String stamp = java.time.LocalDateTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                            java.nio.file.Path exportDir = resolveConfigDir(server)
                                    .resolve("export").resolve(stamp);
                            try {
                                java.nio.file.Files.createDirectories(exportDir);
                            } catch (java.io.IOException ignored) {}
                            int count = net.phoenixvine.chronicles.codec.QuestFileSaver.exportTo(exportDir);
                            final java.nio.file.Path fp = exportDir;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§a✔ Exported §f" + count + " §aquest(s) to §7" + fp), false);
                            return count;
                        }))

                .then(Commands.literal("import")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> doImport(ctx, "import"))
                        .then(Commands
                                .argument("subfolder", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> doImport(ctx,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                                "subfolder")))))

                .then(Commands.literal("import-ftb")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> doImportFtb(ctx, "ftb_import"))
                        .then(Commands
                                .argument("subfolder", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> doImportFtb(ctx,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                                "subfolder")))))

                .then(Commands.literal("validate")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            List<String> errors = QuestFileLoader.LOAD_ERRORS;

                            List<String> noTask = new ArrayList<>();
                            for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {

                                if (n.getTasks().isEmpty() && !n.isLinkStub()) noTask.add(n.getId().getPath());
                            }
                            int total = errors.size() + noTask.size();
                            if (total == 0) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§a✔ No issues found. " +
                                        QuestTreeRegistry.getAllQuests().size() + " quests loaded cleanly."), false);
                                return 1;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("§e⚠ " + total + " issue(s) found:"),
                                    false);
                            for (String err : errors)
                                ctx.getSource().sendSuccess(() -> Component.literal("§c✗ " + err), false);
                            for (String id : noTask)
                                ctx.getSource()
                                        .sendSuccess(() -> Component.literal(
                                                "§7◦ '" + id + "' has no tasks: will auto-complete on unlock."),
                                                false);
                            return 1;
                        })));
    }

    private static int devSetState(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                   QuestState target,
                                   @Nullable net.minecraft.server.level.ServerPlayer explicitPlayer) {
        net.minecraft.server.level.ServerPlayer sp = explicitPlayer;
        if (sp == null) {
            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer self)) {
                ctx.getSource().sendFailure(Component.literal("Must specify a player when running from console."));
                return 0;
            }
            sp = self;
        }
        String questArg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "quest");
        net.minecraft.resources.ResourceLocation questId;
        try {
            questId = parseQuestArg(questArg);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Invalid quest ID: " + questArg));
            return 0;
        }
        QuestNode node = QuestTreeRegistry.getQuest(questId);
        if (node == null) {
            ctx.getSource().sendFailure(Component.literal("Quest not found: " + questArg));
            return 0;
        }
        final net.minecraft.server.level.ServerPlayer fsp = sp;
        fsp.getCapability(
                QuestCapabilityProvider.PLAYER_QUESTS)
                .ifPresent(data -> {
                    data.setQuestState(questId, target);
                    net.phoenixvine.chronicles.tracker.QuestProgressTracker.updateActiveTracking(
                            fsp.getUUID(), node, target);

                    ChronicleNetwork.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> fsp),
                            new S2CSyncPlayerProgressPacket(
                                    data));
                });
        String label = target == QuestState.COMPLETED ? "§acompleted" :
                target == QuestState.ACTIVE ? "§estarted" :
                        target == QuestState.UNLOCKED ? "§bunlocked" : "§7reset";
        String name = fsp.getName().getString();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Quest " + label + "§r for " + name + ": " + questArg), true);
        return 1;
    }

    private static int devForceTask(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                    @Nullable net.minecraft.server.level.ServerPlayer explicitPlayer) {
        net.minecraft.server.level.ServerPlayer sp = explicitPlayer;
        if (sp == null) {
            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer self)) {
                ctx.getSource().sendFailure(Component.literal("Must specify a player when running from console."));
                return 0;
            }
            sp = self;
        }
        String taskArg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "task");
        net.minecraft.resources.ResourceLocation taskId;
        try {
            taskId = parseQuestArg(taskArg);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Invalid task ID: " + taskArg));
            return 0;
        }
        QuestNode node = QuestTreeRegistry.getTaskOwner(taskId);
        if (node == null) {
            ctx.getSource().sendFailure(Component.literal("Task not found: " + taskArg));
            return 0;
        }

        net.minecraft.server.level.ServerPlayer fsp = sp;
        net.phoenixvine.chronicles.capability.TaskProgressAccess.with(fsp, taskId,
                nbt -> nbt.putBoolean("completed", true));
        QuestProgressTracker.checkAndTryComplete(fsp, node);
        QuestProgressTracker.sendProgressSync(fsp);

        String name = fsp.getName().getString();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Task §aforce-completed§r for " + name + ": " + taskArg), true);
        return 1;
    }

    private static int devResetQuest(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                     @Nullable net.minecraft.server.level.ServerPlayer explicitPlayer) {
        net.minecraft.server.level.ServerPlayer sp = explicitPlayer;
        if (sp == null) {
            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer self)) {
                ctx.getSource().sendFailure(Component.literal("Must specify a player when running from console."));
                return 0;
            }
            sp = self;
        }
        String questArg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "quest");
        net.minecraft.resources.ResourceLocation questId;
        try {
            questId = parseQuestArg(questArg);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Invalid quest ID: " + questArg));
            return 0;
        }
        QuestNode node = QuestTreeRegistry.getQuest(questId);
        if (node == null) {
            ctx.getSource().sendFailure(Component.literal("Quest not found: " + questArg));
            return 0;
        }
        List<net.minecraft.resources.ResourceLocation> taskIds = new java.util.ArrayList<>();
        for (net.phoenixvine.chronicles.model.QuestTask t : node.getTasks()) taskIds.add(t.getTaskId());
        final net.minecraft.server.level.ServerPlayer fsp = sp;
        fsp.getCapability(
                QuestCapabilityProvider.PLAYER_QUESTS)
                .ifPresent(data -> {
                    data.resetQuestProgress(questId, taskIds);

                    if (net.phoenixvine.chronicles.tracker.QuestProgressTracker.prereqsSatisfied(node, data,
                            fsp.getServer())) {
                        net.phoenixvine.chronicles.tracker.QuestProgressTracker.changeQuestState(fsp, node,
                                QuestState.UNLOCKED);
                    }

                    ChronicleNetwork.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> fsp),
                            new S2CSyncPlayerProgressPacket(
                                    data));
                });
        String name = fsp.getName().getString();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Quest §7progress reset§r for " + name + ": " + questArg), true);
        return 1;
    }

    private static int doImport(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                String subfolder) {
        MinecraftServer server = getCachedServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.literal("Server not available."));
            return 0;
        }
        java.nio.file.Path importDir = resolveConfigDir(server).resolve(subfolder);
        if (!java.nio.file.Files.exists(importDir)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Import folder not found: §7" + importDir + "\n§cCreate it and place .snbt files inside."));
            return 0;
        }
        int before = QuestTreeRegistry.getAllQuests().size();
        net.phoenixvine.chronicles.codec.QuestFileLoader.loadAdditiveFromDisk(importDir);
        int added = QuestTreeRegistry.getAllQuests().size() - before;
        net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket syncPacket = new net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket(
                QuestTreeRegistry.getAllQuests(), server);
        int playerCount = 0;
        for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), syncPacket);
            playerCount++;
        }
        final int fp = playerCount;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a✔ Imported §f" + added + " §anew quest(s) from §7" + importDir +
                        (fp > 0 ? " §aand synced to §f" + fp + " §aplayer(s)." : ".")),
                true);
        return added;
    }

    private static int doImportFtb(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                   String subfolder) {
        MinecraftServer server = getCachedServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.literal("Server not available."));
            return 0;
        }
        java.nio.file.Path configDir = resolveConfigDir(server);
        java.nio.file.Path importDir = configDir.resolve(subfolder);
        if (!java.nio.file.Files.exists(importDir)) {
            ctx.getSource().sendFailure(Component.literal(
                    "FTB import folder not found: §7" + importDir +
                            "\n§7Place FTB Quests chapter .snbt files inside and run this command again."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7Importing FTB Quests from §f" + importDir + "§7 - this runs in the background, hang on..."), true);

        Thread importThread = new Thread(() -> {
            long tImportStart = System.currentTimeMillis();
            net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter.ImportResult result;
            try {
                result = net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter.importDirectory(importDir,
                        configDir);
            } catch (Exception e) {
                server.execute(
                        () -> ctx.getSource().sendFailure(Component.literal("§cFTB import failed: " + e.getMessage())));
                return;
            }
            long tImportDone = System.currentTimeMillis();
            System.out.println(
                    "[PhoenixChronicles] Import (file conversion) took " + (tImportDone - tImportStart) + "ms");
            final net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter.ImportResult r = result;
            server.execute(() -> {
                long tReloadStart = System.currentTimeMillis();

                QuestTreeRegistry.clearConfigQuests();
                net.phoenixvine.chronicles.registry.ChapterFlagRegistry.load(configDir);
                net.phoenixvine.chronicles.registry.ChapterPrereqDefaults.load(configDir);
                net.phoenixvine.chronicles.registry.QuestEngineConfig.load(configDir);
                net.phoenixvine.chronicles.registry.RewardTableRegistry.load(configDir);
                net.phoenixvine.chronicles.registry.CategoryRegistry.load(configDir);
                net.phoenixvine.chronicles.codec.QuestFileLoader.loadAdditiveFromDisk(configDir);
                long tReloadDone = System.currentTimeMillis();
                System.out
                        .println("[PhoenixChronicles] Registry reload (on main thread) took " +
                                (tReloadDone - tReloadStart) +
                                "ms" + " (queued " + (tReloadStart - tImportDone) + "ms after import finished)");

                List<String> loadErrors = List.copyOf(net.phoenixvine.chronicles.codec.QuestFileLoader.LOAD_ERRORS);

                boolean localReload = server.isSingleplayer();
                int playerCount = 0;
                if (localReload) {
                    net.phoenixvine.chronicles.network.packet.S2CReloadQuestsFromDiskPacket reloadPacket = new net.phoenixvine.chronicles.network.packet.S2CReloadQuestsFromDiskPacket();
                    for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), reloadPacket);
                        playerCount++;
                    }
                } else {
                    net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket syncPacket = new net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket(
                            QuestTreeRegistry.getAllQuests(), server);
                    for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), syncPacket);
                        playerCount++;
                    }
                }
                long tSyncDone = System.currentTimeMillis();
                System.out.println(
                        "[PhoenixChronicles] Player sync/notify (" +
                                (localReload ? "local reload signal" : "full packet") +
                                ") took " + (tSyncDone - tReloadDone) + "ms");
                final int fp = playerCount;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "§a✔ FTB import: §f" + r.imported() + " §aconverted, §c" + r.skipped() + " §askipped" +
                                (r.category().isEmpty() ? "" : " §8(chapter: §7" + r.category() + "§8)") +
                                (fp > 0 ? " §8· §asynced to §f" + fp + " §aplayer(s)" : "")),
                        true);
                for (String w : r.warnings()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("§e⚠ " + w), false);
                }

                if (!loadErrors.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§c⚠ " + loadErrors.size() + " issue(s) found after reload:"), false);
                    for (String err : loadErrors) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§c  • " + err), false);
                    }
                }
            });
        }, "phoenix-chronicles-ftb-import");
        importThread.setDaemon(true);
        importThread.setPriority(Thread.MIN_PRIORITY);
        importThread.start();
        return 1;
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .ifPresent(oldData -> event.getEntity().getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                        .ifPresent(newData -> newData.deserializeNBT(oldData.serializeNBT())));
        event.getOriginal().invalidateCaps();

        event.getEntity().getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .ifPresent(newData -> net.phoenixvine.chronicles.tracker.QuestProgressTracker
                        .cachePlayerData(event.getEntity().getUUID(), newData));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EnergyStorageTask.clearBlockCache(
                event.getEntity().getUUID());
        net.phoenixvine.chronicles.tracker.QuestProgressTracker.clearInventoryFingerprint(
                event.getEntity().getUUID());
        net.phoenixvine.chronicles.tracker.QuestProgressTracker.clearPlayerDataCache(
                event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {

            QuestProgressTracker.beginLoginSync(serverPlayer.getUUID());

            serverPlayer.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                    .ifPresent(data -> QuestProgressTracker.cachePlayerData(serverPlayer.getUUID(), data));

            Map<ResourceLocation, QuestNode> serverQuests = QuestTreeRegistry.getAllQuests();
            ChronicleNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new S2CSyncQuestsPacket(serverQuests, serverPlayer.getServer()));

            QuestProgressTracker.autoUnlockSatisfiedQuests(serverPlayer);
            QuestProgressTracker.sendProgressSync(serverPlayer);
            sendInitialPooledSync(serverPlayer);
        }
    }

    private static void sendInitialPooledSync(net.minecraft.server.level.ServerPlayer player) {
        TeamKeyResolver.resolve(player).ifPresent(teamKey -> {
            PooledTaskProgress pooled = PooledTaskProgress.get(player.serverLevel());
            Map<ResourceLocation, net.minecraft.nbt.CompoundTag> bulk = new HashMap<>();
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                if (!node.isPooledProgress()) continue;
                for (net.phoenixvine.chronicles.model.QuestTask task : node.getEffectiveTasks(player.getServer())) {
                    bulk.put(task.getTaskId(), pooled.getOrCreate(teamKey, task.getTaskId()));
                }
            }
            if (!bulk.isEmpty()) {
                ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CSyncPooledProgressPacket(bulk));
            }
        });
    }

    @SubscribeEvent
    public static void onTreeReloaded(QuestEvent.TreeReloaded event) {
        MinecraftServer server = getCachedServer();
        if (server == null) return;
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestProgressTracker.autoUnlockSatisfiedQuests(player);
        }
    }
}
