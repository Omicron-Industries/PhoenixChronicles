package net.phoenixvine.chronicles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.event.QuestEvent;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.ExternalTriggerTask;
import net.phoenixvine.chronicles.tracker.QuestProgressTracker;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public inbound API for the Phoenix Chronicles quest system.
 *
 * This is the single entry point for external code (other mods, KubeJS scripts)
 * to push information into the quest system. Call these methods server-side.
 *
 * ── KubeJS server_scripts usage ───────────────────────────────────────────────
 * 
 * <pre>
 * const QuestAPI = Java.loadClass('net.phoenixvine.chronicles.QuestAPI')
 *
 * ForgeEvents.onEvent('net.minecraftforge.event.entity.living.LivingDeathEvent', event => {
 *   const killer = event.source.entity
 *   if (killer && event.entity.type.registryName.equals('minecraft:ender_dragon')) {
 *     QuestAPI.fireExternalEvent(killer, 'mypack:killed_dragon', null)
 *   }
 * })
 * </pre>
 *
 * ── Java mod usage ────────────────────────────────────────────────────────────
 * 
 * <pre>
 * // In your Forge event handler:
 * QuestAPI.fireExternalEvent(serverPlayer, "mymod:sun_eaten", null);
 *
 * // With data:
 * CompoundTag data = new CompoundTag();
 * data.putString("dimension", player.level().dimension().location().toString());
 * QuestAPI.fireExternalEvent(serverPlayer, "mymod:dimension_visit", data);
 * </pre>
 *
 * @see ExternalTriggerTask
 * @see QuestEvent.ExternalEvent
 */
public final class QuestAPI {

    private QuestAPI() {}

    /**
     * Every "caller made a mistake" warning below is logged through here instead of a plain
     * LOGGER.warn - a broken integration calling an API method every tick (e.g. a typo'd quest
     * ID checked in a HUD render loop) would otherwise flood the log 20x/sec forever. Each
     * distinct mistake (keyed by method + the bad input) is logged once per server session, not
     * silently swallowed and not spammed - the whole point is that a mistake gets NOTICED, which
     * a wall of repeated identical lines defeats just as much as no message at all.
     */
    private static final Set<String> warnedOnce = ConcurrentHashMap.newKeySet();

    private static void warnOnce(String key, String message, Object... args) {
        if (warnedOnce.add(key)) {
            PhoenixChronicles.LOGGER.warn("[QuestAPI] " + message, args);
        }
    }

