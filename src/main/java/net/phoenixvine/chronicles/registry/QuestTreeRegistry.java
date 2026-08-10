package net.phoenixvine.chronicles.registry;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QuestTreeRegistry {

    private static final Map<ResourceLocation, QuestNode> ALL_QUESTS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, QuestNode> ROOT_NODES = new ConcurrentHashMap<>();

    private static final java.util.Set<ResourceLocation> CONFIG_QUEST_IDS = ConcurrentHashMap.newKeySet();

    private static final Map<ResourceLocation, QuestNode> TASK_OWNER = new ConcurrentHashMap<>();

    public static void registerRootChapter(QuestNode root) {
        if (root == null) return;
        ROOT_NODES.put(root.getId(), root);
        registerRecursively(root);
    }

    private static void registerRecursively(QuestNode node) {
        if (node == null) return;
        ALL_QUESTS.put(node.getId(), node);
        indexTasks(node);
        for (QuestNode child : node.getChildren()) {
            registerRecursively(child);
        }
    }

    private static void indexTasks(QuestNode node) {
        for (var task : node.getTasks()) {
            if (task.getTaskId() != null) {
                TASK_OWNER.put(task.getTaskId(), node);
            }
        }
    }

    public static void registerBareQuestNode(QuestNode node) {
        if (node != null && node.getId() != null) {
            ALL_QUESTS.put(node.getId(), node);
            indexTasks(node);
        }
    }

    public static void injectDynamicQuestNode(QuestNode node, @Nullable ResourceLocation parentId) {
        if (node == null || node.getId() == null) return;
        ALL_QUESTS.put(node.getId(), node);
        indexTasks(node);

        if (parentId != null) {
            QuestNode parent = ALL_QUESTS.get(parentId);
            if (parent != null) {
                parent.addChild(node);
                ROOT_NODES.remove(node.getId());
            }
        } else {
            ROOT_NODES.put(node.getId(), node);
        }

        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                net.phoenixvine.chronicles.tracker.QuestProgressTracker.autoUnlockSatisfiedQuests(player);
            }
        }
    }

    @Nullable
    public static QuestNode getQuest(ResourceLocation id) {
        if (id == null) return null;
        return ALL_QUESTS.get(id);
    }

    public static Map<ResourceLocation, QuestNode> getRootChapters() {
        return Map.copyOf(ROOT_NODES);
    }

    public static Map<ResourceLocation, QuestNode> getAllQuests() {
        return ALL_QUESTS;
    }

    @Nullable
    public static QuestNode getTaskOwner(ResourceLocation taskId) {
        if (taskId == null) return null;
        return TASK_OWNER.get(taskId);
    }

    public static boolean isChapterGatedHidden(String chapter,
                                               java.util.function.Function<QuestNode, net.phoenixvine.chronicles.model.QuestState> stateLookup) {
        if (chapter == null) return false;
        boolean any = false;
        for (QuestNode n : ALL_QUESTS.values()) {
            if (!chapter.equalsIgnoreCase(n.getChapter())) continue;
            any = true;
            if (!n.isGatedHidden(stateLookup)) return false;
        }
        return any;
    }

    public static void reindexTask(ResourceLocation taskId, QuestNode owner) {
        if (taskId != null && owner != null) {
            TASK_OWNER.put(taskId, owner);
        }
    }

    public static void removeQuest(ResourceLocation id) {
        if (id == null) return;
        QuestNode removed = ALL_QUESTS.remove(id);
        ROOT_NODES.remove(id);
        if (removed == null) return;
        for (var task : removed.getTasks()) {
            if (task.getTaskId() != null) TASK_OWNER.remove(task.getTaskId());
        }

        for (QuestNode n : ALL_QUESTS.values()) {
            n.removeChild(removed);
        }
    }

    public static void markAsConfigQuest(ResourceLocation id) {
        if (id != null) {
            CONFIG_QUEST_IDS.add(id);
        }
    }

    public static void clearConfigQuests() {
        for (ResourceLocation id : new java.util.ArrayList<>(CONFIG_QUEST_IDS)) {
            removeQuest(id);
        }
        CONFIG_QUEST_IDS.clear();
    }

    public static void clear() {
        ALL_QUESTS.clear();
        ROOT_NODES.clear();
        CONFIG_QUEST_IDS.clear();
        TASK_OWNER.clear();

        net.phoenixvine.chronicles.tracker.QuestProgressTracker.invalidateAllActiveTracking();
    }
}
