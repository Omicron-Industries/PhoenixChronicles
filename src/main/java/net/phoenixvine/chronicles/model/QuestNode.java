package net.phoenixvine.chronicles.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.registry.ChapterFlagRegistry;
import net.phoenixvine.chronicles.tracker.TutorialStep;

import lombok.Getter;

import java.util.*;

public class QuestNode {

    private final ResourceLocation id;
    private Component title;
    private Component description;

    private String chapter = "MAIN";
    private String shapeType = "SQUARE";

    private String shapeTexture = "";

    public enum NodeSize {
        TINY,
        SMALL,
        NORMAL,
        LARGE,
        HUGE
    }

    private NodeSize nodeSize = NodeSize.NORMAL;
    private Item iconItem = null;

    private String iconTexture = "";
    private int customX = 0;
    private int customY = 0;

    public String getIconTexture() {
        return iconTexture;
    }

    public void setIconTexture(String texture) {
        this.iconTexture = texture == null ? "" : texture.trim();
    }

    private String subtitle = "";

    public enum Visibility {
        NORMAL,
        HIDDEN,
        MYSTERY,
        DISABLED

    }

    private Visibility visibility = Visibility.NORMAL;

    @Getter
    private String enableIf = null;

    public boolean isFlagEnabled() {
        if (enableIf != null && !PhoenixQuestFlags.evaluate(enableIf, null, "quest " + id + " enableIf"))
            return false;
        return ChapterFlagRegistry.isChapterEnabled(chapter);
    }

    public void setEnableIf(String expr) {
        this.enableIf = (expr == null || expr.isBlank()) ? null : expr.trim();
    }

    public boolean isFlagDisabled() {
        return !isFlagEnabled();
    }

    private boolean hideDepLine = false;

    private boolean disabledBlocksChildren = false;

    public boolean isHideDepLine() {
        return hideDepLine;
    }

    public void setHideDepLine(boolean hide) {
        this.hideDepLine = hide;
    }

    private ResourceLocation linkTarget = null;

    public boolean isLinkStub() {
        return linkTarget != null;
    }

    public ResourceLocation getLinkTarget() {
        return linkTarget;
    }

    public void setLinkTarget(ResourceLocation target) {
        this.linkTarget = target;
    }

    public boolean isDisabledBlocksChildren() {
        return disabledBlocksChildren;
    }

    public void setDisabledBlocksChildren(boolean v) {
        this.disabledBlocksChildren = v;
    }

