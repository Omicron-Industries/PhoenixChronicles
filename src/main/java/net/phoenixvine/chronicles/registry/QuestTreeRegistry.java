package net.phoenixvine.chronicles.registry;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.codec.ChronicleDataLoader;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QuestTreeRegistry {

    // These are genuinely shared mutable state between the integrated server thread (tick loop,
    // reload/import logic) and the client render thread (the quest book screen reads
    // getAllQuests() every frame it's open) - a plain HashMap under that access pattern throws
    // ConcurrentModificationException or, worse, corrupts mid-iteration (ArrayIndexOutOfBounds
    // inside HashMap's own internals) the moment a reload happens while the screen is open.
    // ConcurrentHashMap's iterators/toArray are safe under concurrent mutation with no external
    // locking needed, which is what these access patterns actually require.
    private static final Map<ResourceLocation, QuestNode> ALL_QUESTS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, QuestNode> ROOT_NODES = new ConcurrentHashMap<>();
    /** IDs of quests loaded from the config dir (as opposed to datapacks). Used by /chronicles reload. */
    private static final java.util.Set<ResourceLocation> CONFIG_QUEST_IDS = ConcurrentHashMap.newKeySet();
    /** task id → owning quest node, kept in sync with ALL_QUESTS for pooled-progress lookups. */
    private static final Map<ResourceLocation, QuestNode> TASK_OWNER = new ConcurrentHashMap<>();

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers a root node and recursively registers all of its descendants.
     * Called by {@link ChronicleDataLoader} after assembling the full tree.
     */
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
            TASK_OWNER.put(task.getTaskId(), node);
        }
    }

    /**
     * Registers a single node without touching the root map.
     * Used by {@link QuestFileLoader} / content loaders for bare nodes
     * whose tree position is not yet known.
     */
    public static void registerBareQuestNode(QuestNode node) {
        if (node != null) {
            ALL_QUESTS.put(node.getId(), node);
            indexTasks(node);
        }
    }

    /**
     * Live-injects a dynamically created node (e.g. from the creator screen)
     * into both maps and wires up the parent link if one is provided.
     */
    public static void injectDynamicQuestNode(QuestNode node, @Nullable ResourceLocation parentId) {
        if (node == null) return;
        ALL_QUESTS.put(node.getId(), node);
        indexTasks(node);

        if (parentId != null) {
            QuestNode parent = ALL_QUESTS.get(parentId);
            if (parent != null) {
                // addChild guards against duplicates internally
                parent.addChild(node);
                ROOT_NODES.remove(node.getId());
            }
        } else {
            ROOT_NODES.put(node.getId(), node);
        }
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    @Nullable
    public static QuestNode getQuest(ResourceLocation id) {
        return ALL_QUESTS.get(id);
    }

    /** All root nodes (nodes with no parent), in insertion order. */
    public static Map<ResourceLocation, QuestNode> getRootChapters() {
        return Map.copyOf(ROOT_NODES);
    }

    public static Map<ResourceLocation, QuestNode> getAllQuests() {
        return ALL_QUESTS;
    }

    /** The quest node that owns a given task id, or null if untracked (e.g. before load). */
    @Nullable
    public static QuestNode getTaskOwner(ResourceLocation taskId) {
        return TASK_OWNER.get(taskId);
    }

    /** Removes a quest from both maps and unlinks it from any parent's children list. */
    public static void removeQuest(ResourceLocation id) {
        QuestNode removed = ALL_QUESTS.remove(id);
        ROOT_NODES.remove(id);
        if (removed == null) return;
        for (var task : removed.getTasks()) TASK_OWNER.remove(task.getTaskId());
        // Unlink from any parent that references this node as a child
        for (QuestNode n : ALL_QUESTS.values()) {
            // QuestNode.getChildren() is unmodifiable; access via reflection-free helper
            n.removeChild(removed);
        }
    }

    // ── Config-quest tracking (for /chronicles reload) ────────────────────────

    /** Marks a quest ID as config-origin so {@link #clearConfigQuests()} can remove it later. */
    public static void markAsConfigQuest(ResourceLocation id) {
        CONFIG_QUEST_IDS.add(id);
    }

    /**
     * Removes all config-origin quests from the registry and unlinks them from any
     * remaining (datapack-origin) parents. Safe to call before re-running
     * {@link QuestFileLoader#loadAdditiveFromDisk}.
     */
    public static void clearConfigQuests() {
        for (ResourceLocation id : new java.util.ArrayList<>(CONFIG_QUEST_IDS)) {
            removeQuest(id);
        }
        CONFIG_QUEST_IDS.clear();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void clear() {
        ALL_QUESTS.clear();
        ROOT_NODES.clear();
        CONFIG_QUEST_IDS.clear();
        TASK_OWNER.clear();
    }
}
