package net.phoenixvine.chronicles.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.event.QuestEvent;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuestProgressTracker {

    private static final java.util.Map<java.util.UUID, Integer> lastInventoryFingerprint = new java.util.HashMap<>();

    private static final java.util.Map<java.util.UUID, PlayerQuestData> capabilityCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static void cachePlayerData(java.util.UUID playerId, PlayerQuestData data) {
        capabilityCache.put(playerId, data);
    }

    public static void clearPlayerDataCache(java.util.UUID playerId) {
        capabilityCache.remove(playerId);
        activeTrackedQuests.remove(playerId);
        lockedSweepTickCounter.remove(playerId);
    }

    @org.jetbrains.annotations.Nullable
    static PlayerQuestData resolveData(Player player) {
        PlayerQuestData cached = capabilityCache.get(player.getUUID());
        if (cached != null) return cached;
        PlayerQuestData resolved = player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
        if (resolved != null) capabilityCache.put(player.getUUID(), resolved);
        return resolved;
    }

    private static final java.util.Map<java.util.UUID, java.util.Set<net.minecraft.resources.ResourceLocation>> activeTrackedQuests = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean needsTicking(QuestState state, QuestNode node) {
        return state == QuestState.UNLOCKED || state == QuestState.ACTIVE ||
                (state == QuestState.COMPLETED && node.isRepeatable());
    }

    public static void updateActiveTracking(java.util.UUID playerId, QuestNode node, QuestState newState) {
        java.util.Set<net.minecraft.resources.ResourceLocation> set = activeTrackedQuests
                .computeIfAbsent(playerId, id -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        if (needsTicking(newState, node)) set.add(node.getId());
        else set.remove(node.getId());
    }

    public static void invalidateAllActiveTracking() {
        activeTrackedQuests.clear();
    }

    private static java.util.Set<net.minecraft.resources.ResourceLocation> activeTrackedSet(Player player,
                                                                                            PlayerQuestData data) {
        return activeTrackedQuests.computeIfAbsent(player.getUUID(), id -> {
            java.util.Set<net.minecraft.resources.ResourceLocation> fresh = java.util.concurrent.ConcurrentHashMap
                    .newKeySet();
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (needsTicking(state, node)) fresh.add(node.getId());
            }
            return fresh;
        });
    }

    private static final java.util.Set<java.util.UUID> loginSyncPlayers = java.util.concurrent.ConcurrentHashMap
            .newKeySet();

    public static void beginLoginSync(java.util.UUID playerId) {
        loginSyncPlayers.add(playerId);
    }

    public static void endLoginSync(java.util.UUID playerId) {
        loginSyncPlayers.remove(playerId);
    }

    public static boolean isInLoginSync(java.util.UUID playerId) {
        return loginSyncPlayers.contains(playerId);
    }

    public static void clearInventoryFingerprint(java.util.UUID playerId) {
        lastInventoryFingerprint.remove(playerId);
        loginSyncPlayers.remove(playerId);
    }

    private static int computeInventoryFingerprint(Player player) {
        var inv = player.getInventory();
        int hash = 1;
        for (net.minecraft.world.item.ItemStack s : inv.items) hash = 31 * hash + fingerprintStack(s);
        for (net.minecraft.world.item.ItemStack s : inv.offhand) hash = 31 * hash + fingerprintStack(s);
        for (net.minecraft.world.item.ItemStack s : inv.armor) hash = 31 * hash + fingerprintStack(s);

        if (player.containerMenu != null) hash = 31 * hash + fingerprintStack(player.containerMenu.getCarried());

        return hash;
    }

    private static int fingerprintStack(net.minecraft.world.item.ItemStack s) {
        if (s.isEmpty()) return 0;
        int h = s.getItem().hashCode() * 31 + s.getCount();
        return s.getTag() != null ? h * 31 + s.getTag().hashCode() : h;
    }

    private static boolean skipInventoryScan(QuestTask task, Player player, boolean invChanged) {
        return task.dependsOnInventory() && !invChanged && !task.isStickyCompleteCached(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;
        int fingerprint = computeInventoryFingerprint(player);
        Integer previous = lastInventoryFingerprint.put(player.getUUID(), fingerprint);
        boolean invChanged = previous == null || previous != fingerprint;

        boolean firstTickThisSession = previous == null;

        PlayerQuestData data = resolveData(player);
        if (data != null) {

            java.util.Set<net.minecraft.resources.ResourceLocation> tracked = activeTrackedSet(player, data);
            for (net.minecraft.resources.ResourceLocation nodeId : tracked) {

                QuestNode node = QuestTreeRegistry.getQuest(nodeId);
                if (node == null) {
                    tracked.remove(nodeId);
                    continue;
                }

                if (node.isFlagDisabled()) continue;

                if (node.getEffectiveVisibility(player.getServer()) == QuestNode.Visibility.DISABLED) continue;

                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);

                if (state == QuestState.COMPLETED && node.isRepeatable()) {
                    if (canRepeatNow(node, data)) {
                        resetForRepeat(player, node, data);
                        state = data.getQuestState(node.getId(), QuestState.LOCKED);
                    }
                }

                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                boolean cancelled = QuestEvent.PlayerTick.hasAnyListeners() &&
                        MinecraftForge.EVENT_BUS.post(new QuestEvent.PlayerTick(player, node));
                if (!cancelled) {

                    List<QuestTask> tasks = node.getEffectiveTasks(player.getServer());
                    for (QuestTask task : tasks) {
                        if (skipInventoryScan(task, player, invChanged)) continue;
                        if (!task.isCompletedFor(player)) task.onTick(player);
                    }
                    checkAndTryComplete(player, node, invChanged, tasks);
                }
            }
        }

        if (data != null) sweepLockedQuestsIfDue(player, data);

        if (firstTickThisSession) {
            endLoginSync(player.getUUID());
        }
    }

    private static final int LOCKED_SWEEP_INTERVAL_TICKS = 100;
    private static final java.util.Map<java.util.UUID, Integer> lockedSweepTickCounter = new java.util.concurrent.ConcurrentHashMap<>();

    private static void sweepLockedQuestsIfDue(Player player, PlayerQuestData data) {
        int count = lockedSweepTickCounter.merge(player.getUUID(), 1, Integer::sum);
        if (count % LOCKED_SWEEP_INTERVAL_TICKS != 0) return;

        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (node.isFlagDisabled()) continue;
            if (data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.LOCKED) continue;
            if (node.getEffectiveVisibility(player.getServer()) == QuestNode.Visibility.DISABLED) continue;

            for (QuestTask task : node.getEffectiveTasks(player.getServer())) {
                if (task instanceof net.phoenixvine.chronicles.tasks.ItemRequirementTask t) {
                    if (t.isSticky()) task.isCompletedFor(player);
                } else if (task instanceof net.phoenixvine.chronicles.tasks.FluidRequirementTask t) {
                    if (t.isSticky()) task.isCompletedFor(player);
                } else if (task instanceof net.phoenixvine.chronicles.tasks.TagItemTask t) {
                    if (t.isSticky()) task.isCompletedFor(player);
                } else if (task instanceof net.phoenixvine.chronicles.tasks.FilterItemTask t) {
                    if (t.isSticky()) task.isCompletedFor(player);
                } else if (task instanceof net.phoenixvine.chronicles.tasks.FilterFluidTask t) {
                    if (t.isSticky()) task.isCompletedFor(player);
                }
            }
        }
    }

    public static void checkAndTryComplete(Player player, QuestNode node) {
        checkAndTryComplete(player, node, true);
    }

    private static void checkAndTryComplete(Player player, QuestNode node, boolean invChanged) {
        checkAndTryComplete(player, node, invChanged, null);
    }

    private static void checkAndTryComplete(Player player, QuestNode node, boolean invChanged,
                                            List<QuestTask> tasksOverride) {
        if (node.isFlagDisabled() || node.getEffectiveVisibility(player.getServer()) == QuestNode.Visibility.DISABLED)
            return;
        PlayerQuestData data = resolveData(player);
        if (data == null) return;

        QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
        if (state == QuestState.COMPLETED) return;

        java.util.List<QuestTask> tasks = tasksOverride != null ? tasksOverride :
                node.getEffectiveTasks(player.getServer());
        int minCount = node.getTaskMinCount();

        boolean complete;
        if (minCount > 0) {

            int done = 0;
            for (QuestTask task : tasks) {
                if (skipInventoryScan(task, player, invChanged)) continue;
                if (task.isCompletedFor(player)) done++;
            }
            complete = done >= minCount;
        } else if (tasks.isEmpty()) {

            if (node.isLinkStub()) {

                QuestNode target = QuestTreeRegistry.getQuest(node.getLinkTarget());
                complete = target != null &&
                        data.getQuestState(target.getId(), QuestState.LOCKED) == QuestState.COMPLETED;
            } else {
                complete = false;
            }
        } else {

            complete = true;
            boolean logThisPass = System.currentTimeMillis() % 1000 < 50;
            for (QuestTask task : tasks) {
                if (task.isOptional()) continue;
                boolean isFilterTask = task instanceof net.phoenixvine.chronicles.tasks.FilterItemTask ||
                        task instanceof net.phoenixvine.chronicles.tasks.FilterFluidTask;
                if (skipInventoryScan(task, player, invChanged)) {
                    if (isFilterTask && logThisPass) {
                        System.out.println("[Phoenix Chronicles] checkAndTryComplete: quest " + node.getId() +
                                " task " + task.getTaskId() + " SKIPPED (dependsOnInventory=" +
                                task.dependsOnInventory() + " invChanged=" + invChanged + " stickyCached=" +
                                task.isStickyCompleteCached(player) + ")");
                    }
                    complete = false;
                    break;
                }
                boolean taskDone = task.isCompletedFor(player);
                if (isFilterTask && logThisPass) {
                    System.out.println("[Phoenix Chronicles] checkAndTryComplete: quest " + node.getId() +
                            " task " + task.getTaskId() + " isCompletedFor=" + taskDone + " stickyCached=" +
                            task.isStickyCompleteCached(player) + " progress=" + task.getProgressString(player));
                }
                if (!taskDone) {
                    complete = false;
                    break;
                }
            }
        }

        if (complete && (state == QuestState.UNLOCKED || state == QuestState.ACTIVE)) {
            changeQuestState(player, node, QuestState.COMPLETED);
        }
    }

    public static void changeQuestState(Player player, QuestNode node, QuestState newState) {
        PlayerQuestData data = resolveData(player);
        if (data == null) return;

        QuestState oldState = data.getQuestState(node.getId(), QuestState.LOCKED);
        if (oldState == newState) return;

        data.setQuestState(node.getId(), newState);
        updateActiveTracking(player.getUUID(), node, newState);
        MinecraftForge.EVENT_BUS.post(new QuestEvent.StateChanged(player, node, oldState, newState));

        if (newState == QuestState.COMPLETED) {
            data.recordCompletion(node.getId());

            processChildCascades(player, node);
            if (node.isShared() && player instanceof net.minecraft.server.level.ServerPlayer sp)
                propagateSharedCompletion(sp, node);
            if (node.isAutoClaimRewards() && player instanceof ServerPlayer sp)
                grantRewards(sp, node);
        } else if (newState == QuestState.UNLOCKED && !node.getEffectiveTasks(player.getServer()).isEmpty()) {

            checkAndTryComplete(player, node);
        }

        sendProgressSync(player);
    }

    private static void propagateSharedCompletion(net.minecraft.server.level.ServerPlayer source, QuestNode node) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("phoenix_guilds") &&
                GuildsSharedCompletion.propagate(source, node)) {
            return;
        }

        if (net.minecraftforge.fml.ModList.get().isLoaded("ftbteams") &&
                FtbTeamsSharedCompletion.propagate(source, node)) {
            return;
        }

        net.minecraft.world.scores.Team team = source.getTeam();
        if (team == null) return;
        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null) return;
        for (String memberName : team.getPlayers()) {
            net.minecraft.server.level.ServerPlayer member = server.getPlayerList().getPlayerByName(memberName);
            if (member == null || member.getUUID().equals(source.getUUID())) continue;
            PlayerQuestData data = resolveData(member);
            if (data != null && data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) {
                changeQuestState(member, node, QuestState.COMPLETED);
            }
        }
    }

    private static void processChildCascades(Player player, QuestNode completedNode) {
        PlayerQuestData data = resolveData(player);
        if (data == null) return;

        java.util.Set<QuestNode> candidates = new java.util.LinkedHashSet<>(completedNode.getChildren());
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (node.getPrerequisites().contains(completedNode)) candidates.add(node);
        }

        for (QuestNode child : candidates) {
            if (data.getQuestState(child.getId(), QuestState.LOCKED) != QuestState.LOCKED) continue;

            if (prereqsSatisfied(child, data, player.getServer())) {
                changeQuestState(player, child, QuestState.UNLOCKED);

                if (!child.getEffectiveTasks(player.getServer()).isEmpty()) {
                    checkAndTryComplete(player, child);
                }
            }
        }
    }

    public static void autoUnlockSatisfiedQuests(ServerPlayer player) {
        PlayerQuestData data = resolveData(player);
        if (data == null) return;

        for (QuestNode node : net.phoenixvine.chronicles.registry.QuestTreeRegistry.getAllQuests().values()) {
            if (node.isFlagDisabled()) continue;
            if (node.getEffectiveVisibility(player.getServer()) == QuestNode.Visibility.DISABLED) continue;
            if (data.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.LOCKED) {
                if (prereqsSatisfied(node, data, player.getServer())) {
                    changeQuestState(player, node, QuestState.UNLOCKED);
                }
            }
        }
    }

    public static boolean prereqsSatisfied(QuestNode node, PlayerQuestData data,
                                           net.minecraft.server.MinecraftServer server) {
        List<QuestNode> prereqs = node.getPrerequisites();
        if (prereqs.isEmpty()) return true;

        for (QuestNode p : prereqs) {
            if (node.isPrereqForbidden(p.getId())) {
                if (data.getQuestState(p.getId(), QuestState.LOCKED) == QuestState.COMPLETED) return false;
            }
        }

        List<QuestNode> active = new java.util.ArrayList<>();
        for (QuestNode p : prereqs) {
            if (p.isFlagDisabled()) continue;
            if (node.isPrereqForbidden(p.getId())) continue;
            if (node.isPrereqCosmetic(p.getId())) continue;
            if (p.getEffectiveVisibility(server) == QuestNode.Visibility.DISABLED && !p.isDisabledBlocksChildren())
                continue;
            active.add(p);
        }
        if (active.isEmpty()) return true;

        if (node.hasPerPrereqFlags()) {
            List<QuestNode> required = new java.util.ArrayList<>();
            List<QuestNode> optional = new java.util.ArrayList<>();
            for (QuestNode p : active) {
                if (node.isPrereqRequired(p.getId())) required.add(p);
                else optional.add(p);
            }

            for (QuestNode p : required) {
                if (data.getQuestState(p.getId(), QuestState.LOCKED) != QuestState.COMPLETED) return false;
            }

            if (!optional.isEmpty()) {
                int minCount = node.getEffectiveOptionalPrereqMinCount();
                if (minCount < 0) return true;
                int doneOptional = 0;
                for (QuestNode p : optional) {
                    if (data.getQuestState(p.getId(), QuestState.LOCKED) == QuestState.COMPLETED) doneOptional++;
                }
                int need = (minCount == 0) ? optional.size() : minCount;
                return doneOptional >= need;
            }
            return true;
        }

        if (node.getEffectiveRequireAllPrerequisites()) {
            for (QuestNode prereq : active) {
                if (data.getQuestState(prereq.getId(), QuestState.LOCKED) != QuestState.COMPLETED) return false;
            }
            return true;
        } else {
            for (QuestNode prereq : active) {
                if (data.getQuestState(prereq.getId(), QuestState.LOCKED) == QuestState.COMPLETED) return true;
            }
            return false;
        }
    }

    public static boolean canRepeatNow(QuestNode node, PlayerQuestData data) {
        long last = data.getLastCompletedTime(node.getId());
        if (last == 0) return true;

        return switch (node.getRepeatMode()) {
            case NONE -> false;
            case INFINITE -> true;
            case DAILY -> !isSameDay(last, System.currentTimeMillis());
            case COOLDOWN -> System.currentTimeMillis() - last >=
                    TimeUnit.HOURS.toMillis(node.getRepeatCooldownHours());
        };
    }

    private static boolean isSameDay(long epochA, long epochB) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate a = Instant.ofEpochMilli(epochA).atZone(zone).toLocalDate();
        LocalDate b = Instant.ofEpochMilli(epochB).atZone(zone).toLocalDate();
        return a.equals(b);
    }

    private static void resetForRepeat(Player player, QuestNode node, PlayerQuestData data) {
        for (QuestTask task : node.getEffectiveTasks(player.getServer())) {
            net.phoenixvine.chronicles.capability.TaskProgressAccess.clear(player, task.getTaskId());
        }

        data.clearClaimedRewards(node.getId());
        data.clearChosenRewardIndex(node.getId());

        changeQuestState(player, node, QuestState.UNLOCKED);
    }

    public static void grantRewards(ServerPlayer player, QuestNode node) {
        PlayerQuestData data = resolveData(player);
        if (data == null) return;
        if (data.hasClaimedRewards(node.getId())) return;
        if (MinecraftForge.EVENT_BUS.post(new QuestEvent.RewardClaimed(player, node))) return;
        for (QuestReward reward : node.getEffectiveRewards(player.getServer())) {
            reward.grant(player);
        }
        consumeTaskProgress(player, node);
        data.markRewardsClaimed(node.getId());
        sendProgressSync(player);
    }

    private static void consumeTaskProgress(ServerPlayer player, QuestNode node) {
        for (QuestTask task : node.getEffectiveTasks(player.getServer())) {
            task.tryConsume(player);
        }
    }

    public static QuestState getQuestState(Player player, QuestNode node) {
        PlayerQuestData data = resolveData(player);
        return data != null ? data.getQuestState(node.getId(), QuestState.LOCKED) : QuestState.LOCKED;
    }

    public static void sendProgressSync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            boolean initialSync = isInLoginSync(serverPlayer.getUUID());
            PlayerQuestData data = resolveData(serverPlayer);
            if (data != null) {
                ChronicleNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new S2CSyncPlayerProgressPacket(data, initialSync));
            }
        }
    }

    public static void grantChosenReward(ServerPlayer player, QuestNode node, int choiceIndex) {
        PlayerQuestData data = resolveData(player);
        if (data == null) return;
        if (data.hasClaimedRewards(node.getId())) return;
        List<QuestReward> effectiveRewards = node.getEffectiveRewards(player.getServer());
        if (choiceIndex < 0 || choiceIndex >= effectiveRewards.size()) return;
        effectiveRewards.get(choiceIndex).grant(player);
        consumeTaskProgress(player, node);
        data.setChosenRewardIndex(node.getId(), choiceIndex);
        data.markRewardsClaimed(node.getId());
        sendProgressSync(player);
    }
}
