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

public final class QuestAPI {

    private QuestAPI() {}

    private static final Set<String> warnedOnce = ConcurrentHashMap.newKeySet();

    private static void warnOnce(String key, String message, Object... args) {
        if (warnedOnce.add(key)) {
            PhoenixChronicles.LOGGER.warn("[QuestAPI] " + message, args);
        }
    }

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

                    if (MinecraftForge.EVENT_BUS.post(new QuestEvent.ExternalEvent(player, node, triggerId, data)))
                        continue;

                    ext.onExternalEvent(player, data);
                }

                QuestProgressTracker.checkAndTryComplete(player, node);
            }
        });
    }

    public static boolean forceComplete(Player player, String questId) {
        return setState(player, questId, QuestState.COMPLETED);
    }

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

    public static QuestState getState(Player player, String questId) {
        if (player == null) {
            warnOnce("getState:null-player", "getState() called with a null player - returning LOCKED.");
            return QuestState.LOCKED;
        }
        QuestNode node = resolveQuest("getState", questId);
        if (node == null) return QuestState.LOCKED;
        return QuestProgressTracker.getQuestState(player, node);
    }

    public static Map<ResourceLocation, QuestState> getAllStates(Player player) {
        if (player == null) {
            warnOnce("getAllStates:null-player", "getAllStates() called with a null player - returning an empty map.");
            return Collections.emptyMap();
        }
        return player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS)
                .map(data -> Map.copyOf(data.getAllStates()))
                .orElse(Collections.emptyMap());
    }

    public static boolean isCompleted(Player player, String questId) {
        return getState(player, questId) == QuestState.COMPLETED;
    }

    public static boolean isUnlocked(Player player, String questId) {
        QuestState state = getState(player, questId);
        return state == QuestState.UNLOCKED || state == QuestState.ACTIVE;
    }

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

