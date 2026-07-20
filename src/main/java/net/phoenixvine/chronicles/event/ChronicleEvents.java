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
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.codec.ChronicleDataLoader;
import net.phoenixvine.chronicles.codec.KubeJsTaskTypeLoader;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket;
import net.phoenixvine.chronicles.registry.CategoryFlagRegistry;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.registry.RewardTableRegistry;
import net.phoenixvine.chronicles.tasks.*;
import net.phoenixvine.chronicles.tasks.BlockBreakTask;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChronicleEvents {

    public static MinecraftServer getCachedServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    /**
     * True once onServerStarting() has run for the currently-live server instance. Used by
     * ChronicleDataLoader.apply() to tell "this is the initial world-boot datapack reload, which
     * onServerStarting() is about to (or already did) handle its own .snbt load for" apart from
     * "this is a later, manually-triggered /reload, which onServerStarting() will never fire
     * again for" - relying on getCachedServer() being null during the very first boot reload
     * (as the old comment here assumed) isn't a guaranteed timing, and getting it wrong meant a
     * full duplicate disk-walk-and-parse of every .snbt file on every world boot.
     */
    private static volatile boolean hasServerStarted = false;

    public static boolean hasServerFullyStarted() {
        return hasServerStarted;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ChronicleDataLoader());
    }

    /**
     * Returns the world-specific config dir if it exists (saves/<world>/phoenix_chronicles),
     * otherwise falls back to the global config dir. This lets pack devs ship per-world
     * quest configs in singleplayer worlds without affecting other worlds on the same instance.
     */
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
        PhoenixTaskRegistry.registerBuiltins();
        KubeJsTaskTypeLoader.load(configDir); // register KubeJS-defined task types after builtins
        PhoenixQuestFlags.invalidateCaches(); // flush file-backed flag caches before quest load
        CategoryFlagRegistry.load(configDir);
        net.phoenixvine.chronicles.registry.CategoryPrereqDefaults.load(configDir);
        net.phoenixvine.chronicles.registry.QuestEngineConfig.load(configDir);
        RewardTableRegistry.load(configDir);
        net.phoenixvine.chronicles.registry.ChapterFolderRegistry.load(configDir);
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
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(event.getCrafting().getItem());
        if (itemId == null) return;
        int amount = event.getCrafting().getCount();

        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                for (Object task : node.getTasks()) {
                    if (task instanceof CraftItemTask craftTask) {
                        // FIXED: Passing player instance context safely down to mutable capability layers
                        craftTask.onItemCrafted(player, itemId, amount);
                    }
                }
                // Check if this crafting event satisfied the remaining conditions for the quest
                QuestProgressTracker.checkAndTryComplete(player, node);
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
                for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                    QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                    if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                    for (Object task : node.getTasks()) {
                        if (task instanceof KillEntityTask killTask) {
                            // FIXED: Added player context to comply with refactored stateless parameters
                            killTask.onEntityKilled(player, entityId);
                        }
                    }
                    QuestProgressTracker.checkAndTryComplete(player, node);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        net.minecraft.world.level.block.Block broken = event.getState().getBlock();
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;
                boolean changed = false;
                for (Object task : node.getTasks()) {
                    if (task instanceof BlockBreakTask breakTask) {
                        breakTask.onBlockBroken(player, broken);
                        changed = true;
                    }
                }
                if (changed) QuestProgressTracker.checkAndTryComplete(player, node);
            }
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

        // Populate the per-player energy cache for EnergyStorageTask (BLOCK source).
        // Runs on both sides: client side so the quest UI can read cached values immediately,
        // server side so server-tick progress checks also work.
        // Guarded - this fires on EVERY right-click on EVERY block, and its BLOCK-source path
        // reads GTCEu's energy capability internally, so without this check any right-click at
        // all would throw NoClassDefFoundError the moment GTCEu isn't installed (GTCEu is a soft
        // dependency now - see GTCEuCompat).
        if (net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat.isAvailable()) {
            EnergyStorageTask.onBlockRightClicked(
                    player, event.getLevel(), event.getPos());
        }

        if (player.level().isClientSide) return;
        handleBlockEvent(player, clicked, "RIGHT_CLICK");
    }

    private static void handleBlockEvent(Player player, Block block, String action) {
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;
                boolean changed = false;
                for (Object task : node.getTasks()) {
                    if (task instanceof BlockInteractTask blockTask) {
                        blockTask.onBlockEvent(player, block, action);
                        changed = true;
                    }
                }
                if (changed) QuestProgressTracker.checkAndTryComplete(player, node);
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        var dimension = event.getTo();
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;
                boolean changed = false;
                for (Object task : node.getTasks()) {
                    if (task instanceof DimensionTask dimTask) {
                        dimTask.onChangedDimension(player, dimension);
                        changed = true;
                    }
                }
                if (changed) QuestProgressTracker.checkAndTryComplete(player, node);
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var questArg = com.mojang.brigadier.arguments.StringArgumentType.string();

        // ── Player-accessible subcommands (no permission required) ────────────
        dispatcher.register(Commands.literal("chronicles")

                // /chronicles status <quest> — check your own quest state
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
                                        questId = new net.minecraft.resources.ResourceLocation(qStr);
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

                // /chronicles emergency <quest> — get emergency items for an active quest
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
                                        questId = new net.minecraft.resources.ResourceLocation(qStr);
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

                // ── Op-only subcommands (permission level 2) ──────────────────

                // /chronicles complete <quest> [<player>]
                .then(Commands.literal("complete")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> devSetState(ctx, QuestState.COMPLETED, null))
                                .then(Commands
                                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> devSetState(ctx, QuestState.COMPLETED,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                        "player"))))))

                // /chronicles unlock <quest> [<player>]
                .then(Commands.literal("unlock")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> devSetState(ctx, QuestState.UNLOCKED, null))
                                .then(Commands
                                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> devSetState(ctx, QuestState.UNLOCKED,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                        "player"))))))

                // /chronicles reset <quest> [<player>] - full reset (state + task progress +
                // claimed rewards + completion timestamp), not just a state flip to LOCKED like
                // the other devSetState commands - see devResetQuest().
                .then(Commands.literal("reset")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> devResetQuest(ctx, null))
                                .then(Commands
                                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> devResetQuest(ctx,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                        "player"))))))

                // /chronicles active <quest> [<player>]
                .then(Commands.literal("active")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("quest", questArg)
                                .executes(ctx -> devSetState(ctx, QuestState.ACTIVE, null))
                                .then(Commands
                                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> devSetState(ctx, QuestState.ACTIVE,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                        "player"))))))

                // /chronicles reload — hot-reload config-dir quests and sync to all online players
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
                            CategoryFlagRegistry.load(configDir);
                            net.phoenixvine.chronicles.registry.CategoryPrereqDefaults.load(configDir);
                            net.phoenixvine.chronicles.registry.QuestEngineConfig.load(configDir);
                            RewardTableRegistry.load(configDir);
                            net.phoenixvine.chronicles.registry.ChapterFolderRegistry.load(configDir);
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

                // /chronicles export — snapshot all loaded quests to a timestamped folder
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

                // /chronicles import [subfolder] — additive load from a subfolder (default: "import/")
                .then(Commands.literal("import")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> doImport(ctx, "import"))
                        .then(Commands
                                .argument("subfolder", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> doImport(ctx,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                                "subfolder")))))

                // /chronicles import-ftb [subfolder] — convert FTB Quests .snbt files to Chronicles format
                .then(Commands.literal("import-ftb")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> doImportFtb(ctx, "ftb_import"))
                        .then(Commands
                                .argument("subfolder", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> doImportFtb(ctx,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                                "subfolder")))))

                // /chronicles validate — reports load errors + common issues
                .then(Commands.literal("validate")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            List<String> errors = QuestFileLoader.LOAD_ERRORS;
                            // Also check for quests with no tasks (will auto-complete on unlock)
                            List<String> noTask = new ArrayList<>();
                            for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
                                // Link stubs are intentionally task-less placeholders pointing at
                                // a real quest elsewhere - not a validation issue.
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
                                                "§7◦ '" + id + "' has no tasks — will auto-complete on unlock."),
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
            questId = new net.minecraft.resources.ResourceLocation(questArg);
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
                    // Sync updated progress back to the client
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

    /**
     * Full reset: state, every task's progress, claimed-rewards flag, chosen-reward index, and
     * completion timestamp - unlike devSetState(..., LOCKED), which only flips the state key and
     * leaves old task progress/claimed rewards sitting underneath a re-lockable quest.
     */
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
            questId = new net.minecraft.resources.ResourceLocation(questArg);
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
        // A real pack import (hundreds of chapter->quest conversions, each writing its own small
        // file) is much too heavy to run synchronously inside the command handler on the main
        // server thread - doing so blocked the thread long enough to miss keepalives and
        // disconnect players. Moving it to Util.backgroundExecutor() (a shared pool also used by
        // chunk loading and other vanilla/forge subsystems) wasn't enough on its own - the import
        // was still observed timing out players WHILE the background thread was mid-write, before
        // the reload/sync step even started. Use a dedicated, explicitly low-priority thread
        // instead, so hundreds of small file writes (and whatever per-file overhead the OS/AV
        // adds) never compete with the game's own thread scheduling for CPU time.
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
            System.out.println("[PhoenixCore] Import (file conversion) took " + (tImportDone - tImportStart) + "ms");
            final net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter.ImportResult r = result;
            server.execute(() -> {
                long tReloadStart = System.currentTimeMillis();
                // Hot-reload so freshly written files appear immediately
                QuestTreeRegistry.clearConfigQuests();
                net.phoenixvine.chronicles.registry.CategoryFlagRegistry.load(configDir);
                net.phoenixvine.chronicles.registry.CategoryPrereqDefaults.load(configDir);
                net.phoenixvine.chronicles.registry.QuestEngineConfig.load(configDir);
                net.phoenixvine.chronicles.registry.RewardTableRegistry.load(configDir);
                net.phoenixvine.chronicles.registry.ChapterFolderRegistry.load(configDir);
                net.phoenixvine.chronicles.codec.QuestFileLoader.loadAdditiveFromDisk(configDir);
                long tReloadDone = System.currentTimeMillis();
                System.out
                        .println("[PhoenixCore] Registry reload (on main thread) took " + (tReloadDone - tReloadStart) +
                                "ms" + " (queued " + (tReloadStart - tImportDone) + "ms after import finished)");

                // Capture the server's own load-error snapshot BEFORE telling any client to run
                // its own independent reload below - QuestFileLoader.LOAD_ERRORS is a single
                // shared static list, and the client's reloadAllQuestsFromDisk() also clears +
                // repopulates it on a different thread, so reading it after dispatching would race.
                List<String> loadErrors = List.copyOf(net.phoenixvine.chronicles.codec.QuestFileLoader.LOAD_ERRORS);

                // Encoding a full S2CSyncQuestsPacket for an entire freshly-imported pack (900+
                // quests, each with tasks/rewards/prerequisites) synchronously on the main thread
                // was heavy enough on its own to cause keepalive timeouts on a big import. For an
                // integrated/singleplayer server the client shares the exact same config
                // directory the import just wrote to, so it's far cheaper to tell it to re-read
                // those files itself than to re-serialize and transmit them all over the network.
                // Dedicated servers still get the full sync, since remote clients have no local
                // copy of the server's files to re-read.
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
                        "[PhoenixCore] Player sync/notify (" + (localReload ? "local reload signal" : "full packet") +
                                ") took " + (tSyncDone - tReloadDone) + "ms");
                final int fp = playerCount;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "§a✔ FTB import: §f" + r.imported() + " §aconverted, §c" + r.skipped() + " §askipped" +
                                (r.category().isEmpty() ? "" : " §8(category: §7" + r.category() + "§8)") +
                                (fp > 0 ? " §8· §asynced to §f" + fp + " §aplayer(s)" : "")),
                        true);
                for (String w : r.warnings()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("§e⚠ " + w), false);
                }
                // Print load-time issues (broken quest links, missing prereqs, duplicate task
                // ids) right here instead of requiring a separate /chronicles validate call -
                // these only show up after the post-import reload above, so the import summary
                // alone can look clean even when something didn't actually come through right.
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

    /**
     * Copies quest progress to the new player entity on death/respawn.
     * Without this, every death wipes all progress because Forge creates a fresh entity.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .ifPresent(oldData -> event.getEntity().getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                        .ifPresent(newData -> newData.deserializeNBT(oldData.serializeNBT())));
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EnergyStorageTask.clearBlockCache(
                event.getEntity().getUUID());
        net.phoenixvine.chronicles.tracker.QuestProgressTracker.clearInventoryFingerprint(
                event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            Map<ResourceLocation, QuestNode> serverQuests = QuestTreeRegistry.getAllQuests();
            ChronicleNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new S2CSyncQuestsPacket(serverQuests, serverPlayer.getServer()));

            // Auto-unlock any quest whose prerequisites are already satisfied (covers root
            // quests that have no parent and would otherwise stay LOCKED forever).
            serverPlayer.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                for (QuestNode node : serverQuests.values()) {
                    if (node.isFlagDisabled()) continue;
                    if (node.getEffectiveVisibility(serverPlayer.getServer()) == QuestNode.Visibility.DISABLED)
                        continue;
                    if (data.getQuestState(node.getId(), net.phoenixvine.chronicles.model.QuestState.LOCKED) ==
                            net.phoenixvine.chronicles.model.QuestState.LOCKED) {
                        if (net.phoenixvine.chronicles.tracker.QuestProgressTracker.prereqsSatisfied(node, data,
                                serverPlayer.getServer())) {
                            net.phoenixvine.chronicles.tracker.QuestProgressTracker
                                    .changeQuestState(serverPlayer, node,
                                            net.phoenixvine.chronicles.model.QuestState.UNLOCKED);
                        }
                    }
                }
                // Send player progress so the client HUD and screens have live data
                ChronicleNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new S2CSyncPlayerProgressPacket(data));
            });
        }
    }
}