    /**
     * Shared quest-ID resolution for setState/getState/getProgress - logs the SPECIFIC reason a
     * lookup failed (malformed ID vs. not registered) rather than letting every caller silently
     * fall back to a default (LOCKED / 0f / false) with no way to tell "this quest genuinely
     * isn't done yet" apart from "you typo'd the ID and this has never once looked at a real
     * quest." Returns null on any failure; caller decides the appropriate fallback return value.
     */
    @Nullable
    private static QuestNode resolveQuest(String methodName, @Nullable String questId) {
        if (questId == null || questId.isBlank()) {
            warnOnce(methodName + ":blank-id", "{}() called with a null/blank questId - ignored.", methodName);
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(questId);
        if (id == null) {
            warnOnce(methodName + ":bad-id:" + questId,
                    "{}(\"{}\") - not a valid quest ID (expected " + "\"namespace:path\") - ignored.", methodName,
                    questId);
            return null;
        }
        QuestNode node = QuestTreeRegistry.getQuest(id);
        if (node == null) {
            warnOnce(methodName + ":not-found:" + questId,
                    "{}(\"{}\") - no quest with that ID is registered " +
                            "(check for a typo, or that the quest pack has finished loading before this is called).",
                    methodName, questId);
            return null;
        }
        return node;
    }

    /**
     * Signals that a custom external event occurred for a player.
     *
     * <p>
     * The quest system will check all active quests the player has for any
     * {@link ExternalTriggerTask} with a matching {@code trigger_id} and advance
     * their progress. A {@link QuestEvent.ExternalEvent} is also fired on the
     * Forge event bus so other mods can observe or cancel the signal.
     *
     * @param player    The player the event applies to. Must be server-side.
     * @param triggerId The event identifier — matches the {@code trigger_id} field
     *                  in {@code ExternalTriggerTask} SNBT. Use namespaced IDs
     *                  to avoid conflicts (e.g. {@code "mymod:sun_eaten"}).
     * @param data      Optional extra data. Passed to {@link QuestEvent.ExternalEvent}
     *                  and available to Forge event subscribers. May be {@code null}.
     */
    public static void fireExternalEvent(Player player, String triggerId, @Nullable CompoundTag data) {
        if (player == null) {
            warnOnce("fireExternalEvent:null-player", "fireExternalEvent() called with a null player - ignored.");
            return;
        }
        if (triggerId == null || triggerId.isBlank()) {
            warnOnce("fireExternalEvent:blank-trigger", "fireExternalEvent() called with a null/blank triggerId for " +
                    "player {} - ignored. Pass the same namespaced trigger_id your ExternalTriggerTask quests use.",
                    player.getGameProfile().getName());
            return;
        }
        if (player.level().isClientSide()) {
            warnOnce("fireExternalEvent:client:" + triggerId,
                    "fireExternalEvent(\"{}\") called on the CLIENT - " +
                            "ignored. This must be called server-side (e.g. from a server_scripts event, not " +
                            "client_scripts).",
                    triggerId);
            return;
        }
        if (!player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).isPresent()) {
            warnOnce("fireExternalEvent:no-cap:" + player.getGameProfile().getName(), "fireExternalEvent(\"{}\") " +
                    "called for player {}, who has no quest-progress capability attached - ignored. This usually " +
                    "means it was called on something other than a real player entity.",
                    triggerId, player.getGameProfile().getName());
        }

        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(questData -> {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                if (node.isFlagDisabled()) continue;
                if (node.getEffectiveVisibility(player.getServer()) == QuestNode.Visibility.DISABLED) continue;

                QuestState state = questData.getQuestState(node.getId(), QuestState.LOCKED);
                if (state != QuestState.ACTIVE && state != QuestState.UNLOCKED) continue;

                for (Object task : node.getEffectiveTasks(player.getServer())) {
                    if (!(task instanceof ExternalTriggerTask ext)) continue;
                    if (!triggerId.equals(ext.getTriggerId())) continue;
                    if (ext.isCompletedFor(player)) continue;

                    // Fire the per-node ExternalEvent — cancellable so subscribers can veto per-quest
                    if (MinecraftForge.EVENT_BUS.post(new QuestEvent.ExternalEvent(player, node, triggerId, data)))
                        continue;

                    ext.onExternalEvent(player, data);
                }

                // Recheck completion after potentially updating task progress
                QuestProgressTracker.checkAndTryComplete(player, node);
            }
        });
    }

    /**
     * Manually set a quest to COMPLETED for a player, bypassing task requirements.
     * Useful for admin commands or integration tests. Convenience wrapper around
     * {@link #setState}.
     *
     * @param player  The player.
     * @param questId The quest ID (e.g. {@code "phoenixcore:intro_quest"}).
     * @return {@code true} if the quest was found and transitioned to COMPLETED.
     */
    public static boolean forceComplete(Player player, String questId) {
        return setState(player, questId, QuestState.COMPLETED);
    }

    /**
     * Forces a quest directly to the given state for a player, bypassing task requirements
     * and prerequisite gating. Useful for admin commands, debug tools, or resetting a quest
     * back to {@link QuestState#LOCKED} to let repeatable content run again.
     *
     * @param player   The player.
     * @param questId  The quest ID.
     * @param newState The state to force the quest into.
     * @return {@code true} if the quest was found and transitioned.
     */
    public static boolean setState(Player player, String questId, QuestState newState) {
        if (player == null) {
            warnOnce("setState:null-player", "setState() called with a null player - ignored.");
            return false;
        }
        if (newState == null) {
            warnOnce("setState:null-state:" + questId,
                    "setState(\"{}\", null) called - a QuestState is required - ignored.", questId);
            return false;
        }
        if (player.level().isClientSide()) {
            warnOnce("setState:client:" + questId, "setState(\"{}\") called on the CLIENT - ignored. This " +
                    "mutates authoritative quest state and must be called server-side.", questId);
            return false;
        }
        QuestNode node = resolveQuest("setState", questId);
        if (node == null) return false;
        QuestProgressTracker.changeQuestState(player, node, newState);
        return true;
    }

    /**
     * Returns the current state of a quest for a player.
     *
     * @param player  The player.
     * @param questId The quest ID.
     * @return The {@link QuestState}, or {@link QuestState#LOCKED} if not found.
     */
    public static QuestState getState(Player player, String questId) {
        if (player == null) {
            warnOnce("getState:null-player", "getState() called with a null player - returning LOCKED.");
            return QuestState.LOCKED;
        }
        QuestNode node = resolveQuest("getState", questId);
        if (node == null) return QuestState.LOCKED;
        return QuestProgressTracker.getQuestState(player, node);
    }

    /**
     * Returns every quest state currently recorded for this player, keyed by quest ID.
     * Handy for mods building their own quest UI/HUD without reaching into the capability
     * directly. The returned map is a snapshot — mutating it has no effect on the player.
     */
    public static Map<ResourceLocation, QuestState> getAllStates(Player player) {
        if (player == null) {
            warnOnce("getAllStates:null-player", "getAllStates() called with a null player - returning an empty map.");
            return Collections.emptyMap();
        }
        return player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .map(data -> Map.copyOf(data.getAllStates()))
                .orElse(Collections.emptyMap());
    }

    /**
     * Returns {@code true} if the player has completed the given quest.
     * Convenience wrapper around {@link #getState}.
     */
    public static boolean isCompleted(Player player, String questId) {
        return getState(player, questId) == QuestState.COMPLETED;
    }

    /**
     * Returns {@code true} if the quest is unlocked or active (i.e. visible and workable,
     * whether or not the player has started it yet).
     */
    public static boolean isUnlocked(Player player, String questId) {
        QuestState state = getState(player, questId);
        return state == QuestState.UNLOCKED || state == QuestState.ACTIVE;
    }

    /**
     * Returns the fraction (0.0-1.0) of a quest's non-optional tasks the player has completed.
     * Returns {@code 1.0} for a COMPLETED quest and {@code 0.0} if the quest has no non-optional
     * tasks or doesn't exist — useful for progress bars in other mods' UIs.
     *
     * @param player  The player.
     * @param questId The quest ID.
     */
    public static float getProgress(Player player, String questId) {
        if (player == null) {
            warnOnce("getProgress:null-player", "getProgress() called with a null player - returning 0.");
            return 0f;
        }
        QuestNode node = resolveQuest("getProgress", questId);
        if (node == null) return 0f;
        if (QuestProgressTracker.getQuestState(player, node) == QuestState.COMPLETED) return 1f;

        int total = 0, done = 0;
        for (QuestTask task : node.getEffectiveTasks(player.getServer())) {
            if (task.isOptional()) continue;
            total++;
            if (task.isCompletedFor(player)) done++;
        }
        return total == 0 ? 0f : (float) done / total;
    }
}
