package net.phoenixvine.chronicles.codec;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class QuestFileSaver {

    public static void saveOneQuestToDisk(QuestNode node) {
        Path base = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
        try {
            Files.createDirectories(base);
            ResourceLocation parentId = null;
            for (QuestNode candidate : QuestTreeRegistry.getAllQuests().values()) {
                if (candidate.getChildren().contains(node)) {
                    parentId = candidate.getId();
                    break;
                }
            }
            saveNode(base, node, parentId);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to save quest '" + node.getId() + "': " + e.getMessage());
        }
    }

    public static void saveAllQuestsToDisk() {
        Path base = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");

        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        java.util.Map<net.minecraft.resources.ResourceLocation, ResourceLocation> childToParent = new java.util.HashMap<>();
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            for (QuestNode child : node.getChildren()) {
                childToParent.put(child.getId(), node.getId());
            }
        }

        int saved = 0;
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            try {
                saveNode(base, node, childToParent.get(node.getId()));
                saved++;
            } catch (Exception e) {

                System.err
                        .println("[Phoenix Chronicles] Failed to save quest '" + node.getId() + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        cleanupStaleQuestFiles(base);

        saveCategoryJsons(base);

        saveStubCategories(base);

        System.out.println("[Phoenix Chronicles] Saved " + saved + " quest(s) to disk.");
    }

    public static int exportTo(Path exportDir) {
        java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> childToParent = new java.util.HashMap<>();
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            for (QuestNode child : node.getChildren()) childToParent.put(child.getId(), node.getId());
        }
        int saved = 0;
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            try {
                saveNode(exportDir, node, childToParent.get(node.getId()));
                saved++;
            } catch (IOException e) {
                System.err.println("[Phoenix Chronicles] Export failed for '" + node.getId() + "': " + e.getMessage());
            }
        }
        System.out.println("[Phoenix Chronicles] Exported " + saved + " quest(s) to " + exportDir);
        return saved;
    }

    public static void saveNode(Path base, QuestNode node,
                                net.minecraft.resources.ResourceLocation parentId)
                                                                                   throws IOException {
        String id = node.getId().getPath();

        String title = node.getTitleRaw().getString();
        String desc = node.getDescriptionRaw().getString();
        String category = node.getCategory() != null ? node.getCategory() : "MAIN";
        String shape = node.getShapeType() != null ? node.getShapeType() : "SQUARE";
        String iconItem = node.getIconItemId();        
        String parent = parentId != null ? parentId.getPath() : "none";

        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("title", title);
        tag.putString("description", desc);
        tag.putString("category", category);
        tag.putString("shape", shape);
        if (node.getNodeSize() != QuestNode.NodeSize.NORMAL) tag.putString("node_size", node.getNodeSize().name());
        if (node.getSizeOverridePx() > 0) tag.putInt("node_size_px", node.getSizeOverridePx());
        tag.putString("parent", parent);
        tag.putInt("positionX", node.getCustomX());
        tag.putInt("positionY", node.getCustomY());
        if (!iconItem.isEmpty()) tag.putString("icon_item", iconItem);
        if (!node.getIconTexture().isEmpty()) tag.putString("icon_texture", node.getIconTexture());
        if (!node.getIconFluid().isEmpty()) tag.putString("icon_fluid", node.getIconFluid());
        if (!node.getShapeTexture().isEmpty()) tag.putString("shape_texture", node.getShapeTexture());

        if (!node.getSubtitleRaw().isEmpty()) tag.putString("subtitle", node.getSubtitleRaw());
        tag.putString("visibility", node.getVisibility().name());
        if (node.getEnableIf() != null) tag.putString("enable_if", node.getEnableIf());
        if (node.getTaskMinCount() > 0) tag.putInt("task_min_count", node.getTaskMinCount());
        if (node.isHideDepLine()) tag.putBoolean("hide_dep_line", true);
        if (node.isDisabledBlocksChildren()) tag.putBoolean("disabled_blocks_children", true);
        if (node.isShared()) tag.putBoolean("shared", true);
        if (node.isPooledProgress()) tag.putBoolean("pooled_progress", true);
        if (node.isLinkStub()) tag.putString("link_target", node.getLinkTarget().toString());
        if (node.isAutoClaimRewards()) tag.putBoolean("auto_claim_rewards", true);
        if (node.isRewardChoice()) {
            tag.putBoolean("reward_choice", true);
            if (node.getRewardChoiceCount() != 1) tag.putInt("reward_choice_count", node.getRewardChoiceCount());
        }
        if (!node.getDevNotes().isEmpty()) tag.putString("dev_notes", node.getDevNotes());
        if (!node.getPreviewMachineId().isEmpty())
            tag.putString("preview_machine_id", node.getPreviewMachineId());

        tag.putString("repeat_mode", node.getRepeatMode().name());
        tag.putInt("repeat_cooldown_hours", node.getRepeatCooldownHours());

        if (node.getRequireAllPrerequisites() != null)
            tag.putBoolean("require_all_prereqs", node.getRequireAllPrerequisites());
        if (!node.getPrerequisites().isEmpty()) {
            net.minecraft.nbt.ListTag prereqList = new net.minecraft.nbt.ListTag();
            for (QuestNode p : node.getPrerequisites()) {
                CompoundTag pTag = new CompoundTag();
                pTag.putString("id", p.getId().getPath());
                if (node.isPrereqForbidden(p.getId())) {
                    pTag.putBoolean("forbidden", true);
                } else {
                    pTag.putBoolean("required", node.isPrereqRequired(p.getId()));
                }
                if (node.isPrereqLink(p.getId())) pTag.putBoolean("link", true);
                if (node.isPrereqCosmetic(p.getId())) pTag.putBoolean("cosmetic", true);
                if (node.getPrereqLineShape(p.getId()) != null)
                    pTag.putString("line_shape", node.getPrereqLineShape(p.getId()).name());
                if (node.getPrereqLineVisual(p.getId()) != null)
                    pTag.putString("line_style", node.getPrereqLineVisual(p.getId()).name());
                if (node.getPrereqLineSpeed(p.getId()) != null)
                    pTag.putString("line_speed", node.getPrereqLineSpeed(p.getId()).name());
                if (node.getPrereqLineArrow(p.getId()) != null)
                    pTag.putBoolean("line_arrow", node.getPrereqLineArrow(p.getId()));
                prereqList.add(pTag);
            }
            tag.put("prerequisites", prereqList);
        }
        if (node.getOptionalPrereqMinCount() != null)
            tag.putInt("optional_prereq_min_count", node.getOptionalPrereqMinCount());

        if (!node.getTasks().isEmpty()) {
            net.minecraft.nbt.ListTag taskList = new net.minecraft.nbt.ListTag();
            for (QuestTask t : node.getTasks()) {
                CompoundTag tTag = t.serializeNBT();
                tTag.putString("task_id", t.getTaskId().toString());
                tTag.putString("description",
                        net.minecraft.network.chat.Component.Serializer.toJson(t.getDescriptionRaw()));
                tTag.putBoolean("optional", t.isOptional());
                taskList.add(tTag);
            }
            tag.put("tasks", taskList);
        }

        if (!node.getRewards().isEmpty()) {
            net.minecraft.nbt.ListTag rewardList = new net.minecraft.nbt.ListTag();
            for (QuestReward r : node.getRewards()) rewardList.add(r.serializeNBT());
            tag.put("rewards", rewardList);
        }

        if (!node.getVariants().isEmpty()) {
            net.minecraft.nbt.ListTag variantList = new net.minecraft.nbt.ListTag();
            for (QuestNode.QuestVariant v : node.getVariants()) {
                CompoundTag vTag = new CompoundTag();
                vTag.putString("condition", v.condition);
                if (v.title != null) vTag.putString("title", v.title);
                if (v.description != null) vTag.putString("description", v.description);
                if (v.visibility != null) vTag.putString("visibility", v.visibility.name());
                if (v.tasks != null) {
                    net.minecraft.nbt.ListTag taskList = new net.minecraft.nbt.ListTag();
                    for (QuestTask t : v.tasks) {
                        CompoundTag tTag = t.serializeNBT();
                        tTag.putString("task_id", t.getTaskId().toString());
                        tTag.putString("description",
                                net.minecraft.network.chat.Component.Serializer.toJson(t.getDescriptionRaw()));
                        tTag.putBoolean("optional", t.isOptional());
                        taskList.add(tTag);
                    }
                    vTag.put("tasks", taskList);
                }
                if (v.rewards != null) {
                    net.minecraft.nbt.ListTag rewardList = new net.minecraft.nbt.ListTag();
                    for (QuestReward r : v.rewards) rewardList.add(r.serializeNBT());
                    vTag.put("rewards", rewardList);
                }
                variantList.add(vTag);
            }
            tag.put("variants", variantList);
        }

        if (!node.getEmergencyItems().isEmpty()) {
            tag.put("emergency_items", node.serializeEmergencyItems());
        }

        Path categoryFolder = base.resolve("quests").resolve(category.toLowerCase(Locale.ROOT));
        Path snbtPath = categoryFolder.resolve(id + ".snbt");
        Files.createDirectories(snbtPath.getParent());
        Files.writeString(snbtPath, tag.toString(), StandardCharsets.UTF_8);

        Path mdPath = categoryFolder.resolve(id + ".md");

        Files.writeString(mdPath,
                "# " + title + "\n\n" + (desc.isEmpty() ? "" : desc + "\n"),
                StandardCharsets.UTF_8);
    }

    private static void cleanupStaleQuestFiles(Path base) {
        Map<String, Path> expected = new HashMap<>();
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            String category = node.getCategory() != null ? node.getCategory() : "MAIN";
            Path folder = base.resolve("quests").resolve(category.toLowerCase(Locale.ROOT));
            expected.put(node.getId().getPath(), folder.resolve(node.getId().getPath() + ".snbt"));
        }
        if (expected.isEmpty()) return;

        try (Stream<Path> walk = Files.walk(base)) {
            List<Path> snbtFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".snbt"))
                    .toList();
            for (Path p : snbtFiles) {
                String fileName = p.getFileName().toString();
                String id = fileName.substring(0, fileName.length() - 5);
                Path exp = expected.get(id);
                if (exp != null && !p.equals(exp)) {
                    Files.deleteIfExists(p);
                    Files.deleteIfExists(p.resolveSibling(id + ".md"));
                }
            }
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to clean up stale quest files: " + e.getMessage());
        }
    }

    private static void saveCategoryJsons(Path base) {
        Map<String, QuestNode> representative = new HashMap<>();
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            String category = node.getCategory() != null ? node.getCategory() : "MAIN";
            representative.putIfAbsent(category, node);
            if (!node.getIconItemId().isEmpty() && representative.get(category).getIconItemId().isEmpty()) {
                representative.put(category, node);
            }
        }

        Path questsBase = base.resolve("quests");
        for (Map.Entry<String, QuestNode> e : representative.entrySet()) {
            String category = e.getKey();
            Path categoryFolder = questsBase.resolve(category.toLowerCase(Locale.ROOT));
            try {
                Files.createDirectories(categoryFolder);
                writeCategoryJson(categoryFolder, category, e.getValue());
            } catch (IOException ex) {
                System.err.println(
                        "[Phoenix Chronicles] Failed to save category json for '" + category + "': " + ex.getMessage());
            }
        }
    }

    private static void writeCategoryJson(Path categoryFolder, String category,
                                          QuestNode representative) throws IOException {
        Path jsonPath = categoryFolder.resolve(category.toLowerCase(Locale.ROOT) + ".json");

        JsonObject json = new JsonObject();
        if (Files.exists(jsonPath)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(jsonPath, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) json = parsed.getAsJsonObject();
            } catch (Exception ignored) {}
        }

        json.addProperty("id", category.toLowerCase(Locale.ROOT));
        if (!json.has("name")) json.addProperty("name", humanizeCategory(category));
        if (!json.has("icon")) {
            String icon = representative != null && !representative.getIconItemId().isEmpty() ?
                    representative.getIconItemId() : "minecraft:book";
            json.addProperty("icon", icon);
        }
        if (!json.has("order")) json.addProperty("order", 0);
        if (!json.has("background")) json.add("background", com.google.gson.JsonNull.INSTANCE);
        if (!json.has("requirements")) json.add("requirements", new JsonArray());

        Files.writeString(jsonPath, json.toString(), StandardCharsets.UTF_8);
    }

    private static String humanizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "Quests";
        String[] words = raw.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.length() > 1 ? w.substring(1) : "");
        }
        return sb.length() == 0 ? "Quests" : sb.toString();
    }

    private static void saveStubCategories(Path base) {
        try {
            
            Set<String> questCats = new HashSet<>();
            questCats.add("ALL");
            for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
                if (n.getCategory() != null) questCats.add(n.getCategory());
            }

            Path catFile = base.resolve("categories.txt");
            java.util.List<String> stubs = new java.util.ArrayList<>();
            if (Files.exists(catFile)) {
                for (String line : Files.readAllLines(catFile, StandardCharsets.UTF_8)) {
                    String c = line.trim().toUpperCase();
                    if (!c.isEmpty() && !questCats.contains(c)) stubs.add(c);
                }
            }

            Files.writeString(catFile, String.join("\n", stubs), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to save categories.txt: " + e.getMessage());
        }
    }

    public static Path getQuestSnbtPath(QuestNode node) {
        String category = node.getCategory() != null ? node.getCategory() : "MAIN";
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles")
                .resolve("quests").resolve(category.toLowerCase(Locale.ROOT))
                .resolve(node.getId().getPath() + ".snbt");
    }

    public static void patchNodeTag(QuestNode node, java.util.function.Consumer<CompoundTag> mutator) {
        try {
            Path p = getQuestSnbtPath(node);
            if (!Files.exists(p)) return;
            CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(
                    Files.readString(p, StandardCharsets.UTF_8));
            mutator.accept(tag);
            Files.writeString(p, tag.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println(
                    "[Phoenix Chronicles] Failed to patch quest file for '" + node.getId() + "': " + e.getMessage());
        }
    }

    public static void updateNodePosition(QuestNode node) {
        patchNodeTag(node, tag -> {
            tag.putInt("positionX", node.getCustomX());
            tag.putInt("positionY", node.getCustomY());
        });
    }

    public static void updateNodeShape(QuestNode node, String shape) {
        patchNodeTag(node, tag -> tag.putString("shape", shape));
    }

    public static void updateNodeShapeTexture(QuestNode node) {
        patchNodeTag(node, tag -> {
            String texture = node.getShapeTexture();
            if (texture == null || texture.isEmpty()) tag.remove("shape_texture");
            else tag.putString("shape_texture", texture);
        });
    }

    public static void updateNodeCategory(QuestNode node, String cat) {
        patchNodeTag(node, tag -> tag.putString("category", cat));
    }

    public static void updateNodeIconAll(QuestNode node) {
        patchNodeTag(node, tag -> {
            String iconId = node.getIconItemId();
            if (iconId == null || iconId.isEmpty()) tag.remove("icon_item");
            else tag.putString("icon_item", iconId);
            String texture = node.getIconTexture();
            if (texture == null || texture.isEmpty()) tag.remove("icon_texture");
            else tag.putString("icon_texture", texture);
            String fluid = node.getIconFluid();
            if (fluid == null || fluid.isEmpty()) tag.remove("icon_fluid");
            else tag.putString("icon_fluid", fluid);
        });
    }

    public static void updateHideDepLine(QuestNode node) {
        patchNodeTag(node, tag -> {
            if (node.isHideDepLine()) tag.putBoolean("hide_dep_line", true);
            else tag.remove("hide_dep_line");
        });
    }

    public static void deleteQuestFiles(QuestNode node) {
        try {
            Path snbt = getQuestSnbtPath(node);
            Files.deleteIfExists(snbt);
            Files.deleteIfExists(snbt.resolveSibling(node.getId().getPath() + ".md"));
        } catch (IOException e) {
            System.err.println(
                    "[Phoenix Chronicles] Failed to delete files for '" + node.getId() + "': " + e.getMessage());
        }
    }

    public static void updateNodePrerequisites(QuestNode node) {
        patchNodeTag(node, tag -> {
            net.minecraft.nbt.ListTag prereqList = new net.minecraft.nbt.ListTag();

            for (QuestNode req : node.getPrerequisites()) {
                CompoundTag pTag = new CompoundTag();
                pTag.putString("id", req.getId().getPath());

                if (node.isPrereqForbidden(req.getId())) {
                    pTag.putBoolean("forbidden", true);
                } else {
                    pTag.putBoolean("required", node.isPrereqRequired(req.getId()));
                }

                if (node.isPrereqLink(req.getId())) pTag.putBoolean("link", true);
                if (node.isPrereqCosmetic(req.getId())) pTag.putBoolean("cosmetic", true);

                if (node.getPrereqLineShape(req.getId()) != null)
                    pTag.putString("line_shape", node.getPrereqLineShape(req.getId()).name());
                if (node.getPrereqLineVisual(req.getId()) != null)
                    pTag.putString("line_style", node.getPrereqLineVisual(req.getId()).name());
                if (node.getPrereqLineSpeed(req.getId()) != null)
                    pTag.putString("line_speed", node.getPrereqLineSpeed(req.getId()).name());
                if (node.getPrereqLineArrow(req.getId()) != null)
                    pTag.putBoolean("line_arrow", node.getPrereqLineArrow(req.getId()));

                prereqList.add(pTag);
            }

            if (!prereqList.isEmpty()) {
                tag.put("prerequisites", prereqList);
            } else {
                tag.remove("prerequisites");
            }
        });
    }

    public static Path getQuestCategoryFolder(QuestNode node) {
        String category = node.getCategory() != null ? node.getCategory() : "MAIN";
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles")
                .resolve("quests").resolve(category.toLowerCase(Locale.ROOT));
    }

    public static String readRawSnbt(QuestNode node) {
        try {
            Path p = getQuestSnbtPath(node);
            return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    public static void restoreRawSnbt(QuestNode node, String content) {
        if (content == null || content.isEmpty()) return;
        try {
            Path p = getQuestSnbtPath(node);
            Files.createDirectories(p.getParent());
            Files.writeString(p, content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    public static String pasteQuestFromSnbt(String src) throws IOException {
        Path base = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("id:\\s*\"([^\"]+)\"").matcher(src);
        String srcPath = m.find() ? m.group(1) : "pasted_quest";

        String newPath = srcPath + "_copy";
        for (int i = 2; Files.exists(base.resolve(newPath + ".snbt")); i++) {
            newPath = srcPath + "_copy" + i;
        }

        String content = src.replaceFirst("id:\\s*\"[^\"]*\"", "id: \"" + newPath + "\"");

        content = offsetSnbtCoord(content, "positionX", 56);
        content = offsetSnbtCoord(content, "positionY", 56);

        Files.writeString(base.resolve(newPath + ".snbt"), content, StandardCharsets.UTF_8);
        QuestFileLoader.loadAdditiveFromDisk(base);

        return newPath;
    }

    private static String offsetSnbtCoord(String snbt, String key, int offset) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(key + ":\\s*(-?\\d+)").matcher(snbt);
        if (m.find()) {
            int originalVal = Integer.parseInt(m.group(1));
            return snbt.replaceFirst(key + ":\\s*-?\\d+", key + ": " + (originalVal + offset));
        }
        return snbt;
    }

    public static Path getQuestMarkdownPath(QuestNode node) {
        return getQuestCategoryFolder(node).resolve(node.getId().getPath() + ".md");
    }

    public static void updateNodeHideDepLine(QuestNode node) {
        patchNodeTag(node, tag -> {
            if (node.isHideDepLine()) {
                tag.putBoolean("hide_dep_line", true);
            } else {
                tag.remove("hide_dep_line");
            }
        });
    }

    public static void saveNodeToDisk(QuestNode node) {
        patchNodeTag(node, tag -> {
            tag.putInt("positionX", node.getCustomX());
            tag.putInt("positionY", node.getCustomY());
        });
    }

    public static String duplicateQuestOnDisk(QuestNode source) throws IOException {
        Path srcFile = getQuestSnbtPath(source);
        if (!Files.exists(srcFile)) {
            throw new FileNotFoundException("Source file not found on disk");
        }

        String content = Files.readString(srcFile, StandardCharsets.UTF_8);

        String srcPath = source.getId().getPath();
        String newPath = srcPath + "_copy";
        for (int i = 2; Files.exists(srcFile.resolveSibling(newPath + ".snbt")); i++) {
            newPath = srcPath + "_copy" + i;
        }

        content = content.replaceFirst("id:\\s*\"[^\"]*\"", "id: \"" + newPath + "\"");

        content = offsetSnbtCoord(content, "positionX", 48);
        content = offsetSnbtCoord(content, "positionY", 48);

        Path destFile = srcFile.resolveSibling(newPath + ".snbt");
        Files.writeString(destFile, content, StandardCharsets.UTF_8);

        Path base = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
        QuestFileLoader.loadAdditiveFromDisk(base);

        return newPath;
    }

    public static boolean doesQuestFileExist(QuestNode node) {
        try {
            return java.nio.file.Files.exists(getQuestSnbtPath(node));
        } catch (Exception e) {
            return false;
        }
    }
}

