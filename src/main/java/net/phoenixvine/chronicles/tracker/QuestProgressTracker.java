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
import net.phoenixvine.guilds.data.GuildManager;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuestProgressTracker {

    // ── Inventory-change dirty check ──────────────────────────────────────────
    //
    // Forge has no built-in "player inventory changed" event (only narrower ones like
    // ItemPickupEvent/LivingEquipmentChangeEvent that don't cover every way a stack can change -
    // crafting, container transfers, commands, capability-driven NBT mutation, etc.), so there's
    // nothing to subscribe to that would reliably fire on every relevant change. This is the
    // homegrown substitute: a cheap per-player fingerprint of every tracked slot, recomputed once
    // per tick and compared to last tick's - inventory/tag/fluid/energy-in-item tasks
    // (QuestTask#dependsOnInventory) skip their (expensive, full-slot-scan +
    // capability-lookup) isCompletedFor entirely on ticks where it didn't change, since their
    // result provably can't have changed either. This is what was causing FTBQ-style per-tick
    // lag: every active item/fluid/energy task was re-scanning every player's whole inventory
    // 20 times a second regardless of whether the player had touched anything.
    private static final java.util.Map<java.util.UUID, Integer> lastInventoryFingerprint = new java.util.HashMap<>();

    /** Call on player disconnect to avoid an unbounded per-UUID leak across server uptime. */
    public static void clearInventoryFingerprint(java.util.UUID playerId) {
        lastInventoryFingerprint.remove(playerId);
    }

    /**
     * Hashes item identity + count + NBT (CompoundTag has real content-based equals/hashCode,
     * not identity) for every main/offhand/armor slot - cheap relative to the capability lookups
     * and full-inventory walks it's replacing, and sensitive to the same things
     * isCompletedFor's own scans care about, including in-place NBT mutation (e.g. a fluid/energy
     * item's stored amount changing) with no slot/count change at all.
     */
    private static int computeInventoryFingerprint(Player player) {
        var inv = player.getInventory();
        int hash = 1;
        for (net.minecraft.world.item.ItemStack s : inv.items) hash = 31 * hash + fingerprintStack(s);
        for (net.minecraft.world.item.ItemStack s : inv.offhand) hash = 31 * hash + fingerprintStack(s);
        for (net.minecraft.world.item.ItemStack s : inv.armor) hash = 31 * hash + fingerprintStack(s);
        return hash;
    }

    private static int fingerprintStack(net.minecraft.world.item.ItemStack s) {
        if (s.isEmpty()) return 0;
        int h = s.getItem().hashCode() * 31 + s.getCount();
        return s.getTag() != null ? h * 31 + s.getTag().hashCode() : h;
    }

    /**
     * True if it's safe to skip calling task.isCompletedFor() entirely this check: the task only
     * depends on inventory contents, those haven't changed since the last check, AND it hasn't
     * already latched sticky-complete (in which case isCompletedFor is cheap anyway and calling
     * it gets the real, already-cached answer instead of an assumed false - see
     * QuestTask#isStickyCompleteCached's doc for why that distinction matters for multi-task
     * quests).
     */
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

        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            // Snapshot the values instead of iterating the registry's live map directly - the
            // loop body (completion cascades, repeat-resets, QuestEvent.PlayerTick listeners)
            // can end up mutating the registry (e.g. a listener dynamically injecting a new
            // quest node), which throws ConcurrentModificationException mid-iteration. This
            // only ever surfaced under enough simultaneous quest-state churn to actually hit
            // it in the same tick - a huge fresh import (1000+ quests all going live at once,
            // lots of completion cascades firing together) makes that collision far more likely.
            for (QuestNode node : new java.util.ArrayList<>(QuestTreeRegistry.getAllQuests().values())) {
                // Flag-disabled quests are fully inert — skip all processing
                if (node.isFlagDisabled()) continue;
                // DISABLED visibility quests are shown but cannot be completed
                if (node.getEffectiveVisibility(player.getServer()) == QuestNode.Visibility.DISABLED) continue;

                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);

                // Reset a completed repeatable quest if its cooldown has elapsed
                if (state == QuestState.COMPLETED && node.isRepeatable()) {
                    if (canRepeatNow(node, data)) {
                        resetForRepeat(player, node, data);
                        state = data.getQuestState(node.getId(), QuestState.LOCKED);
                    }
                }

                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                if (!MinecraftForge.EVENT_BUS.post(new QuestEvent.PlayerTick(player, node))) {
                    // Let polling tasks update their state (biome, structure, etc.)
                    for (QuestTask task : node.getEffectiveTasks(player.getServer())) {
                        if (skipInventoryScan(task, player, invChanged)) continue;
                        if (!task.isCompletedFor(player)) task.onTick(player);
                    }
                    checkAndTryComplete(player, node, invChanged);
                }
            }
        });
    }

    // ── Completion check ──────────────────────────────────────────────────────

    public static void checkAndTryComplete(Player player, QuestNode node) {
        // Always treat inventory as "changed" here - this overload is the one every event-driven
        // caller uses (crafting, killing, right-clicking, cascades, manual packets), which is
        // exactly the moment something relevant DID just happen and a real check is wanted, not
        // the steady-state per-tick poll the invChanged gate below exists for.
        checkAndTryComplete(player, node, true);
    }

    private static void checkAndTryComplete(Player player, QuestNode node, boolean invChanged) {
        // Flag-disabled or visibility-DISABLED quests can never be completed
        if (node.isFlagDisabled() || node.getEffectiveVisibility(player.getServer()) == QuestNode.Visibility.DISABLED)
            return;
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
            if (state == QuestState.COMPLETED) return;

            java.util.List<QuestTask> tasks = node.getEffectiveTasks(player.getServer());
            int minCount = node.getTaskMinCount();

            // An inventory-dependent task that hasn't been re-scanned this tick (because
            // inventory didn't change) can only still be "not completed" UNLESS it already
            // latched sticky-complete on some earlier tick - skipInventoryScan() checks that
            // cached flag first, so this is exact, not an approximation: either the task's real
            // answer is cheap to get (already latched) or it provably can't have changed.
            boolean complete;
            if (minCount > 0) {
                // X-of-N mode: count any completed task (optional or not)
                int done = 0;
                for (QuestTask task : tasks) {
                    if (skipInventoryScan(task, player, invChanged)) continue;
                    if (task.isCompletedFor(player)) done++;
                }
                complete = done >= minCount;
            } else {
                // Default: all non-optional tasks must be done
                complete = true;
                for (QuestTask task : tasks) {
                    if (task.isOptional()) continue;
                    if (skipInventoryScan(task, player, invChanged)) {
                        complete = false;
                        break;
                    }
                    if (!task.isCompletedFor(player)) {
                        complete = false;
                        break;
                    }
                }
            }

            if (complete && (state == QuestState.UNLOCKED || state == QuestState.ACTIVE)) {
                changeQuestState(player, node, QuestState.COMPLETED);
            }
        });
    }

    // ── State mutation ────────────────────────────────────────────────────────

    public static void changeQuestState(Player player, QuestNode node, QuestState newState) {
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            QuestState oldState = data.getQuestState(node.getId(), QuestState.LOCKED);
            if (oldState == newState) return;

            data.setQuestState(node.getId(), newState);
            MinecraftForge.EVENT_BUS.post(new QuestEvent.StateChanged(player, node, oldState, newState));

            if (newState == QuestState.COMPLETED) {
                data.recordCompletion(node.getId());
                // Notification is the client-side toast system (see QuestToastManager, fired
                // from S2CSyncPlayerProgressPacket's state-diff below), not chat - a chat message
                // here fired unconditionally for every completion, including the whole backlog
                // of quests that unlock-then-instantly-complete during a fresh join's prereq
                // cascade (see onPlayerLogin), which is what was spamming chat on first login.
                // The toast packet already has its own suppression for that case.
                processChildCascades(player, node);
                if (node.isShared() && player instanceof net.minecraft.server.level.ServerPlayer sp)
                    propagateSharedCompletion(sp, node);
                if (node.isAutoClaimRewards() && player instanceof ServerPlayer sp)
                    grantRewards(sp, node);
            }

            sendProgressSync(player);
        });
    }

    // ── Shared quest team cascade ─────────────────────────────────────────────

    private static void propagateSharedCompletion(net.minecraft.server.level.ServerPlayer source, QuestNode node) {
        // Try Phoenix Guilds first (lightweight built-in teams) - guarded, since Phoenix Guilds
        // is meant to be an optional compat, not a hard dependency (see TeamKeyResolver's own
        // matching fix). Without this check, any pack with a "shared" quest but no Phoenix
        // Guilds installed would crash with NoClassDefFoundError the moment that quest completed.
        if (net.minecraftforge.fml.ModList.get().isLoaded("phoenix_guilds")) {
            GuildManager guildMgr = GuildManager.get(source.getServer().overworld());
            var pTeam = guildMgr.getGuildFor(source.getUUID());
            if (pTeam.isPresent()) {
                for (java.util.UUID memberUUID : pTeam.get().getMembers()) {
                    if (memberUUID.equals(source.getUUID())) continue;
                    net.minecraft.server.level.ServerPlayer member = source.getServer().getPlayerList()
                            .getPlayer(memberUUID);
                    if (member == null) continue;
                    member.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                        if (data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED)
                            changeQuestState(member, node, QuestState.COMPLETED);
                    });
                }
                return;
            }
        }

        // Try FTB Teams next (party/server teams only, not the per-player default team)
        if (FTBTeamsAPI.api().isManagerLoaded()) {
            var opt = FTBTeamsAPI.api().getManager()
                    .getTeamForPlayerID(source.getUUID());
            if (opt.isPresent()) {
                var team = opt.get();
                if (team.isPartyTeam() || team.isServerTeam()) {
                    for (net.minecraft.server.level.ServerPlayer member : team.getOnlineMembers()) {
                        if (member.getUUID().equals(source.getUUID())) continue;
                        member.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                            if (data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED)
                                changeQuestState(member, node, QuestState.COMPLETED);
                        });
                    }
                    return;
                }
            }
        }

        // Fallback: Minecraft scoreboard teams
        net.minecraft.world.scores.Team team = source.getTeam();
        if (team == null) return;
        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null) return;
        for (String memberName : team.getPlayers()) {
            net.minecraft.server.level.ServerPlayer member = server.getPlayerList().getPlayerByName(memberName);
            if (member == null || member.getUUID().equals(source.getUUID())) continue;
            member.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
                if (data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED)
                    changeQuestState(member, node, QuestState.COMPLETED);
            });
        }
    }

    // ── Prerequisite-aware cascade ────────────────────────────────────────────

    private static void processChildCascades(Player player, QuestNode completedNode) {
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            for (QuestNode child : completedNode.getChildren()) {
                if (data.getQuestState(child.getId(), QuestState.LOCKED) != QuestState.LOCKED) continue;

                if (prereqsSatisfied(child, data, player.getServer())) {
                    changeQuestState(player, child, QuestState.UNLOCKED);

                    // Auto-complete if the player already satisfied the child quest's tasks
                    if (!child.getEffectiveTasks(player.getServer()).isEmpty()) {
                        checkAndTryComplete(player, child);
                    }
                }
            }
        });
    }

    /**
     * Checks whether a quest's prerequisites are satisfied.
     *
     * If the node has per-prereq required flags set:
     * - All REQUIRED prereqs must be COMPLETED
     * - At least {@code optionalPrereqMinCount} OPTIONAL prereqs must be COMPLETED
     * (0 = all optional must be done; -1 = none needed)
     *
     * Otherwise falls back to the legacy gate:
     * requireAllPrerequisites=true → every prereq must be COMPLETED (AND)
     * requireAllPrerequisites=false → at least one prereq must be COMPLETED (OR)
     *
     * DISABLED quests are skipped — they don't gate their children.
     */
    public static boolean prereqsSatisfied(QuestNode node, PlayerQuestData data,
                                           net.minecraft.server.MinecraftServer server) {
        List<QuestNode> prereqs = node.getPrerequisites();
        if (prereqs.isEmpty()) return true;

        // Check FORBIDDEN prereqs first — any completed forbidden prereq blocks unlock
        for (QuestNode p : prereqs) {
            if (node.isPrereqForbidden(p.getId())) {
                if (data.getQuestState(p.getId(), QuestState.LOCKED) == QuestState.COMPLETED) return false;
            }
        }

        // Filter out flag-disabled, forbidden, cosmetic-only, and non-blocking DISABLED prereqs
        // from the positive gate. A DISABLED prereq with disabledBlocksChildren=true is kept
        // in the gate intentionally. Cosmetic prereqs are drawn but never gate — see
        // QuestNode#isPrereqCosmetic.
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

        // Per-prereq flag mode
        if (node.hasPerPrereqFlags()) {
            List<QuestNode> required = new java.util.ArrayList<>();
            List<QuestNode> optional = new java.util.ArrayList<>();
            for (QuestNode p : active) {
                if (node.isPrereqRequired(p.getId())) required.add(p);
                else optional.add(p);
            }
            // All required must be done
            for (QuestNode p : required) {
                if (data.getQuestState(p.getId(), QuestState.LOCKED) != QuestState.COMPLETED) return false;
            }
            // Optional pool: check minCount
            if (!optional.isEmpty()) {
                int minCount = node.getEffectiveOptionalPrereqMinCount();
                if (minCount < 0) return true; // -1 = none needed
                int doneOptional = 0;
                for (QuestNode p : optional) {
                    if (data.getQuestState(p.getId(), QuestState.LOCKED) == QuestState.COMPLETED) doneOptional++;
                }
                int need = (minCount == 0) ? optional.size() : minCount;
                return doneOptional >= need;
            }
            return true;
        }

        // Legacy AND/OR gate
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

    // ── Repeat helpers ────────────────────────────────────────────────────────

    public static boolean canRepeatNow(QuestNode node, PlayerQuestData data) {
        long last = data.getLastCompletedTime(node.getId());
        if (last == 0) return true; // never completed — always ok

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
        // Wipe all task progress so accumulator tasks (kill, craft, stat) start fresh
        // — clears both per-player and (if pooled) team-shared progress for each task.
        for (QuestTask task : node.getEffectiveTasks(player.getServer())) {
            net.phoenixvine.chronicles.capability.TaskProgressAccess.clear(player, task.getTaskId());
        }
        // Allow claiming rewards again
        data.clearClaimedRewards(node.getId());
        data.clearChosenRewardIndex(node.getId());
        // Transition to UNLOCKED via the normal path (fires events + sends sync)
        changeQuestState(player, node, QuestState.UNLOCKED);
    }

    // ── Reward granting ───────────────────────────────────────────────────────

    /**
     * Grants all rewards for a completed quest. Safe to call from the claim button (client
     * should send a packet that triggers this server-side). Marks the rewards as claimed so
     * repeated calls are no-ops.
     */
    public static void grantRewards(ServerPlayer player, QuestNode node) {
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            if (data.hasClaimedRewards(node.getId())) return;
            if (MinecraftForge.EVENT_BUS.post(
                    new QuestEvent.RewardClaimed(player, node)))
                return; // cancelled — mod vetoed the reward grant
            for (QuestReward reward : node.getEffectiveRewards(player.getServer())) {
                reward.grant(player);
            }
            consumeTaskProgress(player, node);
            data.markRewardsClaimed(node.getId());
            sendProgressSync(player);
        });
    }

    /**
     * Runs every task's own tryConsume(Player) - the hook each "consume"-flagged task type (item/
     * fluid withdrawal, AE2 network extraction, kill-count/stat-baseline resets for repeatable
     * quests) documents as "call this when claiming rewards", but which nothing actually called
     * until now: every task with consume behavior just silently never fired it, so items/fluids
     * were never withdrawn and repeatable-tracking resets never happened, regardless of the
     * consume flag pack devs had set on those tasks.
     */
    private static void consumeTaskProgress(ServerPlayer player, QuestNode node) {
        for (QuestTask task : node.getEffectiveTasks(player.getServer())) {
            task.tryConsume(player);
        }
    }

    public static QuestState getQuestState(Player player, QuestNode node) {
        return player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .map(data -> data.getQuestState(node.getId(), QuestState.LOCKED))
                .orElse(QuestState.LOCKED);
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    public static void sendProgressSync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                    .ifPresent(data -> ChronicleNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> serverPlayer),
                            new S2CSyncPlayerProgressPacket(data)));
        }
    }

    /**
     * Grants a single chosen reward (for choice-group quests).
     */
    public static void grantChosenReward(ServerPlayer player, QuestNode node, int choiceIndex) {
        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(data -> {
            if (data.hasClaimedRewards(node.getId())) return;
            List<QuestReward> effectiveRewards = node.getEffectiveRewards(player.getServer());
            if (choiceIndex < 0 || choiceIndex >= effectiveRewards.size()) return;
            effectiveRewards.get(choiceIndex).grant(player);
            consumeTaskProgress(player, node);
            data.setChosenRewardIndex(node.getId(), choiceIndex);
            data.markRewardsClaimed(node.getId());
            sendProgressSync(player);
        });
    }
}
