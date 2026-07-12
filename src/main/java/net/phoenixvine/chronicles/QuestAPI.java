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
        if (player == null || triggerId == null || triggerId.isBlank()) return;
        if (player.level().isClientSide()) return;

        player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).ifPresent(questData -> {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                if (node.isFlagDisabled()) continue;
                if (node.getVisibility() == QuestNode.Visibility.DISABLED) continue;

                QuestState state = questData.getQuestState(node.getId(), QuestState.LOCKED);
                if (state != QuestState.ACTIVE && state != QuestState.UNLOCKED) continue;

                for (Object task : node.getTasks()) {
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
        ResourceLocation id = ResourceLocation.tryParse(questId);
        if (id == null || newState == null) return false;
        QuestNode node = QuestTreeRegistry.getQuest(id);
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
        ResourceLocation id = ResourceLocation.tryParse(questId);
        if (id == null) return QuestState.LOCKED;
        QuestNode node = QuestTreeRegistry.getQuest(id);
        if (node == null) return QuestState.LOCKED;
        return QuestProgressTracker.getQuestState(player, node);
    }

    /**
     * Returns every quest state currently recorded for this player, keyed by quest ID.
     * Handy for mods building their own quest UI/HUD without reaching into the capability
     * directly. The returned map is a snapshot — mutating it has no effect on the player.
     */
    public static Map<ResourceLocation, QuestState> getAllStates(Player player) {
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
        ResourceLocation id = ResourceLocation.tryParse(questId);
        if (id == null) return 0f;
        QuestNode node = QuestTreeRegistry.getQuest(id);
        if (node == null) return 0f;
        if (getState(player, questId) == QuestState.COMPLETED) return 1f;

        int total = 0, done = 0;
        for (QuestTask task : node.getTasks()) {
            if (task.isOptional()) continue;
            total++;
            if (task.isCompletedFor(player)) done++;
        }
        return total == 0 ? 0f : (float) done / total;
    }
}
