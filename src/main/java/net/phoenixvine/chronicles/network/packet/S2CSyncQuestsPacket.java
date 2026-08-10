package net.phoenixvine.chronicles.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class S2CSyncQuestsPacket {

    private final Map<ResourceLocation, QuestSnapshot> snapshotMap;

    public S2CSyncQuestsPacket(Map<ResourceLocation, QuestNode> serverRegistry,
                               net.minecraft.server.MinecraftServer server) {
        this.snapshotMap = new HashMap<>();
        for (Map.Entry<ResourceLocation, QuestNode> entry : serverRegistry.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                snapshotMap.put(entry.getKey(), new QuestSnapshot(entry.getValue(), server));
            }
        }
    }

    public S2CSyncQuestsPacket(FriendlyByteBuf buf) {
        this.snapshotMap = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buf.readResourceLocation();
            Component title = buf.readComponent();
            Component description = buf.readComponent();
            String chapter = buf.readUtf();
            String shapeType = buf.readUtf();
            String iconItemId = buf.readUtf();
            int customX = buf.readInt();
            int customY = buf.readInt();

            String subtitle = buf.readUtf();
            String visibility = buf.readUtf();
            String enableIf = buf.readUtf();
            int taskMinCount = buf.readInt();
            Boolean requireAllPrerequisites = buf.readNullable(FriendlyByteBuf::readBoolean);

            int childCount = buf.readInt();
            List<ResourceLocation> childIds = new ArrayList<>(childCount);
            for (int c = 0; c < childCount; c++) {
                ResourceLocation childId = buf.readNullable(FriendlyByteBuf::readResourceLocation);
                if (childId != null) childIds.add(childId);
            }

            int prereqCount = buf.readInt();
            List<ResourceLocation> prereqIds = new ArrayList<>(prereqCount);
            List<Boolean> prereqRequired = new ArrayList<>(prereqCount);
            List<Boolean> prereqForbidden = new ArrayList<>(prereqCount);
            List<Boolean> prereqLink = new ArrayList<>(prereqCount);
            List<Boolean> prereqCosmetic = new ArrayList<>(prereqCount);
            List<String> prereqLineShape = new ArrayList<>(prereqCount);
            List<String> prereqLineVisual = new ArrayList<>(prereqCount);
            List<String> prereqLineSpeed = new ArrayList<>(prereqCount);
            List<String> prereqLineArrow = new ArrayList<>(prereqCount);
            List<String> prereqLineStyleId = new ArrayList<>(prereqCount);

            for (int p = 0; p < prereqCount; p++) {
                ResourceLocation pId = buf.readNullable(FriendlyByteBuf::readResourceLocation);
                if (pId != null) {
                    prereqIds.add(pId);
                }
                prereqRequired.add(buf.readBoolean());
                prereqForbidden.add(buf.readBoolean());
                prereqLink.add(buf.readBoolean());
                prereqCosmetic.add(buf.readBoolean());
                prereqLineShape.add(buf.readUtf());
                prereqLineVisual.add(buf.readUtf());
                prereqLineSpeed.add(buf.readUtf());
                prereqLineArrow.add(buf.readUtf());
                prereqLineStyleId.add(buf.readUtf());
            }

            Integer optionalPrereqMinCount = buf.readNullable(FriendlyByteBuf::readInt);

            int taskCount = buf.readInt();
            List<CompoundTag> tasksNbt = new ArrayList<>(taskCount);
            for (int t = 0; t < taskCount; t++) tasksNbt.add(buf.readNbt());

            ResourceLocation linkTarget = buf.readNullable(FriendlyByteBuf::readResourceLocation);
            String iconTexture = buf.readUtf();
            String shapeTexture = buf.readUtf();
            String nodeSize = buf.readUtf();
            int sizeOverridePx = buf.readInt();
            String iconFluid = buf.readUtf();
            String backgroundType = buf.readUtf();
            String externalScreenId = buf.readUtf();

            snapshotMap.put(id, new QuestSnapshot(
                    id, title, description, chapter, shapeType, iconItemId,
                    customX, customY, subtitle, visibility, enableIf, taskMinCount, requireAllPrerequisites,
                    childIds, prereqIds, prereqRequired, prereqForbidden, prereqLink, prereqCosmetic,
                    prereqLineShape, prereqLineVisual, prereqLineSpeed, prereqLineArrow, prereqLineStyleId,
                    optionalPrereqMinCount, tasksNbt, linkTarget, iconTexture, shapeTexture,
                    nodeSize, sizeOverridePx, iconFluid, backgroundType, externalScreenId));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(snapshotMap.size());
        for (QuestSnapshot snap : snapshotMap.values()) {
            buf.writeResourceLocation(snap.id);
            buf.writeComponent(snap.title != null ? snap.title : Component.empty());
            buf.writeComponent(snap.description != null ? snap.description : Component.empty());
            buf.writeUtf(snap.chapter != null ? snap.chapter : "MAIN");
            buf.writeUtf(snap.shapeType != null ? snap.shapeType : "SQUARE");
            buf.writeUtf(snap.iconItemId != null ? snap.iconItemId : "");
            buf.writeInt(snap.customX);
            buf.writeInt(snap.customY);
            buf.writeUtf(snap.subtitle != null ? snap.subtitle : "");
            buf.writeUtf(snap.visibility != null ? snap.visibility : "VISIBLE");
            buf.writeUtf(snap.enableIf != null ? snap.enableIf : "");
            buf.writeInt(snap.taskMinCount);
            buf.writeNullable(snap.requireAllPrerequisites, FriendlyByteBuf::writeBoolean);

            buf.writeInt(snap.childIds.size());
            for (ResourceLocation cId : snap.childIds) {
                buf.writeNullable(cId, FriendlyByteBuf::writeResourceLocation);
            }

            buf.writeInt(snap.prereqIds.size());
            for (int pi = 0; pi < snap.prereqIds.size(); pi++) {
                buf.writeNullable(snap.prereqIds.get(pi), FriendlyByteBuf::writeResourceLocation);
                buf.writeBoolean(pi < snap.prereqRequired.size() && Boolean.TRUE.equals(snap.prereqRequired.get(pi)));
                buf.writeBoolean(pi < snap.prereqForbidden.size() && Boolean.TRUE.equals(snap.prereqForbidden.get(pi)));
                buf.writeBoolean(pi < snap.prereqLink.size() && Boolean.TRUE.equals(snap.prereqLink.get(pi)));
                buf.writeBoolean(pi < snap.prereqCosmetic.size() && Boolean.TRUE.equals(snap.prereqCosmetic.get(pi)));
                buf.writeUtf(pi < snap.prereqLineShape.size() && snap.prereqLineShape.get(pi) != null ?
                        snap.prereqLineShape.get(pi) : "");
                buf.writeUtf(pi < snap.prereqLineVisual.size() && snap.prereqLineVisual.get(pi) != null ?
                        snap.prereqLineVisual.get(pi) : "");
                buf.writeUtf(pi < snap.prereqLineSpeed.size() && snap.prereqLineSpeed.get(pi) != null ?
                        snap.prereqLineSpeed.get(pi) : "");
                buf.writeUtf(pi < snap.prereqLineArrow.size() && snap.prereqLineArrow.get(pi) != null ?
                        snap.prereqLineArrow.get(pi) : "");
                buf.writeUtf(pi < snap.prereqLineStyleId.size() && snap.prereqLineStyleId.get(pi) != null ?
                        snap.prereqLineStyleId.get(pi) : "");
            }
            buf.writeNullable(snap.optionalPrereqMinCount, FriendlyByteBuf::writeInt);

            buf.writeInt(snap.tasksNbt.size());
            for (CompoundTag tag : snap.tasksNbt) buf.writeNbt(tag);

            buf.writeNullable(snap.linkTarget, FriendlyByteBuf::writeResourceLocation);
            buf.writeUtf(snap.iconTexture != null ? snap.iconTexture : "");
            buf.writeUtf(snap.shapeTexture != null ? snap.shapeTexture : "");
            buf.writeUtf(snap.nodeSize != null ? snap.nodeSize : "NORMAL");
            buf.writeInt(snap.sizeOverridePx);
            buf.writeUtf(snap.iconFluid != null ? snap.iconFluid : "");
            buf.writeUtf(snap.backgroundType != null ? snap.backgroundType : "");
            buf.writeUtf(snap.externalScreenId != null ? snap.externalScreenId : "");
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    if (net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) return;
                    ClientPayloadProcessor.processQuestTree(snapshotMap);
                }));
        ctx.get().setPacketHandled(true);
    }

    private static class QuestSnapshot {

        final ResourceLocation id;
        final Component title;
        final Component description;
        final String chapter;
        final String shapeType;
        final String iconItemId;
        final int customX;
        final int customY;

        final String subtitle;
        final String visibility;
        final String enableIf;
        final int taskMinCount;

        final Boolean requireAllPrerequisites;
        final List<ResourceLocation> childIds;
        final List<ResourceLocation> prereqIds;

        final List<Boolean> prereqRequired;
        final List<Boolean> prereqForbidden;
        final List<Boolean> prereqLink;
        final List<Boolean> prereqCosmetic;

        final List<String> prereqLineShape;
        final List<String> prereqLineVisual;
        final List<String> prereqLineSpeed;
        final List<String> prereqLineArrow;
        final List<String> prereqLineStyleId;

        final Integer optionalPrereqMinCount;
        final List<CompoundTag> tasksNbt;

        final ResourceLocation linkTarget;
        final String iconTexture;
        final String shapeTexture;
        final String nodeSize;
        final int sizeOverridePx;
        final String iconFluid;
        final String backgroundType;
        final String externalScreenId;

        QuestSnapshot(QuestNode node, net.minecraft.server.MinecraftServer server) {
            this.id = node.getId();
            this.title = node.getEffectiveTitleRaw(server);
            this.description = node.getEffectiveDescriptionRaw(server);
            this.chapter = node.getChapter() != null ? node.getChapter() : "MAIN";
            this.shapeType = node.getShapeType() != null ? node.getShapeType() : "SQUARE";
            this.iconItemId = node.getIconItemId();
            this.customX = node.getCustomX();
            this.customY = node.getCustomY();
            this.subtitle = node.getSubtitle() != null ? node.getSubtitle() : "";
            this.visibility = node.getEffectiveVisibility(server) != null ? node.getEffectiveVisibility(server).name() :
                    "VISIBLE";
            this.enableIf = node.getEnableIf() != null ? node.getEnableIf() : "";
            this.taskMinCount = node.getTaskMinCount();
            this.requireAllPrerequisites = node.getRequireAllPrerequisites();
            this.optionalPrereqMinCount = node.getOptionalPrereqMinCount();
            this.linkTarget = node.getLinkTarget();
            this.iconTexture = node.getIconTexture() != null ? node.getIconTexture() : "";
            this.shapeTexture = node.getShapeTexture() != null ? node.getShapeTexture() : "";
            this.nodeSize = node.getNodeSize() != null ? node.getNodeSize().name() : "NORMAL";
            this.sizeOverridePx = node.getSizeOverridePx();
            this.iconFluid = node.getIconFluid() != null ? node.getIconFluid() : "";
            this.backgroundType = node.getBackgroundType() != null ? node.getBackgroundType() : "";
            this.externalScreenId = node.getExternalScreenId() != null ? node.getExternalScreenId() : "";

            this.childIds = new ArrayList<>();
            for (QuestNode child : node.getChildren()) {
                if (child != null && child.getId() != null) {
                    childIds.add(child.getId());
                }
            }

            this.prereqIds = new ArrayList<>();
            this.prereqRequired = new ArrayList<>();
            this.prereqForbidden = new ArrayList<>();
            this.prereqLink = new ArrayList<>();
            this.prereqCosmetic = new ArrayList<>();
            this.prereqLineShape = new ArrayList<>();
            this.prereqLineVisual = new ArrayList<>();
            this.prereqLineSpeed = new ArrayList<>();
            this.prereqLineArrow = new ArrayList<>();
            this.prereqLineStyleId = new ArrayList<>();

            for (QuestNode req : node.getPrerequisites()) {
                if (req == null || req.getId() == null) continue;
                prereqIds.add(req.getId());
                prereqRequired.add(node.isPrereqRequired(req.getId()));
                prereqForbidden.add(node.isPrereqForbidden(req.getId()));
                prereqLink.add(node.isPrereqLink(req.getId()));
                prereqCosmetic.add(node.isPrereqCosmetic(req.getId()));
                prereqLineShape.add(node.getPrereqLineShape(req.getId()) != null ?
                        node.getPrereqLineShape(req.getId()).name() : "");
                prereqLineVisual.add(node.getPrereqLineVisual(req.getId()) != null ?
                        node.getPrereqLineVisual(req.getId()).name() : "");
                prereqLineSpeed.add(node.getPrereqLineSpeed(req.getId()) != null ?
                        node.getPrereqLineSpeed(req.getId()).name() : "");
                Boolean arrow = node.getPrereqLineArrow(req.getId());
                prereqLineArrow.add(arrow != null ? arrow.toString().toUpperCase() : "");
                String styleId = node.getPrereqLineStyleId(req.getId());
                prereqLineStyleId.add(styleId != null ? styleId : "");
            }

            this.tasksNbt = new ArrayList<>();
            for (QuestTask task : node.getEffectiveTasks(server)) {
                if (task == null) continue;
                CompoundTag tag = task.serializeNBT();
                if (tag != null) {
                    if (!tag.contains("task_id") && task.getTaskId() != null)
                        tag.putString("task_id", task.getTaskId().toString());
                    if (!tag.contains("description") && task.getDescription() != null)
                        tag.putString("description", Component.Serializer.toJson(task.getDescription()));
                    tag.putBoolean("optional", task.isOptional());
                    tasksNbt.add(tag);
                }
            }
        }

        QuestSnapshot(ResourceLocation id, Component title, Component description,
                      String chapter, String shapeType, String iconItemId,
                      int customX, int customY,
                      String subtitle, String visibility, String enableIf, int taskMinCount,
                      Boolean requireAllPrerequisites,
                      List<ResourceLocation> childIds, List<ResourceLocation> prereqIds,
                      List<Boolean> prereqRequired, List<Boolean> prereqForbidden, List<Boolean> prereqLink,
                      List<Boolean> prereqCosmetic,
                      List<String> prereqLineShape, List<String> prereqLineVisual, List<String> prereqLineSpeed,
                      List<String> prereqLineArrow, List<String> prereqLineStyleId,
                      Integer optionalPrereqMinCount,
                      List<CompoundTag> tasksNbt,
                      ResourceLocation linkTarget, String iconTexture, String shapeTexture,
                      String nodeSize, int sizeOverridePx, String iconFluid,
                      String backgroundType, String externalScreenId) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.chapter = chapter;
            this.shapeType = shapeType;
            this.iconItemId = iconItemId;
            this.customX = customX;
            this.customY = customY;
            this.subtitle = subtitle;
            this.visibility = visibility;
            this.enableIf = enableIf;
            this.taskMinCount = taskMinCount;
            this.requireAllPrerequisites = requireAllPrerequisites;
            this.childIds = childIds;
            this.prereqIds = prereqIds;
            this.prereqRequired = prereqRequired;
            this.prereqForbidden = prereqForbidden;
            this.prereqLink = prereqLink;
            this.prereqCosmetic = prereqCosmetic;
            this.prereqLineShape = prereqLineShape;
            this.prereqLineVisual = prereqLineVisual;
            this.prereqLineSpeed = prereqLineSpeed;
            this.prereqLineArrow = prereqLineArrow;
            this.prereqLineStyleId = prereqLineStyleId;
            this.optionalPrereqMinCount = optionalPrereqMinCount;
            this.tasksNbt = tasksNbt;
            this.linkTarget = linkTarget;
            this.iconTexture = iconTexture;
            this.shapeTexture = shapeTexture;
            this.nodeSize = nodeSize;
            this.sizeOverridePx = sizeOverridePx;
            this.iconFluid = iconFluid;
            this.backgroundType = backgroundType;
            this.externalScreenId = externalScreenId;
        }
    }

    private static class ClientPayloadProcessor {

        static void processQuestTree(Map<ResourceLocation, QuestSnapshot> snapshots) {
            QuestTreeRegistry.clear();

            for (QuestSnapshot snap : snapshots.values()) {
                if (snap.id == null) continue;
                QuestNode node = new QuestNode(snap.id, snap.title, snap.description);
                node.setChapter(snap.chapter);
                node.setShapeType(snap.shapeType);
                node.setCustomX(snap.customX);
                node.setCustomY(snap.customY);
                node.setSubtitle(snap.subtitle);
                node.setTaskMinCount(snap.taskMinCount);
                node.setRequireAllPrerequisites(snap.requireAllPrerequisites);
                node.setOptionalPrereqMinCount(snap.optionalPrereqMinCount);
                try {
                    if (snap.visibility != null) node.setVisibility(QuestNode.Visibility.valueOf(snap.visibility));
                } catch (Exception ignored) {}
                node.setEnableIf(snap.enableIf != null && snap.enableIf.isEmpty() ? null : snap.enableIf);
                node.setLinkTarget(snap.linkTarget);
                node.setIconTexture(snap.iconTexture);
                node.setIconFluid(snap.iconFluid);
                node.setShapeTexture(snap.shapeTexture);
                node.setBackgroundType(snap.backgroundType);
                node.setExternalScreenId(snap.externalScreenId);
                try {
                    if (snap.nodeSize != null) node.setNodeSize(QuestNode.NodeSize.valueOf(snap.nodeSize));
                } catch (Exception ignored) {}
                if (snap.sizeOverridePx > 0) node.setSizeOverridePx(snap.sizeOverridePx);

                if (snap.iconItemId != null && !snap.iconItemId.isEmpty()) {
                    node.setIconItemById(snap.iconItemId);
                }

                for (CompoundTag tag : snap.tasksNbt) {
                    if (tag == null) continue;
                    QuestTask task = deserializeTask(tag);
                    if (task != null) {
                        if (tag.contains("optional")) task.setOptional(tag.getBoolean("optional"));
                        node.addTask(task);
                    }
                }

                QuestTreeRegistry.registerBareQuestNode(node);
            }

            Set<ResourceLocation> hasParent = new HashSet<>();
            for (QuestSnapshot snap : snapshots.values()) {
                if (snap.childIds != null) {
                    for (ResourceLocation cId : snap.childIds) {
                        if (cId != null) hasParent.add(cId);
                    }
                }
            }

            for (QuestSnapshot snap : snapshots.values()) {
                if (snap.id == null) continue;
                QuestNode node = QuestTreeRegistry.getQuest(snap.id);
                if (node == null) continue;

                if (snap.childIds != null) {
                    for (ResourceLocation childId : snap.childIds) {
                        if (childId == null) continue;
                        QuestNode child = QuestTreeRegistry.getQuest(childId);
                        if (child != null) node.addChild(child);
                    }
                }

                if (snap.prereqIds != null) {
                    for (int pi = 0; pi < snap.prereqIds.size(); pi++) {
                        ResourceLocation reqId = snap.prereqIds.get(pi);
                        if (reqId == null) continue;
                        QuestNode req = QuestTreeRegistry.getQuest(reqId);
                        if (req != null) {
                            node.addPrerequisite(req);
                            boolean forbidden = pi < snap.prereqForbidden.size() &&
                                    Boolean.TRUE.equals(snap.prereqForbidden.get(pi));
                            if (forbidden) {
                                node.setPrereqForbidden(req.getId(), true);
                            } else {
                                boolean required = pi < snap.prereqRequired.size() &&
                                        Boolean.TRUE.equals(snap.prereqRequired.get(pi));
                                node.setPrereqRequired(req.getId(), required);
                            }
                            if (pi < snap.prereqLink.size() && Boolean.TRUE.equals(snap.prereqLink.get(pi))) {
                                node.setPrereqLink(req.getId(), true);
                            }
                            if (pi < snap.prereqCosmetic.size() && Boolean.TRUE.equals(snap.prereqCosmetic.get(pi))) {
                                node.setPrereqCosmetic(req.getId(), true);
                            }
                            if (pi < snap.prereqLineShape.size() && snap.prereqLineShape.get(pi) != null &&
                                    !snap.prereqLineShape.get(pi).isEmpty()) {
                                try {
                                    node.setPrereqLineShape(req.getId(),
                                            net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineStyle
                                                    .valueOf(snap.prereqLineShape.get(pi)));
                                } catch (IllegalArgumentException ignored) {}
                            }
                            if (pi < snap.prereqLineVisual.size() && snap.prereqLineVisual.get(pi) != null &&
                                    !snap.prereqLineVisual.get(pi).isEmpty()) {
                                try {
                                    node.setPrereqLineVisual(req.getId(),
                                            net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineVisualStyle
                                                    .valueOf(snap.prereqLineVisual.get(pi)));
                                } catch (IllegalArgumentException ignored) {}
                            }
                            if (pi < snap.prereqLineSpeed.size() && snap.prereqLineSpeed.get(pi) != null &&
                                    !snap.prereqLineSpeed.get(pi).isEmpty()) {
                                try {
                                    node.setPrereqLineSpeed(req.getId(),
                                            net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineAnimSpeed
                                                    .valueOf(snap.prereqLineSpeed.get(pi)));
                                } catch (IllegalArgumentException ignored) {}
                            }
                            if (pi < snap.prereqLineArrow.size() && snap.prereqLineArrow.get(pi) != null &&
                                    !snap.prereqLineArrow.get(pi).isEmpty()) {
                                node.setPrereqLineArrow(req.getId(), Boolean.valueOf(snap.prereqLineArrow.get(pi)));
                            }
                            if (pi < snap.prereqLineStyleId.size() && snap.prereqLineStyleId.get(pi) != null &&
                                    !snap.prereqLineStyleId.get(pi).isEmpty()) {
                                node.setPrereqLineStyleId(req.getId(), snap.prereqLineStyleId.get(pi));
                            }
                        }
                    }
                }

                if (!hasParent.contains(snap.id)) {
                    QuestTreeRegistry.registerRootChapter(node);
                }
            }

            System.out.println("[Phoenix Chronicles] Client synced " + snapshots.size() + " quest(s) from server.");
        }

        private static QuestTask deserializeTask(CompoundTag tag) {
            if (tag == null || !tag.contains("type") || !tag.contains("task_id")) return null;
            QuestTask task = PhoenixTaskRegistry.deserialize(tag);
            if (task == null) {
                System.err.println("[Phoenix Chronicles] Unknown task type in sync packet: '" +
                        tag.getString("type") + "' — skipping.");
            }
            return task;
        }
    }
}
