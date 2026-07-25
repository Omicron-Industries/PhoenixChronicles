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
            snapshotMap.put(entry.getKey(), new QuestSnapshot(entry.getValue(), server));
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
            Boolean requireAllPrerequisites = buf.readBoolean() ? buf.readBoolean() : null;

            int childCount = buf.readInt();
            List<ResourceLocation> childIds = new ArrayList<>(childCount);
            for (int c = 0; c < childCount; c++) childIds.add(buf.readResourceLocation());

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
            for (int p = 0; p < prereqCount; p++) {
                prereqIds.add(buf.readResourceLocation());
                prereqRequired.add(buf.readBoolean());
                prereqForbidden.add(buf.readBoolean());
                prereqLink.add(buf.readBoolean());
                prereqCosmetic.add(buf.readBoolean());
                prereqLineShape.add(buf.readUtf());
                prereqLineVisual.add(buf.readUtf());
                prereqLineSpeed.add(buf.readUtf());
                prereqLineArrow.add(buf.readUtf());
            }
            Integer optionalPrereqMinCount = buf.readBoolean() ? buf.readInt() : null;

            int taskCount = buf.readInt();
            List<CompoundTag> tasksNbt = new ArrayList<>(taskCount);
            for (int t = 0; t < taskCount; t++) tasksNbt.add(buf.readNbt());

            ResourceLocation linkTarget = buf.readBoolean() ? buf.readResourceLocation() : null;
            String iconTexture = buf.readUtf();
            String shapeTexture = buf.readUtf();
            String nodeSize = buf.readUtf();
            int sizeOverridePx = buf.readInt();
            String iconFluid = buf.readUtf();

            snapshotMap.put(id, new QuestSnapshot(
                    id, title, description, chapter, shapeType, iconItemId,
                    customX, customY, subtitle, visibility, enableIf, taskMinCount, requireAllPrerequisites,
                    childIds, prereqIds, prereqRequired, prereqForbidden, prereqLink, prereqCosmetic,
                    prereqLineShape, prereqLineVisual, prereqLineSpeed, prereqLineArrow,
                    optionalPrereqMinCount, tasksNbt, linkTarget, iconTexture, shapeTexture,
                    nodeSize, sizeOverridePx, iconFluid));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(snapshotMap.size());
        for (QuestSnapshot snap : snapshotMap.values()) {
            buf.writeResourceLocation(snap.id);
            buf.writeComponent(snap.title);
            buf.writeComponent(snap.description);
            buf.writeUtf(snap.chapter);
            buf.writeUtf(snap.shapeType);
            buf.writeUtf(snap.iconItemId);
            buf.writeInt(snap.customX);
            buf.writeInt(snap.customY);
            buf.writeUtf(snap.subtitle);
            buf.writeUtf(snap.visibility);
            buf.writeUtf(snap.enableIf);
            buf.writeInt(snap.taskMinCount);
            buf.writeBoolean(snap.requireAllPrerequisites != null);
            if (snap.requireAllPrerequisites != null) buf.writeBoolean(snap.requireAllPrerequisites);

            buf.writeInt(snap.childIds.size());
            for (ResourceLocation cId : snap.childIds) buf.writeResourceLocation(cId);

            buf.writeInt(snap.prereqIds.size());
            for (int pi = 0; pi < snap.prereqIds.size(); pi++) {
                buf.writeResourceLocation(snap.prereqIds.get(pi));
                buf.writeBoolean(pi < snap.prereqRequired.size() && snap.prereqRequired.get(pi));
                buf.writeBoolean(pi < snap.prereqForbidden.size() && snap.prereqForbidden.get(pi));
                buf.writeBoolean(pi < snap.prereqLink.size() && snap.prereqLink.get(pi));
                buf.writeBoolean(pi < snap.prereqCosmetic.size() && snap.prereqCosmetic.get(pi));
                buf.writeUtf(pi < snap.prereqLineShape.size() ? snap.prereqLineShape.get(pi) : "");
                buf.writeUtf(pi < snap.prereqLineVisual.size() ? snap.prereqLineVisual.get(pi) : "");
                buf.writeUtf(pi < snap.prereqLineSpeed.size() ? snap.prereqLineSpeed.get(pi) : "");
                buf.writeUtf(pi < snap.prereqLineArrow.size() ? snap.prereqLineArrow.get(pi) : "");
            }
            buf.writeBoolean(snap.optionalPrereqMinCount != null);
            if (snap.optionalPrereqMinCount != null) buf.writeInt(snap.optionalPrereqMinCount);

            buf.writeInt(snap.tasksNbt.size());
            for (CompoundTag tag : snap.tasksNbt) buf.writeNbt(tag);

            buf.writeBoolean(snap.linkTarget != null);
            if (snap.linkTarget != null) buf.writeResourceLocation(snap.linkTarget);
            buf.writeUtf(snap.iconTexture);
            buf.writeUtf(snap.shapeTexture);
            buf.writeUtf(snap.nodeSize);
            buf.writeInt(snap.sizeOverridePx);
            buf.writeUtf(snap.iconFluid);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPayloadProcessor.processQuestTree(snapshotMap)));
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

        final Integer optionalPrereqMinCount;
        final List<CompoundTag> tasksNbt;

        final ResourceLocation linkTarget;

        final String iconTexture;

        final String shapeTexture;

        final String nodeSize;

        final int sizeOverridePx;

        final String iconFluid;

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
            this.visibility = node.getEffectiveVisibility(server).name();
            this.enableIf = node.getEnableIf() != null ? node.getEnableIf() : "";
            this.taskMinCount = node.getTaskMinCount();
            this.requireAllPrerequisites = node.getRequireAllPrerequisites();
            this.optionalPrereqMinCount = node.getOptionalPrereqMinCount();
            this.linkTarget = node.getLinkTarget();
            this.iconTexture = node.getIconTexture() != null ? node.getIconTexture() : "";
            this.shapeTexture = node.getShapeTexture() != null ? node.getShapeTexture() : "";
            this.nodeSize = node.getNodeSize().name();
            this.sizeOverridePx = node.getSizeOverridePx();
            this.iconFluid = node.getIconFluid() != null ? node.getIconFluid() : "";

            this.childIds = new ArrayList<>();
            for (QuestNode child : node.getChildren()) childIds.add(child.getId());

            this.prereqIds = new ArrayList<>();
            this.prereqRequired = new ArrayList<>();
            this.prereqForbidden = new ArrayList<>();
            this.prereqLink = new ArrayList<>();
            this.prereqCosmetic = new ArrayList<>();
            this.prereqLineShape = new ArrayList<>();
            this.prereqLineVisual = new ArrayList<>();
            this.prereqLineSpeed = new ArrayList<>();
            this.prereqLineArrow = new ArrayList<>();
            for (QuestNode req : node.getPrerequisites()) {
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
            }

            this.tasksNbt = new ArrayList<>();
            for (QuestTask task : node.getEffectiveTasks(server)) {
                CompoundTag tag = task.serializeNBT();
                if (!tag.contains("task_id"))
                    tag.putString("task_id", task.getTaskId().toString());
                if (!tag.contains("description"))
                    tag.putString("description", Component.Serializer.toJson(task.getDescription()));
                tag.putBoolean("optional", task.isOptional());
                tasksNbt.add(tag);
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
                      List<String> prereqLineArrow,
                      Integer optionalPrereqMinCount,
                      List<CompoundTag> tasksNbt,
                      ResourceLocation linkTarget, String iconTexture, String shapeTexture,
                      String nodeSize, int sizeOverridePx, String iconFluid) {
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
            this.optionalPrereqMinCount = optionalPrereqMinCount;
            this.tasksNbt = tasksNbt;
            this.linkTarget = linkTarget;
            this.iconTexture = iconTexture;
            this.shapeTexture = shapeTexture;
            this.nodeSize = nodeSize;
            this.sizeOverridePx = sizeOverridePx;
            this.iconFluid = iconFluid;
        }
    }

    private static class ClientPayloadProcessor {

        static void processQuestTree(Map<ResourceLocation, QuestSnapshot> snapshots) {
            QuestTreeRegistry.clear();

            for (QuestSnapshot snap : snapshots.values()) {
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
                    node.setVisibility(QuestNode.Visibility.valueOf(snap.visibility));
                } catch (Exception ignored) {}
                node.setEnableIf(snap.enableIf.isEmpty() ? null : snap.enableIf);
                node.setLinkTarget(snap.linkTarget);
                node.setIconTexture(snap.iconTexture);
                node.setIconFluid(snap.iconFluid);
                node.setShapeTexture(snap.shapeTexture);
                try {
                    node.setNodeSize(QuestNode.NodeSize.valueOf(snap.nodeSize));
                } catch (Exception ignored) {}
                if (snap.sizeOverridePx > 0) node.setSizeOverridePx(snap.sizeOverridePx);

                if (!snap.iconItemId.isEmpty()) {
                    node.setIconItemById(snap.iconItemId);
                }

                for (CompoundTag tag : snap.tasksNbt) {
                    QuestTask task = deserializeTask(tag);
                    if (task != null) {
                        if (tag.contains("optional")) task.setOptional(tag.getBoolean("optional"));
                        node.addTask(task);
                    }
                }

                QuestTreeRegistry.registerBareQuestNode(node);
            }

            Set<ResourceLocation> hasParent = new HashSet<>();
            for (QuestSnapshot snap : snapshots.values())
                hasParent.addAll(snap.childIds);

            for (QuestSnapshot snap : snapshots.values()) {
                QuestNode node = QuestTreeRegistry.getQuest(snap.id);
                if (node == null) continue;

                for (ResourceLocation childId : snap.childIds) {
                    QuestNode child = QuestTreeRegistry.getQuest(childId);
                    if (child != null) node.addChild(child);
                }

                for (int pi = 0; pi < snap.prereqIds.size(); pi++) {
                    QuestNode req = QuestTreeRegistry.getQuest(snap.prereqIds.get(pi));
                    if (req != null) {
                        node.addPrerequisite(req);
                        boolean forbidden = pi < snap.prereqForbidden.size() && snap.prereqForbidden.get(pi);
                        if (forbidden) {
                            node.setPrereqForbidden(req.getId(), true);
                        } else {
                            boolean required = pi < snap.prereqRequired.size() && snap.prereqRequired.get(pi);
                            node.setPrereqRequired(req.getId(), required);
                        }
                        if (pi < snap.prereqLink.size() && snap.prereqLink.get(pi)) {
                            node.setPrereqLink(req.getId(), true);
                        }
                        if (pi < snap.prereqCosmetic.size() && snap.prereqCosmetic.get(pi)) {
                            node.setPrereqCosmetic(req.getId(), true);
                        }
                        if (pi < snap.prereqLineShape.size() && !snap.prereqLineShape.get(pi).isEmpty()) {
                            try {
                                node.setPrereqLineShape(req.getId(),
                                        net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineStyle
                                                .valueOf(snap.prereqLineShape.get(pi)));
                            } catch (IllegalArgumentException ignored) {}
                        }
                        if (pi < snap.prereqLineVisual.size() && !snap.prereqLineVisual.get(pi).isEmpty()) {
                            try {
                                node.setPrereqLineVisual(req.getId(),
                                        net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineVisualStyle
                                                .valueOf(snap.prereqLineVisual.get(pi)));
                            } catch (IllegalArgumentException ignored) {}
                        }
                        if (pi < snap.prereqLineSpeed.size() && !snap.prereqLineSpeed.get(pi).isEmpty()) {
                            try {
                                node.setPrereqLineSpeed(req.getId(),
                                        net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineAnimSpeed
                                                .valueOf(snap.prereqLineSpeed.get(pi)));
                            } catch (IllegalArgumentException ignored) {}
                        }
                        if (pi < snap.prereqLineArrow.size() && !snap.prereqLineArrow.get(pi).isEmpty()) {
                            node.setPrereqLineArrow(req.getId(), Boolean.valueOf(snap.prereqLineArrow.get(pi)));
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
            if (!tag.contains("type") || !tag.contains("task_id")) return null;
            QuestTask task = PhoenixTaskRegistry.deserialize(tag);
            if (task == null) {
                System.err.println("[Phoenix Chronicles] Unknown task type in sync packet: '" +
                        tag.getString("type") + "' — skipping.");
            }
            return task;
        }
    }
}
