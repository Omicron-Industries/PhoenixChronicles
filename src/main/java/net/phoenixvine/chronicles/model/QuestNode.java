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
import net.phoenixvine.chronicles.registry.CategoryFlagRegistry;
import net.phoenixvine.chronicles.tracker.TutorialStep;

import lombok.Getter;

import java.util.*;

/**
 * Runtime representation of a single quest blueprint.
 * Stateless — all per-player progress lives in PlayerQuestData.
 */
public class QuestNode {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final ResourceLocation id;
    private Component title;
    private Component description;

    // ── Appearance ────────────────────────────────────────────────────────────
    private String category = "MAIN";
    private String shapeType = "SQUARE";
    /**
     * An arbitrary registered texture (same TextureBrowserScreen mechanism as
     * {@link #iconTexture}) used as this node's outline shape when {@link #shapeType} is
     * {@code "CUSTOM"}. Ignored for every other shape type.
     */
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
    /**
     * An arbitrary registered texture (e.g. "modid:textures/gui/my_icon.png") to use as this
     * quest's icon instead of an item render - picked via the same TextureBrowserScreen category
     * backgrounds use. Takes priority over {@link #iconItem} when set, but a custom per-quest PNG
     * from QuestIconCache (config/phoenix_chronicles/icons/<id>.png) still wins over both.
     */
    private String iconTexture = "";
    private int customX = 0;
    private int customY = 0;

    public String getIconTexture() {
        return iconTexture;
    }

    public void setIconTexture(String texture) {
        this.iconTexture = texture == null ? "" : texture.trim();
    }

    // ── Extended metadata ─────────────────────────────────────────────────────
    private String subtitle = "";

    public enum Visibility {
        NORMAL,   // always shown; locked appearance if prereqs not met
        HIDDEN,   // invisible until prerequisites are complete
        MYSTERY,  // shown as ??? until prerequisites are complete
        DISABLED  // shown but grayed-out; excluded from progress counts;
                  // if also treated as hidden, its dep lines have no effect
    }

    private Visibility visibility = Visibility.NORMAL;

    /**
     * Comma-separated flag expression evaluated via {@link PhoenixQuestFlags}.
     * When the expression evaluates to {@code false}, this quest is treated as
     * HIDDEN + DISABLED regardless of its {@link #visibility} field.
     * Null / blank means always enabled.
     */
    @Getter
    private String enableIf = null;

    /** Returns true if this quest's flag condition is currently satisfied. */
    public boolean isFlagEnabled() {
        return PhoenixQuestFlags.evaluate(enableIf) && CategoryFlagRegistry.isCategoryEnabled(category);
    }

    public void setEnableIf(String expr) {
        this.enableIf = (expr == null || expr.isBlank()) ? null : expr.trim();
    }

    /**
     * True when the quest is completely inert: flag condition failed, meaning it
     * should be treated as if it doesn't exist (hidden, non-completable, no gating).
     * Distinct from {@link Visibility#DISABLED}, which still shows the quest.
     */
    public boolean isFlagDisabled() {
        return !isFlagEnabled();
    }

    /**
     * When true, all dependency lines touching this quest are hidden on the canvas.
     * Persisted as {@code hide_dep_line: 1b} in the quest SNBT.
     */
    private boolean hideDepLine = false;

    /**
     * Only meaningful when {@link #visibility} is {@link Visibility#DISABLED}.
     * When false (default): DISABLED quests are excluded from the prerequisite gate —
     * children unlock as if this quest were not a prerequisite at all.
     * When true: children remain locked until this quest is somehow completed
     * (useful for branching-narrative placeholders that still block progress).
     */
    private boolean disabledBlocksChildren = false;

    public boolean isHideDepLine() {
        return hideDepLine;
    }

    public void setHideDepLine(boolean hide) {
        this.hideDepLine = hide;
    }