    private boolean shared = false;

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean s) {
        this.shared = s;
    }

    private boolean pooledProgress = false;

    public boolean isPooledProgress() {
        return pooledProgress;
    }

    public void setPooledProgress(boolean v) {
        this.pooledProgress = v;
    }

    private boolean autoClaimRewards = false;

    public boolean isAutoClaimRewards() {
        return autoClaimRewards;
    }

    public void setAutoClaimRewards(boolean v) {
        this.autoClaimRewards = v;
    }

    private boolean rewardChoice = false;
    private int rewardChoiceCount = 1;

    public boolean isRewardChoice() {
        return rewardChoice;
    }

    public int getRewardChoiceCount() {
        return rewardChoiceCount;
    }

    public void setRewardChoice(boolean v) {
        this.rewardChoice = v;
    }

    public void setRewardChoiceCount(int n) {
        this.rewardChoiceCount = Math.max(1, n);
    }

    private String devNotes = "";

    public String getDevNotes() {
        return devNotes;
    }

    public void setDevNotes(String s) {
        this.devNotes = s != null ? s : "";
    }

    private String previewMachineId = "";

    public String getPreviewMachineId() {
        return previewMachineId;
    }

    public void setPreviewMachineId(String s) {
        this.previewMachineId = s != null ? s : "";
    }

    private int taskMinCount = 0;

    public enum RepeatMode {
        NONE,
        DAILY,
        COOLDOWN,
        INFINITE
    }

    private RepeatMode repeatMode = RepeatMode.NONE;
    private int repeatCooldownHours = 24;

    private Boolean requireAllPrerequisites = null;

    private final Map<ResourceLocation, Boolean> prereqRequired = new HashMap<>();

    private final Set<ResourceLocation> prereqForbidden = new HashSet<>();

    private final Set<ResourceLocation> prereqLink = new HashSet<>();

    private final Set<ResourceLocation> prereqCosmetic = new HashSet<>();

    private final Map<ResourceLocation, net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineStyle> prereqLineShape = new HashMap<>();
    private final Map<ResourceLocation, net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineVisualStyle> prereqLineVisual = new HashMap<>();
    private final Map<ResourceLocation, net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineAnimSpeed> prereqLineSpeed = new HashMap<>();

    private final Map<ResourceLocation, Boolean> prereqLineArrow = new HashMap<>();

    private final Map<ResourceLocation, String> prereqLineStyleId = new HashMap<>();

    private Integer optionalPrereqMinCount = null;

    private final List<ItemStack> emergencyItems = new ArrayList<>();

    private final List<TutorialStep> tutorialSteps = new ArrayList<>();

    public List<TutorialStep> getTutorialSteps() {
        return Collections.unmodifiableList(tutorialSteps);
    }

    public void addTutorialStep(TutorialStep step) {
        if (step != null) tutorialSteps.add(step);
    }

    public void clearTutorialSteps() {
        tutorialSteps.clear();
    }

    private final List<QuestNode> children = new ArrayList<>();
    private final List<QuestNode> prerequisites = new ArrayList<>();
    private final List<QuestTask> tasks = new ArrayList<>();
    private final List<QuestReward> rewards = new ArrayList<>();

    public static final class QuestVariant {

        public String condition;
        public String title;
        public String description;
        public Visibility visibility;
        public List<QuestTask> tasks;
        public List<QuestReward> rewards;

        public QuestVariant(String condition) {
            this.condition = condition == null ? "" : condition;
        }
    }

    private final List<QuestVariant> variants = new ArrayList<>();

    public QuestNode(ResourceLocation id, Component title, Component description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public ResourceLocation getId() {
        return id;
    }

    public Component getTitle() {
        if (isLinkStub() && title.getString().isBlank()) {
            QuestNode target = net.phoenixvine.chronicles.registry.QuestTreeRegistry.getQuest(linkTarget);
            if (target != null) return target.getTitle();
        }
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            String override = net.phoenixvine.chronicles.client.ClientTextOverrides.get(langKey("title"));
            if (override != null) return Component.literal(override);
            Component t = ClientLangLookup.resolve(langKey("title"));
            if (t != null) return t;
        }
        return title;
    }

    public void setTitle(Component t) {
        this.title = t != null ? t : Component.empty();

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT)
            net.phoenixvine.chronicles.client.ClientTextOverrides.put(langKey("title"), this.title.getString());
    }

    public Component getTitleRaw() {
        return title;
    }

    public Component getDescription() {
        if (isLinkStub() && description.getString().isBlank()) {
            QuestNode target = net.phoenixvine.chronicles.registry.QuestTreeRegistry.getQuest(linkTarget);
            if (target != null) return target.getDescription();
        }
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            String override = net.phoenixvine.chronicles.client.ClientTextOverrides.get(langKey("description"));
            if (override != null) return Component.literal(override);
            Component d = ClientLangLookup.resolve(langKey("description"));
            if (d != null) return d;
        }
        return description;
    }

    public void setDescription(Component d) {
        this.description = d != null ? d : Component.empty();
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT)
            net.phoenixvine.chronicles.client.ClientTextOverrides.put(langKey("description"),
                    this.description.getString());
    }

    public Component getDescriptionRaw() {
        return description;
    }

    private String langKey(String field) {
        return "phoenix_chronicles.quest." + id.getPath().replace('/', '.') + "." + field;
    }

    public String getChapter() {
        return chapter;
    }

    public void setChapter(String c) {
        this.chapter = c;
    }

    public String getShapeType() {
        return shapeType;
    }

    public void setShapeType(String t) {
        this.shapeType = t;
    }

    public String getShapeTexture() {
        return shapeTexture;
    }

    public void setShapeTexture(String texture) {
        this.shapeTexture = texture == null ? "" : texture.trim();
    }

    private String backgroundType = "";

    public String getBackgroundType() {
        return backgroundType;
    }

    public void setBackgroundType(String type) {
        this.backgroundType = type == null ? "" : type.trim();
    }

    private String externalScreenId = "";

    public String getExternalScreenId() {
        return externalScreenId;
    }

    public void setExternalScreenId(String id) {
        this.externalScreenId = id == null ? "" : id.trim();
    }

    public NodeSize getNodeSize() {
        return nodeSize;
    }

    public void setNodeSize(NodeSize s) {
        this.nodeSize = s != null ? s : NodeSize.NORMAL;
        this.sizeOverridePx = 0;
    }

    private int sizeOverridePx = 0;

    public int getSizeOverridePx() {
        return sizeOverridePx;
    }

    public void setSizeOverridePx(int px) {
        this.sizeOverridePx = Math.max(8, Math.min(200, px));
    }

    public int getNodePixelSize() {
        if (sizeOverridePx > 0) return sizeOverridePx;
        return switch (nodeSize) {
            case TINY -> 14;
            case SMALL -> 18;
            case LARGE -> 48;
            case HUGE -> 64;
            default -> 32;
        };
    }

    public Item getIconItem() {
        return iconItem;
    }

    public void setIconItem(Item item) {
        this.iconItem = item;
    }

    public void setIconItemById(String id) {
        if (id == null || id.isBlank()) {
            this.iconItem = null;
            return;
        }
        try {
            Item found = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            this.iconItem = (found != null && found != Items.AIR) ? found : null;
        } catch (Exception ignored) {
            this.iconItem = null;
        }
    }

    public String getIconItemId() {
        if (iconItem == null) return "";
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(iconItem);
        return key != null ? key.toString() : "";
    }

    private String iconFluid = "";

    public String getIconFluid() {
        return iconFluid;
    }

    public void setIconFluid(String fluidId) {
        this.iconFluid = fluidId == null ? "" : fluidId.trim();
    }

    public int getCustomX() {
        return customX;
    }

    public void setCustomX(int x) {
        this.customX = x;
    }

    public int getCustomY() {
        return customY;
    }

    public void setCustomY(int y) {
        this.customY = y;
    }

    public void setCustomPosition(int x, int y) {
        this.customX = x;
        this.customY = y;
    }

    public String getSubtitle() {
        if (isLinkStub() && subtitle.isBlank()) {
            QuestNode target = net.phoenixvine.chronicles.registry.QuestTreeRegistry.getQuest(linkTarget);
            if (target != null) return target.getSubtitle();
        }
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            String override = net.phoenixvine.chronicles.client.ClientTextOverrides.get(langKey("subtitle"));
            if (override != null) return override;
            Component s = ClientLangLookup.resolve(langKey("subtitle"));
            if (s != null) return s.getString();
        }
        return subtitle;
    }

    public String getSubtitleRaw() {
        return subtitle;
    }

    public void setSubtitle(String s) {
        this.subtitle = s != null ? s : "";
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT)
            net.phoenixvine.chronicles.client.ClientTextOverrides.put(langKey("subtitle"), this.subtitle);
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility v) {
        this.visibility = v != null ? v : Visibility.NORMAL;
    }

    public int getTaskMinCount() {
        return taskMinCount;
    }

    public void setTaskMinCount(int n) {
        this.taskMinCount = Math.max(0, n);
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode m) {
        this.repeatMode = m;
    }

    public int getRepeatCooldownHours() {
        return repeatCooldownHours;
    }

    public void setRepeatCooldownHours(int h) {
        this.repeatCooldownHours = Math.max(1, h);
    }

    public boolean isRepeatable() {
        return repeatMode != RepeatMode.NONE;
    }

    public Boolean getRequireAllPrerequisites() {
        return requireAllPrerequisites;
    }

    public void setRequireAllPrerequisites(Boolean v) {
        this.requireAllPrerequisites = v;
    }

    public boolean getEffectiveRequireAllPrerequisites() {
        if (requireAllPrerequisites != null) return requireAllPrerequisites;
        Boolean catDefault = net.phoenixvine.chronicles.registry.ChapterPrereqDefaults.getRequireAll(chapter);
        return catDefault != null ? catDefault : true;
    }

    public boolean isPrereqRequired(ResourceLocation prereqId) {
        return prereqRequired.getOrDefault(prereqId, true);
    }

    public void setPrereqRequired(ResourceLocation prereqId, boolean required) {
        prereqRequired.put(prereqId, required);
    }

    public Map<ResourceLocation, Boolean> getPrereqRequired() {
        return Collections.unmodifiableMap(prereqRequired);
    }

    public boolean hasPerPrereqFlags() {
        return !prereqRequired.isEmpty() || !prereqForbidden.isEmpty();
    }

    public boolean isPrereqForbidden(ResourceLocation prereqId) {
        return prereqForbidden.contains(prereqId);
    }

    public void setPrereqForbidden(ResourceLocation prereqId, boolean forbidden) {
        if (forbidden) {
            prereqForbidden.add(prereqId);
            prereqRequired.remove(prereqId);
        } else {
            prereqForbidden.remove(prereqId);
        }
    }

    public Set<ResourceLocation> getPrereqForbidden() {
        return Collections.unmodifiableSet(prereqForbidden);
    }

    public boolean isPrereqLink(ResourceLocation prereqId) {
        return prereqLink.contains(prereqId);
    }

    public void setPrereqLink(ResourceLocation prereqId, boolean link) {
        if (link) prereqLink.add(prereqId);
        else prereqLink.remove(prereqId);
    }

    public Set<ResourceLocation> getPrereqLink() {
        return Collections.unmodifiableSet(prereqLink);
    }

    public boolean isPrereqCosmetic(ResourceLocation prereqId) {
        return prereqCosmetic.contains(prereqId);
    }

    public void setPrereqCosmetic(ResourceLocation prereqId, boolean cosmetic) {
        if (cosmetic) prereqCosmetic.add(prereqId);
        else prereqCosmetic.remove(prereqId);
    }

    public Set<ResourceLocation> getPrereqCosmetic() {
        return Collections.unmodifiableSet(prereqCosmetic);
    }

    public net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineStyle getPrereqLineShape(ResourceLocation prereqId) {
        return prereqLineShape.get(prereqId);
    }

    public void setPrereqLineShape(ResourceLocation prereqId,
                                   net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineStyle style) {
        if (style == null) prereqLineShape.remove(prereqId);
        else prereqLineShape.put(prereqId, style);
    }

    public net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineVisualStyle getPrereqLineVisual(ResourceLocation prereqId) {
        return prereqLineVisual.get(prereqId);
    }

    public void setPrereqLineVisual(ResourceLocation prereqId,
                                    net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineVisualStyle style) {
        if (style == null) prereqLineVisual.remove(prereqId);
        else prereqLineVisual.put(prereqId, style);
    }

    public net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineAnimSpeed getPrereqLineSpeed(ResourceLocation prereqId) {
        return prereqLineSpeed.get(prereqId);
    }

    public void setPrereqLineSpeed(ResourceLocation prereqId,
                                   net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineAnimSpeed speed) {
        if (speed == null) prereqLineSpeed.remove(prereqId);
        else prereqLineSpeed.put(prereqId, speed);
    }

    public Boolean getPrereqLineArrow(ResourceLocation prereqId) {
        return prereqLineArrow.get(prereqId);
    }

    public void setPrereqLineArrow(ResourceLocation prereqId, Boolean showArrow) {
        if (showArrow == null) prereqLineArrow.remove(prereqId);
        else prereqLineArrow.put(prereqId, showArrow);
    }

    public String getPrereqLineStyleId(ResourceLocation prereqId) {
        return prereqLineStyleId.get(prereqId);
    }

    public void setPrereqLineStyleId(ResourceLocation prereqId, String styleId) {
        if (styleId == null || styleId.isBlank()) prereqLineStyleId.remove(prereqId);
        else prereqLineStyleId.put(prereqId, styleId);
    }

    public Integer getOptionalPrereqMinCount() {
        return optionalPrereqMinCount;
    }

    public void setOptionalPrereqMinCount(Integer n) {
        this.optionalPrereqMinCount = n;
    }

    public int getEffectiveOptionalPrereqMinCount() {
        if (optionalPrereqMinCount != null) return optionalPrereqMinCount;
        Integer catDefault = net.phoenixvine.chronicles.registry.ChapterPrereqDefaults.getOptionalMinCount(chapter);
        return catDefault != null ? catDefault : 0;
    }

    public void addChild(QuestNode child) {
        if (child != null && !children.contains(child)) children.add(child);
    }

    public void removeChild(QuestNode child) {
        children.remove(child);
    }

    public List<QuestNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addPrerequisite(QuestNode p) {
        if (p != null && !prerequisites.contains(p)) prerequisites.add(p);
    }

    public void removePrerequisite(QuestNode p) {
        if (p != null) {
            prerequisites.remove(p);
            prereqRequired.remove(p.getId());
            prereqForbidden.remove(p.getId());
            prereqLink.remove(p.getId());
            prereqCosmetic.remove(p.getId());
            prereqLineShape.remove(p.getId());
            prereqLineVisual.remove(p.getId());
            prereqLineSpeed.remove(p.getId());
            prereqLineArrow.remove(p.getId());
            prereqLineStyleId.remove(p.getId());
        }
    }

    public List<QuestNode> getPrerequisites() {
        return Collections.unmodifiableList(prerequisites);
    }

    public void addTask(QuestTask task) {
        if (task != null) tasks.add(task);
    }

    public void clearTasks() {
        tasks.clear();
    }

    public List<QuestTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public void addReward(QuestReward r) {
        if (r != null) rewards.add(r);
    }

    public void clearRewards() {
        rewards.clear();
    }

    public List<QuestReward> getRewards() {
        return Collections.unmodifiableList(rewards);
    }

    public void addVariant(QuestVariant v) {
        if (v != null) variants.add(v);
    }

    public void clearVariants() {
        variants.clear();
    }

    public List<QuestVariant> getVariants() {
        return Collections.unmodifiableList(variants);
    }

    public QuestVariant resolveVariant(net.minecraft.server.MinecraftServer server) {
        for (QuestVariant v : variants) {
            if (PhoenixQuestFlags.evaluate(v.condition, server, "quest " + id + " variant condition")) return v;
        }
        return null;
    }

    public Component getEffectiveTitleRaw(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.title != null && !v.title.isBlank()) ? Component.literal(v.title) : getTitleRaw();
    }

    public Component getEffectiveDescriptionRaw(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.description != null && !v.description.isBlank()) ? Component.literal(v.description) :
                getDescriptionRaw();
    }

    public Visibility getEffectiveVisibility(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.visibility != null) ? v.visibility : visibility;
    }

    public List<QuestTask> getEffectiveTasks(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.tasks != null) ? Collections.unmodifiableList(v.tasks) : getTasks();
    }

    public List<QuestReward> getEffectiveRewards(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.rewards != null) ? Collections.unmodifiableList(v.rewards) : getRewards();
    }

    public List<ItemStack> getEmergencyItems() {
        return Collections.unmodifiableList(emergencyItems);
    }

    public void addEmergencyItem(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) emergencyItems.add(stack.copy());
    }

    public void clearEmergencyItems() {
        emergencyItems.clear();
    }

    public ListTag serializeEmergencyItems() {
        ListTag list = new ListTag();
        for (ItemStack stack : emergencyItems) list.add(stack.save(new CompoundTag()));
        return list;
    }

    public void deserializeEmergencyItems(ListTag list) {
        emergencyItems.clear();
        for (Tag t : list) {
            if (t instanceof CompoundTag ct) {
                ItemStack stack = ItemStack.of(ct);
                if (!stack.isEmpty()) emergencyItems.add(stack);
            }
        }
    }

    private static class ClientLangLookup {

        static Component resolve(String key) {
            return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : null;
        }
    }
}
