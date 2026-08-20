package net.phoenixvine.chronicles.codec;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tracker.TutorialStep;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class QuestFileLoader {

    public static final List<String> LOAD_ERRORS = java.util.Collections.synchronizedList(new ArrayList<>());

    private record QuestRecord(
                               ResourceLocation id,
                               String title,
                               String description,
                               String subtitle,
                               String chapter,
                               String shape,
                               String iconItemId,
                               int posX,
                               int posY,
                               QuestNode.Visibility visibility,
                               int taskMinCount,
                               ResourceLocation parentId,
                               QuestNode.RepeatMode repeatMode,
                               int repeatCooldownHours,
                               Boolean requireAllPrereqs,
                               List<QuestReward> rewards,
                               List<QuestTask> tasks,
                               net.minecraft.nbt.ListTag emergencyItems,
                               Map<String, Boolean> prereqRequired,
                               Integer optionalPrereqMinCount,
                               String enableIf,
                               Set<String> prereqForbidden,
                               Set<String> prereqLink,
                               Set<String> prereqCosmetic,
                               Map<String, String> prereqLineShape,
                               Map<String, String> prereqLineVisual,
                               Map<String, String> prereqLineSpeed,
                               Map<String, Boolean> prereqLineArrow,
                               Map<String, String> prereqLineStyleId,
                               boolean hideDepLine,
                               boolean disabledBlocksChildren,
                               boolean shared,
                               boolean pooledProgress,
                               List<TutorialStep> tutorialSteps,
                               boolean autoClaimRewards,
                               boolean rewardChoice,
                               int rewardChoiceCount,
                               String devNotes,
                               QuestNode.NodeSize nodeSize,
                               int sizeOverridePx,
                               ResourceLocation linkTarget,
                               String iconTexture,
                               String shapeTexture,
                               List<QuestNode.QuestVariant> variants,
                               String previewMachineId,
                               String iconFluid,
                               String backgroundType,
                               String externalScreenId,
                               boolean optional,
                               Set<String> tags) {}

    private static void applyLineOverrides(QuestNode node, QuestNode prereq, QuestRecord rec, String pid) {
        String shape = rec.prereqLineShape().get(pid);
        if (shape != null) {
            try {
                node.setPrereqLineShape(prereq.getId(), QuestChroniclesSettings.LineStyle.valueOf(shape));
            } catch (IllegalArgumentException ignored) {}
        }
        String visual = rec.prereqLineVisual().get(pid);
        if (visual != null) {
            try {
                node.setPrereqLineVisual(prereq.getId(), QuestChroniclesSettings.LineVisualStyle.valueOf(visual));
            } catch (IllegalArgumentException ignored) {}
        }
        String speed = rec.prereqLineSpeed().get(pid);
        if (speed != null) {
            try {
                node.setPrereqLineSpeed(prereq.getId(), QuestChroniclesSettings.LineAnimSpeed.valueOf(speed));
            } catch (IllegalArgumentException ignored) {}
        }
        Boolean arrow = rec.prereqLineArrow().get(pid);
        if (arrow != null) node.setPrereqLineArrow(prereq.getId(), arrow);
        String styleId = rec.prereqLineStyleId().get(pid);
        if (styleId != null) node.setPrereqLineStyleId(prereq.getId(), styleId);
    }

    public static void loadOneFromDisk(Path snbtFile) {
        if (!Files.exists(snbtFile)) return;
        QuestRecord rec = parseFile(snbtFile);
        if (rec == null || QuestTreeRegistry.getQuest(rec.id()) != null) return;
        wireAndRegister(List.of(rec));
        QuestTreeRegistry.markAsConfigQuest(rec.id());
    }

    public static void loadAdditiveFromDisk(Path configDir) {
        if (!Files.exists(configDir)) return;
        LOAD_ERRORS.clear();

        Path questsDir = configDir.resolve("quests");
        if (!Files.exists(questsDir)) return;

        List<QuestRecord> records;
        try (Stream<Path> walk = Files.walk(questsDir)) {

            records = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".snbt"))
                    .filter(p -> !isFtbImportStagingFile(p))
                    .map(QuestFileLoader::parseFile)
                    .filter(rec -> rec != null && QuestTreeRegistry.getQuest(rec.id()) == null)
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to walk config folder: " + e.getMessage());
            return;
        }

        if (records.isEmpty()) return;
        wireAndRegister(records);

        for (QuestRecord rec : records) QuestTreeRegistry.markAsConfigQuest(rec.id());
        System.out.println("[Phoenix Chronicles] Loaded " + records.size() +
                " editor quest(s) from config dir.");
    }

    public static void reloadAllQuestsFromDisk() {
        LOAD_ERRORS.clear();
        QuestTreeRegistry.clear();

        Path configFolder = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
        Path questsFolder = configFolder.resolve("quests");
        if (!Files.exists(questsFolder)) return;

        List<QuestRecord> records;
        try (Stream<Path> walk = Files.walk(questsFolder)) {
            records = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".snbt"))
                    .filter(p -> !isFtbImportStagingFile(p))
                    .map(QuestFileLoader::parseFile)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to walk config folder: " + e.getMessage());
            return;
        }

        wireAndRegister(records);
        System.out.println("[Phoenix Chronicles] Loaded " + records.size() + " quest(s) from disk (" +
                QuestTreeRegistry.getRootChapters().size() + " root(s)).");
    }

    private static boolean isFtbImportStagingFile(Path p) {
        for (Path segment : p) {
            if (segment.toString().equalsIgnoreCase("ftb_import")) return true;
        }
        return false;
    }

    private static void wireAndRegister(List<QuestRecord> records) {
        for (QuestRecord rec : records) {
            QuestNode node = new QuestNode(rec.id(),
                    Component.literal(rec.title()), Component.literal(rec.description()));
            node.setChapter(rec.chapter());
            node.setShapeType(rec.shape());
            node.setCustomX(rec.posX());
            node.setCustomY(rec.posY());
            node.setSubtitle(rec.subtitle());
            node.setVisibility(rec.visibility());
            node.setEnableIf(rec.enableIf());
            node.setTaskMinCount(rec.taskMinCount());
            node.setHideDepLine(rec.hideDepLine());
            node.setOptional(rec.optional());
            node.setTags(rec.tags());
            node.setDisabledBlocksChildren(rec.disabledBlocksChildren());
            node.setShared(rec.shared());
            node.setPooledProgress(rec.pooledProgress());
            node.setAutoClaimRewards(rec.autoClaimRewards());
            node.setRewardChoice(rec.rewardChoice());
            node.setRewardChoiceCount(rec.rewardChoiceCount());
            node.setDevNotes(rec.devNotes());
            node.setPreviewMachineId(rec.previewMachineId());
            node.setNodeSize(rec.nodeSize());
            if (rec.sizeOverridePx() > 0) node.setSizeOverridePx(rec.sizeOverridePx());
            node.setLinkTarget(rec.linkTarget());
            node.setIconTexture(rec.iconTexture());
            node.setIconFluid(rec.iconFluid());
            node.setShapeTexture(rec.shapeTexture());
            node.setBackgroundType(rec.backgroundType());
            node.setExternalScreenId(rec.externalScreenId());
            if (!rec.iconItemId().isEmpty()) node.setIconItemById(rec.iconItemId());
            node.setRepeatMode(rec.repeatMode());
            node.setRepeatCooldownHours(rec.repeatCooldownHours());
            node.setRequireAllPrerequisites(rec.requireAllPrereqs());
            node.setOptionalPrereqMinCount(rec.optionalPrereqMinCount());
            for (QuestReward r : rec.rewards()) node.addReward(r);
            for (QuestTask t : rec.tasks()) node.addTask(t);
            for (QuestNode.QuestVariant v : rec.variants()) node.addVariant(v);
            if (rec.emergencyItems() != null) node.deserializeEmergencyItems(rec.emergencyItems());
            for (TutorialStep step : rec.tutorialSteps()) node.addTutorialStep(step);
            QuestTreeRegistry.registerBareQuestNode(node);
        }

        for (QuestRecord rec : records) {
            QuestNode node = QuestTreeRegistry.getQuest(rec.id());
            if (node == null) continue;
            if (rec.parentId() != null) {
                QuestNode parent = QuestTreeRegistry.getQuest(rec.parentId());
                if (parent != null) {
                    parent.addChild(node);
                } else {
                    System.err.println("[Phoenix Chronicles] Parent '" + rec.parentId() + "' not found for '" +
                            rec.id() + "' : treating as root.");
                    QuestTreeRegistry.registerRootChapter(node);
                }
            } else {
                QuestTreeRegistry.registerRootChapter(node);
            }

            for (String pid : rec.prereqRequired().keySet()) {
                if (QuestTreeRegistry.getQuest(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", pid)) ==
                        null)
                    LOAD_ERRORS.add("Quest '" + rec.id().getPath() + "': prereq '" + pid + "' not found.");
            }
            for (String pid : rec.prereqForbidden()) {
                if (QuestTreeRegistry.getQuest(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", pid)) ==
                        null)
                    LOAD_ERRORS.add("Quest '" + rec.id().getPath() + "': forbidden prereq '" + pid + "' not found.");
            }

            for (Map.Entry<String, Boolean> e : rec.prereqRequired().entrySet()) {
                QuestNode prereq = QuestTreeRegistry
                        .getQuest(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", e.getKey()));
                if (prereq != null) {
                    node.addPrerequisite(prereq);
                    node.setPrereqRequired(prereq.getId(), e.getValue());
                    if (rec.prereqLink().contains(e.getKey())) node.setPrereqLink(prereq.getId(), true);
                    if (rec.prereqCosmetic().contains(e.getKey())) node.setPrereqCosmetic(prereq.getId(), true);
                    applyLineOverrides(node, prereq, rec, e.getKey());
                }
            }
            for (String pid : rec.prereqForbidden()) {
                QuestNode prereq = QuestTreeRegistry
                        .getQuest(ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", pid));
                if (prereq != null) {
                    node.addPrerequisite(prereq);
                    node.setPrereqForbidden(prereq.getId(), true);
                    if (rec.prereqLink().contains(pid)) node.setPrereqLink(prereq.getId(), true);
                    if (rec.prereqCosmetic().contains(pid)) node.setPrereqCosmetic(prereq.getId(), true);
                    applyLineOverrides(node, prereq, rec, pid);
                }
            }

            if (rec.linkTarget() != null && QuestTreeRegistry.getQuest(rec.linkTarget()) == null) {
                LOAD_ERRORS.add("Quest link '" + rec.id().getPath() + "': target '" + rec.linkTarget() +
                        "' was not found in the registry - it may have failed to load.");
            }
        }

        Map<ResourceLocation, String> taskIdToQuest = new LinkedHashMap<>();
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            for (QuestTask task : node.getTasks()) {
                ResourceLocation tid = task.getTaskId();
                if (taskIdToQuest.containsKey(tid)) {

                    ResourceLocation newId = regenerateCollidedTaskId(tid);
                    LOAD_ERRORS.add("Duplicate task_id '" + tid + "' in quest '" + node.getId().getPath() +
                            "' (also in '" + taskIdToQuest.get(tid) +
                            "') - auto-regenerated to '" + newId + "' and saved.");
                    forceTaskId(task, newId);
                    QuestTreeRegistry.reindexTask(newId, node);
                    try {
                        QuestFileSaver.saveOneQuestToDisk(node);
                    } catch (Exception e) {
                        LOAD_ERRORS.add("Failed to persist regenerated task_id for quest '" +
                                node.getId().getPath() + "': " + e.getMessage());
                    }
                    taskIdToQuest.put(newId, node.getId().getPath());
                } else {
                    taskIdToQuest.put(tid, node.getId().getPath());
                }
            }
        }
    }

    private static ResourceLocation regenerateCollidedTaskId(ResourceLocation original) {
        String newId = original.getPath() + "_fixed_" +
                java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return ResourceLocation.fromNamespaceAndPath(original.getNamespace(), newId);
    }

    private static void forceTaskId(QuestTask task, ResourceLocation newId) {
        try {
            java.lang.reflect.Field field = QuestTask.class.getDeclaredField("taskId");
            field.setAccessible(true);
            field.set(task, newId);
        } catch (Exception e) {
            LOAD_ERRORS.add("Failed to regenerate task_id on task instance: " + e.getMessage());
        }
    }

    private static QuestRecord parseFile(Path file) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            CompoundTag tag = TagParser.parseTag(raw);

            String fileName = file.getFileName().toString();
            String idStr = tag.contains("id") && !tag.getString("id").isEmpty() ? tag.getString("id") :
                    fileName.substring(0, fileName.lastIndexOf('.'));
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", idStr.toLowerCase());

            String title = tag.contains("title") ? tag.getString("title") : "Unnamed Quest";
            String desc = tag.contains("description") ? tag.getString("description") : "";

            String chapter = tag.contains("chapter") ? tag.getString("chapter") :
                    tag.contains("category") ? tag.getString("category") : "MAIN";
            String shape = tag.contains("shape") ? tag.getString("shape") : "SQUARE";
            String iconItem = tag.contains("icon_item") ? tag.getString("icon_item") : "";
            String iconTexture = tag.contains("icon_texture") ? tag.getString("icon_texture") : "";
            String iconFluid = tag.contains("icon_fluid") ? tag.getString("icon_fluid") : "";
            String shapeTexture = tag.contains("shape_texture") ? tag.getString("shape_texture") : "";
            String backgroundType = tag.contains("background") ? tag.getString("background") : "";
            String externalScreenId = tag.contains("external_screen") ? tag.getString("external_screen") : "";
            int posX = tag.contains("positionX") ? tag.getInt("positionX") : 40;
            int posY = tag.contains("positionY") ? tag.getInt("positionY") : 70;

            String parentStr = tag.contains("parent") ? tag.getString("parent") : "none";
            ResourceLocation parentId = (!parentStr.isEmpty() && !parentStr.equals("none")) ?
                    ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", parentStr.toLowerCase()) : null;

            QuestNode.RepeatMode repeatMode = QuestNode.RepeatMode.NONE;
            if (tag.contains("repeat_mode")) {
                try {
                    repeatMode = QuestNode.RepeatMode.valueOf(tag.getString("repeat_mode").toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
            int repeatCooldownHours = tag.contains("repeat_cooldown_hours") ? tag.getInt("repeat_cooldown_hours") : 24;

            Boolean requireAllPrereqs = tag.contains("require_all_prereqs") ? tag.getBoolean("require_all_prereqs") :
                    null;

            String subtitle = tag.contains("subtitle") ? tag.getString("subtitle") : "";
            QuestNode.Visibility visibility = QuestNode.Visibility.NORMAL;
            if (tag.contains("visibility")) {
                try {
                    visibility = QuestNode.Visibility.valueOf(tag.getString("visibility").toUpperCase());
                } catch (Exception ignored) {}
            }
            int taskMinCount = tag.contains("task_min_count") ? tag.getInt("task_min_count") : 0;

            List<QuestReward> rewards = parseRewards(tag);
            List<QuestTask> tasks = parseTasks(tag);

            List<QuestNode.QuestVariant> variants = new ArrayList<>();
            if (tag.contains("variants")) {
                ListTag variantList = tag.getList("variants", Tag.TAG_COMPOUND);
                for (int vi = 0; vi < variantList.size(); vi++) {
                    CompoundTag vTag = variantList.getCompound(vi);
                    String condition = vTag.contains("condition") ? vTag.getString("condition") : "";
                    if (condition.isBlank()) continue;
                    QuestNode.QuestVariant variant = new QuestNode.QuestVariant(condition);
                    if (vTag.contains("title")) variant.title = vTag.getString("title");
                    if (vTag.contains("subtitle")) variant.subtitle = vTag.getString("subtitle");
                    if (vTag.contains("description")) variant.description = vTag.getString("description");
                    if (vTag.contains("visibility")) {
                        try {
                            variant.visibility = QuestNode.Visibility
                                    .valueOf(vTag.getString("visibility").toUpperCase());
                        } catch (IllegalArgumentException ignored) {}
                    }
                    if (vTag.contains("tasks")) variant.tasks = parseTasks(vTag);
                    if (vTag.contains("rewards")) variant.rewards = parseRewards(vTag);
                    variants.add(variant);
                }
            }

            net.minecraft.nbt.ListTag emergencyTag = tag.contains("emergency_items") ?
                    tag.getList("emergency_items", Tag.TAG_COMPOUND) : null;

            Map<String, Boolean> prereqRequired = new LinkedHashMap<>();
            Set<String> prereqForbidden = new java.util.LinkedHashSet<>();
            Set<String> prereqLink = new java.util.LinkedHashSet<>();
            Set<String> prereqCosmetic = new java.util.LinkedHashSet<>();
            Map<String, String> prereqLineShape = new LinkedHashMap<>();
            Map<String, String> prereqLineVisual = new LinkedHashMap<>();
            Map<String, String> prereqLineSpeed = new LinkedHashMap<>();
            Map<String, Boolean> prereqLineArrow = new LinkedHashMap<>();
            Map<String, String> prereqLineStyleId = new LinkedHashMap<>();
            if (tag.contains("prerequisites")) {
                ListTag pList = tag.getList("prerequisites", Tag.TAG_COMPOUND);
                for (int pi = 0; pi < pList.size(); pi++) {
                    CompoundTag pTag = pList.getCompound(pi);
                    String pid = pTag.contains("id") ? pTag.getString("id") : "";
                    if (pid.isEmpty()) continue;
                    if (pTag.contains("forbidden") && pTag.getBoolean("forbidden")) {
                        prereqForbidden.add(pid);
                    } else {
                        boolean req = !pTag.contains("required") || pTag.getBoolean("required");
                        prereqRequired.put(pid, req);
                    }
                    if (pTag.contains("link") && pTag.getBoolean("link")) prereqLink.add(pid);
                    if (pTag.contains("cosmetic") && pTag.getBoolean("cosmetic")) prereqCosmetic.add(pid);
                    if (pTag.contains("line_shape")) prereqLineShape.put(pid, pTag.getString("line_shape"));
                    if (pTag.contains("line_style")) prereqLineVisual.put(pid, pTag.getString("line_style"));
                    if (pTag.contains("line_speed")) prereqLineSpeed.put(pid, pTag.getString("line_speed"));
                    if (pTag.contains("line_arrow")) prereqLineArrow.put(pid, pTag.getBoolean("line_arrow"));
                    if (pTag.contains("line_style_id")) prereqLineStyleId.put(pid, pTag.getString("line_style_id"));
                }
            }
            Integer optionalPrereqMinCount = tag.contains("optional_prereq_min_count") ?
                    tag.getInt("optional_prereq_min_count") : null;

            String enableIf = tag.contains("enable_if") ? tag.getString("enable_if") : null;
            boolean hideDepLine = tag.contains("hide_dep_line") && tag.getBoolean("hide_dep_line");
            boolean disabledBlocksChildren = tag.contains("disabled_blocks_children") &&
                    tag.getBoolean("disabled_blocks_children");
            boolean shared = tag.contains("shared") && tag.getBoolean("shared");
            boolean pooledProgress = tag.contains("pooled_progress") && tag.getBoolean("pooled_progress");
            boolean autoClaimRewards = tag.contains("auto_claim_rewards") && tag.getBoolean("auto_claim_rewards");
            boolean rewardChoice = tag.contains("reward_choice") && tag.getBoolean("reward_choice");
            int rewardChoiceCount = tag.contains("reward_choice_count") ? tag.getInt("reward_choice_count") : 1;
            String devNotes = tag.contains("dev_notes") ? tag.getString("dev_notes") : "";
            String previewMachineId = tag.contains("preview_machine_id") ? tag.getString("preview_machine_id") : "";
            QuestNode.NodeSize nodeSize = QuestNode.NodeSize.NORMAL;
            if (tag.contains("node_size")) {
                try {
                    nodeSize = QuestNode.NodeSize.valueOf(tag.getString("node_size").toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
            int sizeOverridePx = tag.contains("node_size_px") ? tag.getInt("node_size_px") : 0;

            ResourceLocation linkTarget = null;
            if (tag.contains("link_target") && !tag.getString("link_target").isEmpty()) {
                try {
                    linkTarget = ResourceLocation.parse(tag.getString("link_target"));
                } catch (Exception ignored) {}
            }

            boolean optional = tag.contains("optional") && tag.getBoolean("optional");
            Set<String> tags = new LinkedHashSet<>();
            if (tag.contains("tags")) {
                ListTag tagList = tag.getList("tags", Tag.TAG_STRING);
                for (int ti = 0; ti < tagList.size(); ti++) tags.add(tagList.getString(ti));
            }

            List<TutorialStep> tutorialSteps = new ArrayList<>();
            if (tag.contains("tutorial_steps")) {
                ListTag stepList = tag.getList("tutorial_steps", Tag.TAG_COMPOUND);
                for (int si = 0; si < stepList.size(); si++) {
                    CompoundTag st = stepList.getCompound(si);
                    String stepText = st.contains("text") ? st.getString("text") : "";
                    String highlight = st.contains("highlight") ? st.getString("highlight") : TutorialStep.HL_NONE;
                    if (!stepText.isBlank()) tutorialSteps.add(new TutorialStep(stepText, highlight));
                }
            }

            return new QuestRecord(id, title, desc, subtitle, chapter.toUpperCase(), shape.toUpperCase(),
                    iconItem, posX, posY, visibility, taskMinCount, parentId,
                    repeatMode, repeatCooldownHours, requireAllPrereqs, rewards, tasks, emergencyTag,
                    prereqRequired, optionalPrereqMinCount, enableIf, prereqForbidden, prereqLink, prereqCosmetic,
                    prereqLineShape, prereqLineVisual, prereqLineSpeed, prereqLineArrow, prereqLineStyleId,
                    hideDepLine, disabledBlocksChildren, shared, pooledProgress, tutorialSteps, autoClaimRewards,
                    rewardChoice, rewardChoiceCount, devNotes, nodeSize, sizeOverridePx, linkTarget, iconTexture,
                    shapeTexture, variants, previewMachineId, iconFluid, backgroundType, externalScreenId,
                    optional, tags);

        } catch (Exception e) {
            String msg = "Failed to parse '" + file.getFileName() + "': " + e.getMessage();
            LOAD_ERRORS.add(msg);
            System.err.println("[Phoenix Chronicles] " + msg);
            return null;
        }
    }

    private static List<QuestReward> parseRewards(CompoundTag tag) {
        List<QuestReward> rewards = new ArrayList<>();
        if (tag.contains("rewards")) {
            ListTag rewardList = tag.getList("rewards", Tag.TAG_COMPOUND);
            for (int ri = 0; ri < rewardList.size(); ri++) {
                QuestReward r = QuestReward.deserializeNBT(rewardList.getCompound(ri));
                if (r != null) rewards.add(r);
            }
        }
        return rewards;
    }

    private static List<QuestTask> parseTasks(CompoundTag tag) {
        List<QuestTask> tasks = new ArrayList<>();
        if (tag.contains("tasks")) {
            ListTag taskList = tag.getList("tasks", Tag.TAG_COMPOUND);
            for (int ti = 0; ti < taskList.size(); ti++) {
                QuestTask t = deserializeTask(taskList.getCompound(ti));
                if (t != null) tasks.add(t);
            }
        }
        return tasks;
    }

    private static QuestTask deserializeTask(CompoundTag tag) {
        if (!tag.contains("type") || !tag.contains("task_id")) return null;
        boolean optional = tag.contains("optional") && tag.getBoolean("optional");

        QuestTask task = PhoenixTaskRegistry.deserialize(tag);
        if (task != null) {
            task.setOptional(optional);
        }
        return task;
    }
}