    /**
     * When set, this node is a lightweight visual placeholder for the quest identified by
     * {@link #linkTarget} - equivalent to FTB Quests' "quest link" feature, which lets one
     * quest appear (at a different position/category) elsewhere on the map without duplicating
     * its actual definition. A link stub carries no tasks/rewards of its own; clicking it should
     * navigate to and open the real target quest instead of treating it as an independent quest.
     */
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

    /**
     * When true, completing this quest on any player cascades the completion to all
     * online members of the same team (Phoenix Guilds, then FTB Teams party/server
     * teams, then vanilla scoreboard teams — see {@code TeamKeyResolver}). Independent
     * of {@link #pooledProgress}: with pooledProgress off, task progress stays
     * per-player and only the final COMPLETED state is copied to teammates.
     */
    private boolean shared = false;

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean s) {
        this.shared = s;
    }

    /**
     * When true, every task's progress counter on this quest is pooled across the
     * player's team (same resolution order as {@link #shared}) instead of tracked
     * per-player — e.g. a "kill 100 zombies" task counts kills from any teammate
     * toward one shared total. Each teammate's own quest state still flips to
     * COMPLETED independently the next time their tasks are checked, since they're
     * all reading the same pooled counters. Falls back to normal per-player progress
     * for solo players (no resolvable team).
     */
    private boolean pooledProgress = false;

    public boolean isPooledProgress() {
        return pooledProgress;
    }

    public void setPooledProgress(boolean v) {
        this.pooledProgress = v;
    }

    /**
     * When true, rewards are automatically granted the moment the quest completes —
     * no need for the player to open the quest book and click "Claim".
     */
    private boolean autoClaimRewards = false;

    public boolean isAutoClaimRewards() {
        return autoClaimRewards;
    }

    public void setAutoClaimRewards(boolean v) {
        this.autoClaimRewards = v;
    }

    /**
     * When true, the player must choose exactly {@link #rewardChoiceCount} reward(s)
     * from the list instead of receiving all of them. Sends choiceIndex to server.
     */
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

    /** Internal dev-only notes. Never shown to players; only visible in dev mode quest editor. */
    private String devNotes = "";

    public String getDevNotes() {
        return devNotes;
    }

    public void setDevNotes(String s) {
        this.devNotes = s != null ? s : "";
    }

    /**
     * Optional Phantasia machine id shown as a live embedded build preview inside the quest
     * viewer's own content area — independent of any {@code view_machine} task requirement, so a
     * quest can show a build reference without forcing the player to view it to complete.
     */
    private String previewMachineId = "";

    public String getPreviewMachineId() {
        return previewMachineId;
    }

    public void setPreviewMachineId(String s) {
        this.previewMachineId = s != null ? s : "";
    }

    /** 0 = all non-optional tasks required; >0 = need exactly this many tasks. */
    private int taskMinCount = 0;

    // ── Repeat behaviour ──────────────────────────────────────────────────────
    /** How this quest can be repeated after first completion. */
    public enum RepeatMode {
        NONE,
        DAILY,
        COOLDOWN,
        INFINITE
    }

    private RepeatMode repeatMode = RepeatMode.NONE;
    private int repeatCooldownHours = 24; // used when mode == COOLDOWN

    // ── Prerequisite gate ─────────────────────────────────────────────────────
    /**
     * Legacy simple gate (still used when no per-prereq flags are set):
     * true = ALL prereqs must be complete (AND); false = ANY one suffices (OR).
     *
     * When per-prereq flags are set this field is ignored — use
     * {@link #isPrereqRequired(ResourceLocation)} + {@link #optionalPrereqMinCount}.
     *
     * null = inherit from {@link net.phoenixvine.chronicles.registry.CategoryPrereqDefaults}
     * for this quest's category (falling back to true if the category has no default either).
     * Use {@link #getEffectiveRequireAllPrerequisites()} for gating decisions; this raw field
     * is only for the packdev-facing override state.
     */
    private Boolean requireAllPrerequisites = null;

    /**
     * Per-prerequisite required flag.
     * Absent entry → defaults to true (required).
     * false → prereq is optional and contributes to the optional pool.
     */
    private final Map<ResourceLocation, Boolean> prereqRequired = new HashMap<>();

    /**
     * Forbidden prerequisites — these must NOT be completed for this quest to unlock.
     * Useful for mutually exclusive quest paths: "unlock if player did A but NOT B".
     *
     * SNBT: {id: "other_quest", forbidden: true}
     * Expression shorthand (display): shown as "!other_quest" in the dep list.
     */
    private final Set<ResourceLocation> prereqForbidden = new HashSet<>();

    /**
     * Link prerequisites — created via Alt+drag in the editor.
     * A purely visual style: rendered and saved distinctly so pack devs can tell
     * "storyline" edges from "also requires" edges at a glance. Whether the edge
     * also gates completion is controlled independently by {@link #prereqCosmetic} —
     * a link can be a real (gating) prereq or a decoration-only one, same as a
     * normal edge can.
     *
     * SNBT: {id: "other_quest", link: true}
     */
    private final Set<ResourceLocation> prereqLink = new HashSet<>();

    /**
     * Cosmetic-only prerequisites — the edge is drawn (honoring {@link #prereqLink}'s
     * style) but never gates unlock, like FTB Quests' purely decorative quest-link
     * lines. Independent of the link/normal style toggle: any prereq, link-styled or
     * not, can be marked cosmetic. Excluded entirely from
     * {@code QuestProgressTracker.prereqsSatisfied}'s gating logic.
     *
     * SNBT: {id: "other_quest", cosmetic: true}
     */
    private final Set<ResourceLocation> prereqCosmetic = new HashSet<>();

    /**
     * Per-edge line-visual overrides — shape (spline/straight), style (thin/normal/bold/
     * thick/wide/glow) and dot-animation speed for this specific dependency connection.
     * Absent entry → inherit the global setting from {@code QuestChroniclesSettings}.
     *
     * SNBT: {id: "other_quest", line_shape: "STRAIGHT", line_style: "GLOW", line_speed: "FAST"}
     */
    private final Map<ResourceLocation, net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineStyle> prereqLineShape = new HashMap<>();
    private final Map<ResourceLocation, net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineVisualStyle> prereqLineVisual = new HashMap<>();
    private final Map<ResourceLocation, net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineAnimSpeed> prereqLineSpeed = new HashMap<>();
    /** Per-edge arrowhead override. Absent → inherit QuestChroniclesSettings#isShowLineArrows(). */
    private final Map<ResourceLocation, Boolean> prereqLineArrow = new HashMap<>();

    /**
     * How many optional (non-required) prerequisites must be completed.
     * 0 = all optional prereqs must be done (same as required).
     * -1 = none of the optional prereqs need to be done.
     * N > 0 = exactly N (or more) from the optional pool.
     * Ignored when there are no optional prereqs.
     *
     * null = inherit from {@link net.phoenixvine.chronicles.registry.CategoryPrereqDefaults}
     * for this quest's category (falling back to 0 if the category has no default either).
     * Use {@link #getEffectiveOptionalPrereqMinCount()} for gating decisions.
     */
    private Integer optionalPrereqMinCount = null;

    // ── Emergency items ───────────────────────────────────────────────────────
    /**
     * Items given to the player via /chronicle emergency <questId> when they
     * lose quest-required items mid-progress. Only granted while the quest is ACTIVE.
     */
    private final List<ItemStack> emergencyItems = new ArrayList<>();

    // ── Tutorial steps ────────────────────────────────────────────────────────
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

    // ── Relations ─────────────────────────────────────────────────────────────
    private final List<QuestNode> children = new ArrayList<>();
    private final List<QuestNode> prerequisites = new ArrayList<>();
    private final List<QuestTask> tasks = new ArrayList<>();
    private final List<QuestReward> rewards = new ArrayList<>();

    // ── Pack-mode variants ────────────────────────────────────────────────────
    /**
     * One conditional override block: when {@link #condition} evaluates true (same flag-
     * expression syntax as {@link #enableIf}, via {@link PhoenixQuestFlags#evaluate}), any
     * non-null field here overrides the quest's own base value. {@code tasks}/{@code rewards}
     * REPLACE the base list entirely rather than merging - a variant is meant to describe "this
     * quest looks like THIS in this pack mode", not a patch. {@code rewards} can itself contain a
     * {@code {type: "reward_table", table_id: "..."}} entry, reusing the existing weighted-
     * reward-table feature instead of needing a separate table-reference field here.
     */
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

    // ── Constructor ───────────────────────────────────────────────────────────
    public QuestNode(ResourceLocation id, Component title, Component description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    // ── Identity accessors ────────────────────────────────────────────────────
    public ResourceLocation getId() {
        return id;
    }

    /**
     * Resolves as a REAL translation key ("phoenix_chronicles.quest.<path>.title") when one is
     * registered, so each player's own client resolves it via their own selected game language
     * (see net.phoenixvine.chronicles.client.ChroniclesLangPack) - the same mechanism vanilla
     * item/block names use. Falls back to the baked default when no entry exists yet (e.g. a
     * brand-new quest that hasn't been through a save/lang-write cycle).
     */
    public Component getTitle() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            Component t = ClientLangLookup.resolve(langKey("title"));
            if (t != null) return t;
        }
        return title;
    }

    public void setTitle(Component t) {
        this.title = t != null ? t : Component.empty();
    }

    /**
     * The baked default text, ignoring any translation. Use this when re-persisting structural
     * data to SNBT so an active translation is never silently baked over the original-language
     * default just because the quest was re-saved for an unrelated edit.
     */
    public Component getTitleRaw() {
        return title;
    }

    public Component getDescription() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            Component d = ClientLangLookup.resolve(langKey("description"));
            if (d != null) return d;
        }
        return description;
    }

    public void setDescription(Component d) {
        this.description = d != null ? d : Component.empty();
    }

    /** See {@link #getTitleRaw()}. */
    public Component getDescriptionRaw() {
        return description;
    }

    private String langKey(String field) {
        return "phoenix_chronicles.quest." + id.getPath().replace('/', '.') + "." + field;
    }

    // ── Appearance accessors ──────────────────────────────────────────────────
    public String getCategory() {
        return category;
    }

    public void setCategory(String c) {
        this.category = c;
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

    public NodeSize getNodeSize() {
        return nodeSize;
    }

    /** Picking a preset clears any explicit {@link #sizeOverridePx} - a preset should look exactly like the preset. */
    public void setNodeSize(NodeSize s) {
        this.nodeSize = s != null ? s : NodeSize.NORMAL;
        this.sizeOverridePx = 0;
    }

    /**
     * Explicit pixel-size override, set by the canvas's freeform "Resize (scroll + drag)…" mode
     * for pack devs who want finer control than the 5 fixed presets - 0 means "no override, use
     * the {@link #nodeSize} preset instead" (the common case, and what every quest saved before
     * this field existed has).
     */
    private int sizeOverridePx = 0;

    public int getSizeOverridePx() {
        return sizeOverridePx;
    }

    /** Clamped to a sane range - use {@link #setNodeSize} instead to clear back to a preset. */
    public void setSizeOverridePx(int px) {
        this.sizeOverridePx = Math.max(8, Math.min(200, px));
    }

    /** Returns the logical pixel size of this node (before zoom). NORMAL=32 matches the original constant. */
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

    /**
     * Registry id of a fluid to use as this quest's icon (rendered as a flat tinted square, same
     * convention as QuestToastConfig's fluid icons) - "" = none. Sits in the SAME priority chain
     * as {@link #iconItem}/{@link #iconTexture}: a custom per-quest PNG wins over everything, then
     * iconTexture, then this, then iconItem, then a state glyph fallback. Setting any ONE of
     * iconItem/iconTexture/iconFluid via the "Set Icon…" picker clears the other two - they're
     * mutually exclusive choices of what to render, not independently stackable.
     */
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
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            Component s = ClientLangLookup.resolve(langKey("subtitle"));
            if (s != null) return s.getString();
        }
        return subtitle;
    }

    /** See {@link #getTitleRaw()}. */
    public String getSubtitleRaw() {
        return subtitle;
    }

    public void setSubtitle(String s) {
        this.subtitle = s != null ? s : "";
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

    // ── Repeat accessors ──────────────────────────────────────────────────────
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

    // ── Prerequisite gate accessors ───────────────────────────────────────────
    /** Raw override value; null means "inherit from category default". */
    public Boolean getRequireAllPrerequisites() {
        return requireAllPrerequisites;
    }

    public void setRequireAllPrerequisites(Boolean v) {
        this.requireAllPrerequisites = v;
    }

    /** Resolved gate value: explicit override, else category default, else true. */
    public boolean getEffectiveRequireAllPrerequisites() {
        if (requireAllPrerequisites != null) return requireAllPrerequisites;
        Boolean catDefault = net.phoenixvine.chronicles.registry.CategoryPrereqDefaults.getRequireAll(category);
        return catDefault != null ? catDefault : true;
    }

    /** Returns true if this prereq is required (default), false if it's optional. */
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

    // ── Forbidden prereqs ─────────────────────────────────────────────────────

    /** True if this prereq must NOT be completed for this quest to unlock. */
    public boolean isPrereqForbidden(ResourceLocation prereqId) {
        return prereqForbidden.contains(prereqId);
    }

    public void setPrereqForbidden(ResourceLocation prereqId, boolean forbidden) {
        if (forbidden) {
            prereqForbidden.add(prereqId);
            prereqRequired.remove(prereqId); // forbidden is mutually exclusive with required/optional
        } else {
            prereqForbidden.remove(prereqId);
        }
    }

    public Set<ResourceLocation> getPrereqForbidden() {
        return Collections.unmodifiableSet(prereqForbidden);
    }

    // ── Link prereqs ──────────────────────────────────────────────────────────

    /** True if this connection was created via Alt+drag (a "link" rather than a parent-child edge). */
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

    /** True if this prerequisite is decoration-only — drawn but never gates unlock. */
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

    // ── Per-edge line-visual overrides ────────────────────────────────────────

    /** Null = inherit the global line shape for this specific edge. */
    public net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineStyle getPrereqLineShape(ResourceLocation prereqId) {
        return prereqLineShape.get(prereqId);
    }

    public void setPrereqLineShape(ResourceLocation prereqId,
                                   net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineStyle style) {
        if (style == null) prereqLineShape.remove(prereqId);
        else prereqLineShape.put(prereqId, style);
    }

    /** Null = inherit the global line style for this specific edge. */
    public net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineVisualStyle getPrereqLineVisual(ResourceLocation prereqId) {
        return prereqLineVisual.get(prereqId);
    }

    public void setPrereqLineVisual(ResourceLocation prereqId,
                                    net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineVisualStyle style) {
        if (style == null) prereqLineVisual.remove(prereqId);
        else prereqLineVisual.put(prereqId, style);
    }

    /** Null = inherit the global dot-animation speed for this specific edge. */
    public net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineAnimSpeed getPrereqLineSpeed(ResourceLocation prereqId) {
        return prereqLineSpeed.get(prereqId);
    }

    public void setPrereqLineSpeed(ResourceLocation prereqId,
                                   net.phoenixvine.chronicles.codec.QuestChroniclesSettings.LineAnimSpeed speed) {
        if (speed == null) prereqLineSpeed.remove(prereqId);
        else prereqLineSpeed.put(prereqId, speed);
    }

    /** Null = inherit the global arrow setting for this specific edge. */
    public Boolean getPrereqLineArrow(ResourceLocation prereqId) {
        return prereqLineArrow.get(prereqId);
    }

    public void setPrereqLineArrow(ResourceLocation prereqId, Boolean showArrow) {
        if (showArrow == null) prereqLineArrow.remove(prereqId);
        else prereqLineArrow.put(prereqId, showArrow);
    }

    /** Raw override value; null means "inherit from category default". */
    public Integer getOptionalPrereqMinCount() {
        return optionalPrereqMinCount;
    }

    public void setOptionalPrereqMinCount(Integer n) {
        this.optionalPrereqMinCount = n;
    }

    /** Resolved gate value: explicit override, else category default, else 0. */
    public int getEffectiveOptionalPrereqMinCount() {
        if (optionalPrereqMinCount != null) return optionalPrereqMinCount;
        Integer catDefault = net.phoenixvine.chronicles.registry.CategoryPrereqDefaults.getOptionalMinCount(category);
        return catDefault != null ? catDefault : 0;
    }

    // ── Relations ─────────────────────────────────────────────────────────────
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
        }
    }

    public List<QuestNode> getPrerequisites() {
        return Collections.unmodifiableList(prerequisites);
    }

    public void addTask(QuestTask task) { // Changed Object -> QuestTask
        if (task != null) tasks.add(task);
    }

    public void clearTasks() {
        tasks.clear();
    }

    public List<QuestTask> getTasks() { // Changed List<Object> -> List<QuestTask>
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

    // ── Pack-mode variants ────────────────────────────────────────────────────

    public void addVariant(QuestVariant v) {
        if (v != null) variants.add(v);
    }

    public void clearVariants() {
        variants.clear();
    }

    public List<QuestVariant> getVariants() {
        return Collections.unmodifiableList(variants);
    }

    /**
     * Returns the first variant whose condition currently evaluates true, or {@code null} if
     * none match (including when this quest has no variants at all). Same evaluation call
     * {@link #isFlagEnabled()} already uses for {@link #enableIf} - no new flag machinery.
     */
    public QuestVariant resolveVariant(net.minecraft.server.MinecraftServer server) {
        for (QuestVariant v : variants) {
            if (PhoenixQuestFlags.evaluate(v.condition, server)) return v;
        }
        return null;
    }

    /**
     * See {@link #getTitleRaw()} - the resolved variant's title, or the base title if no
     * variant matches or the matching variant doesn't override it.
     */
    public Component getEffectiveTitleRaw(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.title != null && !v.title.isBlank()) ? Component.literal(v.title) : getTitleRaw();
    }

    /** See {@link #getEffectiveTitleRaw}. */
    public Component getEffectiveDescriptionRaw(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.description != null && !v.description.isBlank()) ? Component.literal(v.description) :
                getDescriptionRaw();
    }

    /** See {@link #getEffectiveTitleRaw}. */
    public Visibility getEffectiveVisibility(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.visibility != null) ? v.visibility : visibility;
    }

    /**
     * The active variant's task list if one matches and overrides tasks, else this quest's own
     * base tasks. REPLACES rather than merges - use this (not {@link #getTasks()}) for anything
     * player-facing (completion checks, sync, display) so pack-mode variants actually take
     * effect; {@link #getTasks()} stays the raw base definition the editor shows/edits.
     */
    public List<QuestTask> getEffectiveTasks(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.tasks != null) ? Collections.unmodifiableList(v.tasks) : getTasks();
    }

    /** See {@link #getEffectiveTasks}. */
    public List<QuestReward> getEffectiveRewards(net.minecraft.server.MinecraftServer server) {
        QuestVariant v = resolveVariant(server);
        return (v != null && v.rewards != null) ? Collections.unmodifiableList(v.rewards) : getRewards();
    }

    // ── Emergency items ───────────────────────────────────────────────────────

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

    /**
     * Isolated in its own nested class so the dedicated server JVM never loads
     * {@code net.minecraft.client.resources.language.I18n} - callers must check
     * {@code FMLEnvironment.dist == Dist.CLIENT} before reaching this class (see getTitle()
     * etc.), matching the isolation pattern used elsewhere for client-only packet handling.
     */
    private static class ClientLangLookup {

        static Component resolve(String key) {
            return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : null;
        }
    }
}
