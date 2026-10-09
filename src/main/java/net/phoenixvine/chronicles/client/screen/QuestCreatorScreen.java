package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.registry.LangSyncScheduler;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.rich.CodeEditBox;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.CategoryDefinition;
import net.phoenixvine.chronicles.common.model.EmergencyKit;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.*;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuestCreatorScreen extends Screen {

    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_OK;
    private static final int C_ERR = 0xFFCC4444;
    private static final int C_SHAPE_SEL = 0x775533AA;
    private static final int C_SECTION_HEADER = 0xFF1A1A26;
    private static final int C_SECTION_HEADER_HOV = 0xFF20202E;

    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 32;
    private static final int MARGIN = 14;
    private static final int MAX_W = 520;
    private static final int LABEL_H = 8;
    private static final int LABEL_GAP = 4;
    private static final int FIELD_H = 16;
    private static final int ROW_GAP = 10;
    private static final int STRIDE = LABEL_H + 3 + FIELD_H + ROW_GAP;
    private static final int EDIT_W = 20;
    private static final int COL_GAP = 8;
    private static final int SEC_PAD = 3;
    private static final int SEC_HEADER_H = 18;
    private static final int SEC_HEADER_GAP = 4;

    private enum Section {

        BASIC_INFO("Basic Info"),
        POSITION_SIZE("Position & Size"),
        TASKS_REWARDS("Tasks & Rewards"),
        EMERGENCY("Emergency Items"),
        VARIANTS("Variants"),
        VISIBILITY_PREREQS("Visibility & Prerequisites"),
        REWARDS_REPEATS("Rewards & Repeats"),
        AUDIO("Narration & Music"),
        ADVANCED("Advanced"),
        RAW("Raw SNBT Preview");

        final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private final Set<Section> collapsedSections = new HashSet<>(List.of(
            Section.TASKS_REWARDS, Section.EMERGENCY, Section.VARIANTS, Section.REWARDS_REPEATS, Section.ADVANCED,
            Section.RAW));

    private record SectionHeaderRect(Section section, int y, int h) {}

    private final List<SectionHeaderRect> sectionHeaderRects = new ArrayList<>();

    private record LabelEntry(int x, int y, String text, int color) {}

    private final List<LabelEntry> labels = new ArrayList<>();

    private int panelScrollY = 0;
    private int scrollContentTop, scrollContentBottom;
    private int totalContentH = 0;

    private int footerStartIndex = 0;

    private record ShapeMeta(String id, String glyph) {}

    private static final ShapeMeta[] SHAPES = {
            new ShapeMeta("SQUARE", "■"), new ShapeMeta("CIRCLE", "●"),
            new ShapeMeta("DIAMOND", "◆"), new ShapeMeta("HEXAGON", "⬡"),
            new ShapeMeta("TRIANGLE", "▲"), new ShapeMeta("STAR", "★"),
            new ShapeMeta("PENTAGON", "⬠"), new ShapeMeta("SHIELD", "❖"),
            new ShapeMeta("CROSS", "✚"), new ShapeMeta("CUSTOM", "▩"),
            new ShapeMeta("NONE", "◌"),
    };

    private static final QuestNode.NodeSize[] NODE_SIZES = QuestNode.NodeSize.values();
    private static final String[] NODE_SIZE_LABELS = { "Tiny", "Small", "Normal", "Large", "Huge" };

    private final Screen parent;

    Screen getParentScreen() {
        return parent;
    }

    private final QuestNode editingNode;

    @org.jetbrains.annotations.Nullable
    private QuestNode lastSavedNode;

    private String cachedTitle = "";
    private String cachedDesc = "";
    private String cachedSubtitle = "";
    private String cachedChapter = "MAIN";
    private String cachedIconItemId = "";
    private String cachedShape = "SQUARE";
    private String cachedLabelPosition = "BOTTOM";
    private static final String[] LABEL_POSITIONS = { "BOTTOM", "TOP", "LEFT", "RIGHT" };

    private String cachedShapeTexture = "";
    private String cachedBackgroundType = "";
    private QuestNode.Visibility cachedVisibility = QuestNode.Visibility.NORMAL;
    private String cachedEnableIf = "";

    private Boolean cachedRequireAll = null;
    private java.util.Set<String> cachedChapterPrereqs = new java.util.LinkedHashSet<>();
    private java.util.Set<String> cachedCategoryPrereqs = new java.util.LinkedHashSet<>();
    private boolean cachedDisabledBlocksChildren = false;
    private final List<QuestNode> cachedPrerequisites = new ArrayList<>();
    private int cachedTaskMinCount = 0;
    private String cachedId = "";
    private boolean idManuallySet = false;

    private boolean suppressIdResponder = false;
    private boolean initialized = false;
    private QuestNode.RepeatMode cachedRepeatMode = QuestNode.RepeatMode.NONE;
    private int cachedRepeatCooldownHours = 24;
    private boolean cachedHideDepLine = false;
    private boolean cachedAutoClaimRewards = false;
    private boolean cachedRewardChoice = false;
    private int cachedRewardChoiceCount = 1;
    private QuestNode.NodeSize cachedNodeSize = QuestNode.NodeSize.NORMAL;

    private int cachedSizeOverridePx = 0;
    private String cachedDevNotes = "";
    private String cachedPreviewMachineId = "";
    private String cachedExternalScreenId = "";
    private String cachedUnlockSoundId = "";
    private String cachedCompleteSoundId = "";
    private String cachedVoiceId = "";
    private boolean cachedVoiceAuto = false;
    private String cachedMusicId = "";
    private String cachedMusicVolumePct = "100";
    private String cachedMusicFadeSec = "1.5";
    private int cachedPosX = 40;
    private int cachedPosY = 70;

    private QuestNode pendingWorkingNode = null;

    private EditBox titleBox, descBox, subtitleBox, chapterBox, idBox, posXBox, posYBox;

    private boolean visibilityDropdownOpen = false;
    private boolean chapterDropdownOpen = false;
    private static final QuestNode.Visibility[] VISIBILITIES = QuestNode.Visibility.values();

    private boolean cancelConfirmOpen = false;

    private int lastMouseX, lastMouseY;

    private String statusMsg = "";
    private boolean statusIsErr = false;

    private int cx, cw;
    private int shapeRowY, shapeColW, shapeX, iconColW;
    private int visRowY, visW;
    private int catRowY, catW;
    private int repeatRowY, repeatBtnW;
    private int idRowLabelY;

    public QuestCreatorScreen(Screen parent) {
        this(parent, (String) null);
    }

    public QuestCreatorScreen(Screen parent, String defaultChapter) {
        super(ChroniclesUIKit.lit("New Quest"));
        this.parent = parent;
        this.editingNode = null;
        if (defaultChapter != null && !defaultChapter.isBlank()) this.cachedChapter = defaultChapter;
        initialSnapshot = snapshotKey();
    }

    public QuestCreatorScreen(Screen parent, int canvasX, int canvasY) {
        this(parent, canvasX, canvasY, null);
    }

    public QuestCreatorScreen(Screen parent, int canvasX, int canvasY, String defaultChapter) {
        super(ChroniclesUIKit.lit("New Quest"));
        this.parent = parent;
        this.editingNode = null;
        this.cachedPosX = canvasX;
        this.cachedPosY = canvasY;
        if (defaultChapter != null && !defaultChapter.isBlank()) this.cachedChapter = defaultChapter;
        initialSnapshot = snapshotKey();
    }

    public QuestCreatorScreen(Screen parent, QuestNode editingNode) {
        super(ChroniclesUIKit.lit("Edit Quest"));
        this.parent = parent;
        this.editingNode = editingNode;

        cachedId = editingNode.getId().getPath();

        cachedTitle = editingNode.getTitleRaw().getString();
        cachedDesc = editingNode.getDescriptionRaw().getString();
        cachedSubtitle = editingNode.getSubtitleRaw() != null ? editingNode.getSubtitleRaw() : "";
        cachedChapter = editingNode.getChapter();
        cachedIconItemId = editingNode.getIconItemId();
        cachedShape = editingNode.getShapeType() != null ? editingNode.getShapeType() : "SQUARE";
        cachedLabelPosition = editingNode.getLabelPosition() != null ? editingNode.getLabelPosition() : "BOTTOM";
        cachedShapeTexture = editingNode.getShapeTexture() != null ? editingNode.getShapeTexture() : "";
        cachedBackgroundType = editingNode.getBackgroundType() != null ? editingNode.getBackgroundType() : "";
        cachedVisibility = editingNode.getVisibility() != null ? editingNode.getVisibility() :
                QuestNode.Visibility.NORMAL;
        cachedEnableIf = editingNode.getEnableIf() != null ? editingNode.getEnableIf() : "";
        cachedRequireAll = editingNode.getRequireAllPrerequisites();
        cachedChapterPrereqs = new java.util.LinkedHashSet<>(editingNode.getChapterPrereqs());
        cachedCategoryPrereqs = new java.util.LinkedHashSet<>(editingNode.getCategoryPrereqs());
        cachedDisabledBlocksChildren = editingNode.isDisabledBlocksChildren();
        cachedTaskMinCount = editingNode.getTaskMinCount();
        cachedRepeatMode = editingNode.getRepeatMode() != null ? editingNode.getRepeatMode() :
                QuestNode.RepeatMode.NONE;
        cachedRepeatCooldownHours = editingNode.getRepeatCooldownHours();
        cachedHideDepLine = editingNode.isHideDepLine();
        cachedAutoClaimRewards = editingNode.isAutoClaimRewards();
        cachedRewardChoice = editingNode.isRewardChoice();
        cachedRewardChoiceCount = editingNode.getRewardChoiceCount();
        cachedNodeSize = editingNode.getNodeSize();
        cachedSizeOverridePx = editingNode.getSizeOverridePx();
        cachedDevNotes = editingNode.getDevNotes();
        cachedPreviewMachineId = editingNode.getPreviewMachineId();
        cachedExternalScreenId = editingNode.getExternalScreenId();
        cachedUnlockSoundId = editingNode.getUnlockSoundId();
        cachedCompleteSoundId = editingNode.getCompleteSoundId();
        cachedVoiceId = editingNode.getAudio().voiceId();
        cachedVoiceAuto = editingNode.getAudio().voiceAuto();
        cachedMusicId = editingNode.getAudio().musicId();
        cachedMusicVolumePct = String.valueOf(Math.round(editingNode.getAudio().musicVolume() * 100));
        cachedMusicFadeSec = String.valueOf(editingNode.getAudio().fadeMs() / 1000f);

        cachedPosX = editingNode.getCustomX();
        cachedPosY = editingNode.getCustomY();
        cachedPrerequisites.addAll(editingNode.getPrerequisites());
        idManuallySet = true;
        initialized = true;
        initialSnapshot = snapshotKey();
    }

    private String initialSnapshot = "";

    private String snapshotKey() {
        String prereqKey = cachedPrerequisites.stream().map(n -> n.getId().toString()).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        return String.join("",
                cachedId, cachedTitle, cachedDesc, cachedSubtitle, cachedChapter, cachedIconItemId, cachedShape,
                cachedLabelPosition,
                cachedShapeTexture, String.valueOf(cachedVisibility), cachedEnableIf,
                String.valueOf(cachedRequireAll), String.valueOf(cachedChapterPrereqs),
                String.valueOf(cachedCategoryPrereqs), String.valueOf(cachedDisabledBlocksChildren),
                String.valueOf(cachedTaskMinCount), String.valueOf(cachedRepeatMode),
                String.valueOf(cachedRepeatCooldownHours), String.valueOf(cachedHideDepLine),
                String.valueOf(cachedAutoClaimRewards), String.valueOf(cachedRewardChoice),
                String.valueOf(cachedRewardChoiceCount), String.valueOf(cachedNodeSize),
                String.valueOf(cachedSizeOverridePx), cachedDevNotes, cachedPreviewMachineId,
                cachedExternalScreenId, cachedUnlockSoundId, cachedCompleteSoundId, cachedVoiceId,
                String.valueOf(cachedVoiceAuto), cachedMusicId, cachedMusicVolumePct, cachedMusicFadeSec,
                String.valueOf(cachedPosX), String.valueOf(cachedPosY), prereqKey);
    }

    private boolean hasUnsavedChanges() {
        return !snapshotKey().equals(initialSnapshot);
    }

    @Override
    protected void init() {
        PhoenixTheme t = PhoenixTheme.current();
        C_BG = t.bg.getColor();
        C_PANEL = t.panel.getColor();
        C_HEADER = t.header.getColor();
        C_BORDER = t.border.getColor();
        C_ACCENT = t.accent.getColor();
        C_TEXT = t.text.getColor();
        C_TEXT_DIM = t.textDim.getColor();
        C_TEXT_FAINT = t.textFaint.getColor();
        C_OK = t.done.getColor();

        clearWidgets();
        labels.clear();
        sectionHeaderRects.clear();
        if (!initialized) initialized = true;

        cw = Math.min(width - MARGIN * 2, MAX_W);
        cx = (width - cw) / 2;

        scrollContentTop = HEADER_H + 1;
        scrollContentBottom = height - FOOTER_H - 4;

        {
            int viewHPre = scrollContentBottom - scrollContentTop;
            int maxScrollPre = Math.max(0, totalContentH - viewHPre);
            panelScrollY = Math.max(0, Math.min(maxScrollPre, panelScrollY));
        }

        int y = scrollContentTop + SEC_PAD - panelScrollY;
        int firstWidgetIndex = 0;

        y = buildSection(Section.BASIC_INFO, y, this::buildBasicInfo, this::basicInfoSummary);
        y = buildSection(Section.POSITION_SIZE, y, this::buildPositionSize, this::positionSizeSummary);
        y = buildSection(Section.TASKS_REWARDS, y, this::buildTasksRewards, this::tasksRewardsSummary);
        y = buildSection(Section.EMERGENCY, y, this::buildEmergency, this::emergencySummary);
        y = buildSection(Section.VARIANTS, y, this::buildVariants, this::variantsSummary);
        y = buildSection(Section.VISIBILITY_PREREQS, y, this::buildVisibilityPrereqs, this::visibilityPrereqsSummary);
        y = buildSection(Section.REWARDS_REPEATS, y, this::buildRewardsRepeats, this::rewardsRepeatsSummary);
        y = buildSection(Section.AUDIO, y, this::buildAudioSection, this::audioSummary);
        y = buildSection(Section.ADVANCED, y, this::buildAdvanced, this::advancedSummary);
        y = buildSection(Section.RAW, y, this::buildRaw, () -> "");

        totalContentH = (y + panelScrollY) - (scrollContentTop + SEC_PAD);

        int viewH = scrollContentBottom - scrollContentTop;
        int maxScroll = Math.max(0, totalContentH - viewH);
        panelScrollY = Math.max(0, Math.min(maxScroll, panelScrollY));

        for (int i = firstWidgetIndex; i < this.renderables.size(); i++) {
            if (this.renderables.get(i) instanceof AbstractWidget w) {
                boolean visible = w.getY() + w.getHeight() > scrollContentTop && w.getY() < scrollContentBottom;
                w.visible = visible;
            }
        }

        footerStartIndex = this.renderables.size();

        int fbtnY = height - FOOTER_H + (FOOTER_H - 16) / 2;
        int halfW = (cw - COL_GAP) / 2;
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§a✓ Save & Close"), b -> {
            save();
            if (!statusIsErr) {
                ChronicleOverviewScreen.invalidateNodeCachesUpChain(parent, lastSavedNode);
                if (minecraft != null) minecraft.setScreen(parent);
            }
        }).bounds(cx, fbtnY, halfW, 16)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Write quest to disk, register it live, and return")))
                .build());
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f✕ Cancel"), b -> {
            if (hasUnsavedChanges()) {
                cancelConfirmOpen = true;
            } else if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        }).bounds(cx + halfW + COL_GAP, fbtnY, halfW, 16)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Discard unsaved changes and return"))).build());
    }

    @FunctionalInterface
    private interface RowBuilder {

        int build(int y);
    }

    @FunctionalInterface
    private interface SummaryProvider {

        String get();
    }

    private int buildSection(Section sec, int y, RowBuilder builder, SummaryProvider summary) {
        sectionHeaderRects.add(new SectionHeaderRect(sec, y, SEC_HEADER_H));
        y += SEC_HEADER_H + SEC_HEADER_GAP;
        if (!collapsedSections.contains(sec)) {
            y = builder.build(y);
            y += SEC_HEADER_GAP;
        }
        return y;
    }

    private int buildBasicInfo(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fTitle", C_TEXT_FAINT));
        titleBox = new CodeEditBox(font, cx, rowY, cw - EDIT_W - 2, FIELD_H, Component.empty());
        titleBox.setMaxLength(160);
        titleBox.setHint(ChroniclesUIKit.lit("§fQuest title shown to players"));
        titleBox.setValue(cachedTitle);
        titleBox.setResponder(v -> {
            cachedTitle = v;
            if (!idManuallySet) {
                cachedId = v.trim().toLowerCase().replaceAll("[^a-z0-9 /._-]", "").replaceAll("\\s+", "_");
                if (idBox != null) {
                    suppressIdResponder = true;
                    idBox.setValue(cachedId);
                    suppressIdResponder = false;
                }
            }
        });
        addRenderableWidget(titleBox);
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f✎"),
                b -> Minecraft.getInstance().setScreen(new QuestTextInputScreen(this, "Title", cachedTitle, 64,
                        v -> {
                            cachedTitle = v;
                            if (titleBox != null) titleBox.setValue(v);
                        })))
                .bounds(cx + cw - EDIT_W, rowY, EDIT_W, FIELD_H).build());
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fDescription", C_TEXT_FAINT));
        descBox = new CodeEditBox(font, cx, rowY, cw - EDIT_W - 2, FIELD_H, Component.empty());
        descBox.setMaxLength(512);
        descBox.setHint(ChroniclesUIKit.lit("§fShort description / lore text"));
        descBox.setValue(cachedDesc);
        descBox.setResponder(v -> cachedDesc = v);
        addRenderableWidget(descBox);
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f✎"),
                b -> Minecraft.getInstance()
                        .setScreen(new QuestTextInputScreen(this, "Description", cachedDesc, 8192,
                                v -> {
                                    cachedDesc = v;
                                    if (descBox != null) descBox.setValue(v);
                                })))
                .bounds(cx + cw - EDIT_W, rowY, EDIT_W, FIELD_H).build());
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        int catColW = (int) (cw * 0.55f);
        int subW = cw - catColW - COL_GAP;
        int subX = cx + catColW + COL_GAP;
        catRowY = y;
        catW = catColW;
        labels.add(new LabelEntry(cx, y, "§fChapter", C_TEXT_FAINT));
        labels.add(new LabelEntry(subX, y, "§fSubtitle", C_TEXT_FAINT));
        int catPickW = 16, newCatW = 32;
        int catBoxW = catColW - catPickW - 2 - newCatW - 2;
        chapterBox = new EditBox(font, cx, rowY, catBoxW, FIELD_H, Component.empty());
        chapterBox.setMaxLength(32);
        chapterBox.setHint(ChroniclesUIKit.lit("§fMAIN  CHAPTER_1  …"));
        chapterBox.setValue(cachedChapter);
        chapterBox.setResponder(v -> {
            cachedChapter = v;
            chapterDropdownOpen = false;
        });
        addRenderableWidget(chapterBox);
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f▾"), b -> {
            chapterDropdownOpen = !chapterDropdownOpen;
            visibilityDropdownOpen = false;
        }).bounds(cx + catBoxW + 2, rowY, catPickW, FIELD_H).build());
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§a+New"), b -> {
            chapterDropdownOpen = false;
            cachedChapter = "";
            if (chapterBox != null) {
                chapterBox.setValue("");
                chapterBox.setFocused(true);
            }
        }).bounds(cx + catBoxW + 2 + catPickW + 2, rowY, newCatW, FIELD_H).build());
        subtitleBox = new CodeEditBox(font, subX, rowY, subW - EDIT_W - 2, FIELD_H, Component.empty());
        subtitleBox.setMaxLength(256);
        subtitleBox.setHint(ChroniclesUIKit.lit("§fSubtitle…"));
        subtitleBox.setValue(cachedSubtitle);
        subtitleBox.setResponder(v -> cachedSubtitle = v);
        addRenderableWidget(subtitleBox);
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f✎"),
                b -> Minecraft.getInstance()
                        .setScreen(new QuestTextInputScreen(this, "Subtitle", cachedSubtitle, 128,
                                v -> {
                                    cachedSubtitle = v;
                                    if (subtitleBox != null) subtitleBox.setValue(v);
                                })))
                .bounds(subX + subW - EDIT_W, rowY, EDIT_W, FIELD_H).build());
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        int iconW = (int) (cw * 0.35f);
        int shapeW = cw - iconW - COL_GAP;
        int sX = cx + iconW + COL_GAP;
        iconColW = iconW;
        shapeRowY = rowY;
        shapeColW = shapeW;
        shapeX = sX;
        labels.add(new LabelEntry(cx, y, "§fIcon", C_TEXT_FAINT));
        labels.add(new LabelEntry(sX, y, "§fShape  §f" + cachedShape, C_TEXT_FAINT));
        net.minecraft.world.item.Item iconItem = cachedIconItemId.isBlank() ? null :
                ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(cachedIconItemId));
        String iconBtnLabel = (iconItem != null && iconItem != net.minecraft.world.item.Items.AIR) ?
                "§f" + new net.minecraft.world.item.ItemStack(iconItem).getHoverName().getString() : "§fPick icon…";
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit(iconBtnLabel), b -> {
            if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                cachedIconItemId = id != null ? id.toString() : "";
                rebuildWidgets();
            }));
        }).bounds(cx, rowY, iconW - EDIT_W - 2, FIELD_H).build());
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§c×"), b -> {
            cachedIconItemId = "";
            rebuildWidgets();
        }).bounds(cx + iconW - EDIT_W, rowY, EDIT_W, FIELD_H).build());
        int shapeSlot = shapeW / SHAPES.length;
        for (int i = 0; i < SHAPES.length; i++) {
            ShapeMeta sm = SHAPES[i];
            boolean sel = sm.id().equals(cachedShape);
            addRenderableWidget(Button.builder(
                    ChroniclesUIKit.lit((sel ? "§d" : "§f") + sm.glyph()),
                    b -> {
                        if ("CUSTOM".equals(sm.id())) {
                            if (minecraft != null) minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                                cachedShape = "CUSTOM";
                                cachedShapeTexture = rl;
                                rebuildWidgets();
                            }));
                        } else {
                            cachedShape = sm.id();
                            rebuildWidgets();
                        }
                    })
                    .bounds(sX + i * shapeSlot, rowY, shapeSlot - 1, FIELD_H).build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        java.util.List<String> bgIds = new java.util.ArrayList<>();
        bgIds.add("");
        bgIds.addAll(QuestBackgroundRegistry.getAll().keySet());
        String bgLabel = cachedBackgroundType.isBlank() ? "§fNo background" : "§d" + cachedBackgroundType;
        labels.add(new LabelEntry(cx, y, "§fBackground (animated, drawn as the node's own body)", C_TEXT_FAINT));
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit(bgLabel), b -> {
            int idx = bgIds.indexOf(cachedBackgroundType);
            cachedBackgroundType = bgIds.get((idx + 1) % bgIds.size());
            rebuildWidgets();
        }).bounds(cx, rowY, cw, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Cycles through every registered animated background (see QuestBackgroundRegistry) - " +
                                "\"No background\" leaves this quest's normal flat state-colored body. " +
                                "Custom ones can be added via Java or KubeJS (QuestBackgroundBuilder / " +
                                "BackgroundEffects), no shader knowledge required.")))
                .build());
        y = rowY + FIELD_H + ROW_GAP;

        if (editingNode != null) {
            rowY = y + LABEL_H + LABEL_GAP;
            labels.add(new LabelEntry(cx, y, "§fAppearance", C_TEXT_FAINT));
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f🔔 Design Pop-Up…"),
                    b -> Minecraft.getInstance().setScreen(new ToastDesignerScreen(this, editingNode)))
                    .bounds(cx, rowY, cw, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "Customize this quest's unlock/completion toast popup appearance")))
                    .build());
            y = rowY + FIELD_H;
        }
        return y;
    }

    private String basicInfoSummary() {
        return cachedTitle.isBlank() ? "§f(untitled)" : "§f" + cachedTitle;
    }

    private int buildPositionSize(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fCanvas Position X/Y", C_TEXT_FAINT));
        int halfPosW = (cw - COL_GAP) / 2;
        posXBox = new EditBox(font, cx, rowY, halfPosW, FIELD_H, Component.empty());
        posXBox.setMaxLength(6);
        posXBox.setHint(ChroniclesUIKit.lit("§fX"));
        posXBox.setValue(String.valueOf(cachedPosX));
        posXBox.setResponder(v -> {
            try {
                cachedPosX = Integer.parseInt(v.trim());
            } catch (Exception ignored) {}
        });
        addRenderableWidget(posXBox);
        posYBox = new EditBox(font, cx + halfPosW + COL_GAP, rowY, halfPosW, FIELD_H, Component.empty());
        posYBox.setMaxLength(6);
        posYBox.setHint(ChroniclesUIKit.lit("§fY"));
        posYBox.setValue(String.valueOf(cachedPosY));
        posYBox.setResponder(v -> {
            try {
                cachedPosY = Integer.parseInt(v.trim());
            } catch (Exception ignored) {}
        });
        addRenderableWidget(posYBox);
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        String sizeHeaderLabel = cachedSizeOverridePx > 0 ?
                "§fNode size  §b(custom " + cachedSizeOverridePx + "px active. Pick a preset below to reset)" :
                "§fNode size";
        labels.add(new LabelEntry(cx, y, sizeHeaderLabel, C_TEXT_FAINT));
        int sizeBtnW = cw / NODE_SIZES.length;
        for (int i = 0; i < NODE_SIZES.length; i++) {
            QuestNode.NodeSize sizeOpt = NODE_SIZES[i];
            boolean sel = cachedSizeOverridePx <= 0 && cachedNodeSize == sizeOpt;
            String lbl = (sel ? "§d" : "§f") + NODE_SIZE_LABELS[i];
            int bx = cx + i * sizeBtnW;
            int bw = (i == NODE_SIZES.length - 1) ? (cw - i * sizeBtnW) : sizeBtnW - 1;
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit(lbl), b -> {
                cachedNodeSize = sizeOpt;
                cachedSizeOverridePx = 0;
                rebuildWidgets();
            }).bounds(bx, rowY, bw, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "Node size on the quest canvas.\n\n" +
                                    "Tiny=14px\n" +
                                    "Small=18px\n" +
                                    "Normal=32px\n" +
                                    "Large=48px\n" +
                                    "Huge=64px\n\n" +
                                    "For freeform pixel control, use the canvas's own right-click →\n" +
                                    "\"Resize (scroll + drag)…\" instead.")))
                    .build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fTitle label position", C_TEXT_FAINT));
        int lblBtnW = cw / LABEL_POSITIONS.length;
        for (int i = 0; i < LABEL_POSITIONS.length; i++) {
            String posOpt = LABEL_POSITIONS[i];
            boolean sel = posOpt.equals(cachedLabelPosition);
            String lbl = (sel ? "§d" : "§f") + posOpt.charAt(0) + posOpt.substring(1).toLowerCase();
            int bx = cx + i * lblBtnW;
            int bw = (i == LABEL_POSITIONS.length - 1) ? (cw - i * lblBtnW) : lblBtnW - 1;
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit(lbl), b -> {
                cachedLabelPosition = posOpt;
                rebuildWidgets();
            }).bounds(bx, rowY, bw, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "Where this quest's title text draws relative to its icon on the map.")))
                    .build());
        }
        y = rowY + FIELD_H;
        return y;
    }

    private String positionSizeSummary() {
        return "§f(" + cachedPosX + ", " + cachedPosY + ")";
    }

    private int buildTasksRewards(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        int taskCount = editingNode != null ? editingNode.getTasks().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getTasks().size() : 0);
        int rewardCount = editingNode != null ? editingNode.getRewards().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getRewards().size() : 0);
        labels.add(new LabelEntry(cx, y,
                "§f" + taskCount + " task(s)  ·  " + rewardCount + " reward(s)", C_TEXT_FAINT));
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f⊞ Open Tasks & Rewards Editor"), b -> {
            chapterDropdownOpen = false;
            visibilityDropdownOpen = false;
            Minecraft.getInstance().setScreen(new TaskRewardEditorScreen(this, resolveWorkingNode()));
        }).bounds(cx, rowY, cw, FIELD_H).build());
        return rowY + FIELD_H;
    }

    private int buildEmergency(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        EmergencyKit kit = resolveWorkingNode().getEmergencyKit();
        labels.add(new LabelEntry(cx, y, "§fFallback rewards for a player stuck on this quest", C_TEXT_FAINT));
        addRenderableWidget(Button.builder(
                ChroniclesUIKit.lit("§6⚠ §fEdit Emergency Items" +
                        (kit.hasRewards() ? " §7(" + kit.getRewards().size() + ")" : "")),
                b -> {
                    chapterDropdownOpen = false;
                    visibilityDropdownOpen = false;
                    Minecraft.getInstance()
                            .setScreen(new EmergencyItemsScreen(this, resolveWorkingNode(), () -> cachedChapter));
                })
                .bounds(cx, rowY, cw, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Rewards for a player who lost an item the quest needs, or got stuck. They claim them from\n" +
                                "the \u26a0 button in the header or the quest popup. The screen also sets how often\n" +
                                "they can be claimed, and edits the chapter default for quests with no kit of their own.")))
                .build());
        return rowY + FIELD_H;
    }

    private String emergencySummary() {
        EmergencyKit kit = resolveWorkingNode().getEmergencyKit();
        return kit.hasRewards() ? "§f" + kit.getRewards().size() + " reward(s)" : "§8none";
    }

    private String tasksRewardsSummary() {
        int taskCount = editingNode != null ? editingNode.getTasks().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getTasks().size() : 0);
        return "§f" + taskCount + " task(s)";
    }

    private int buildVariants(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        int variantCount = editingNode != null ? editingNode.getVariants().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getVariants().size() : 0);
        labels.add(new LabelEntry(cx, y, "§f" + variantCount + " variant(s)", C_TEXT_FAINT));
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f◈ Open Variants Editor"), b -> {
            chapterDropdownOpen = false;
            visibilityDropdownOpen = false;
            Minecraft.getInstance().setScreen(new VariantEditorScreen(this, resolveWorkingNode()));
        }).bounds(cx, rowY, cw, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Pack-mode variants: override this quest's title/description/visibility/tasks/rewards based on a flag condition (e.g. config:pack_mode=expert)")))
                .build());
        return rowY + FIELD_H;
    }

    private String variantsSummary() {
        int variantCount = editingNode != null ? editingNode.getVariants().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getVariants().size() : 0);
        return "§f" + variantCount;
    }

    private QuestNode resolveWorkingNode() {
        if (editingNode != null) return editingNode;
        if (pendingWorkingNode == null) {
            String id = cachedId.trim().isEmpty() ? "_preview_" : cachedId.trim();
            pendingWorkingNode = new QuestNode(
                    ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", id),
                    Component.literal(cachedTitle), Component.literal(cachedDesc));
        }
        return pendingWorkingNode;
    }

    private int buildVisibilityPrereqs(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fVisibility  ·  Prerequisite gate", C_TEXT_FAINT));
        int vw = 90;
        visRowY = y;
        visW = vw;
        addRenderableWidget(Button.builder(
                ChroniclesUIKit.lit("§f" + cachedVisibility.name() + " §f▾"),
                b -> {
                    visibilityDropdownOpen = !visibilityDropdownOpen;
                    chapterDropdownOpen = false;
                })
                .bounds(cx, rowY, vw, FIELD_H).build());
        boolean showBlock = cachedVisibility == QuestNode.Visibility.DISABLED;
        int blockW = showBlock ? 90 : 0;
        int prereqW = cw - vw - COL_GAP - (showBlock ? blockW + COL_GAP : 0);
        String prereqLabel;
        if (cachedRequireAll == null) {

            Boolean catDefault = ChapterPrereqDefaults
                    .getRequireAll(cachedChapter);
            boolean effective = catDefault != null ? catDefault : true;
            prereqLabel = "§fInherit (" + (effective ? "ALL" : "ANY") + ") §f▾";
        } else if (cachedRequireAll) {
            prereqLabel = "§a✔ ALL prereqs required";
        } else {
            prereqLabel = "§e◑ ANY prereq sufficient";
        }
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit(prereqLabel),
                b -> {

                    if (cachedRequireAll == null) cachedRequireAll = true;
                    else if (cachedRequireAll) cachedRequireAll = false;
                    else cachedRequireAll = null;
                    rebuildWidgets();
                })
                .bounds(cx + vw + COL_GAP, rowY, prereqW, FIELD_H).build());
        if (showBlock) {
            String blkLabel = cachedDisabledBlocksChildren ? "§eBlocks children" : "§fBlocks children";
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit(blkLabel),
                    b -> {
                        cachedDisabledBlocksChildren = !cachedDisabledBlocksChildren;
                        rebuildWidgets();
                    })
                    .bounds(cx + vw + COL_GAP + prereqW + COL_GAP, rowY, blockW, FIELD_H).build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fWhole chapters / categories this quest waits on", C_TEXT_FAINT));
        int chapterDepCount = cachedChapterPrereqs.size() + cachedCategoryPrereqs.size();
        addRenderableWidget(Button.builder(
                ChroniclesUIKit.lit("§f▤ Chapter & Category Dependencies" +
                        (chapterDepCount > 0 ? " §7(" + chapterDepCount + ")" : "")),
                b -> {
                    chapterDropdownOpen = false;
                    visibilityDropdownOpen = false;
                    Minecraft.getInstance().setScreen(new ChapterDependencyScreen(this, cachedChapter,
                            cachedChapterPrereqs, cachedCategoryPrereqs, (chapters, categories) -> {
                                cachedChapterPrereqs = new java.util.LinkedHashSet<>(chapters);
                                cachedCategoryPrereqs = new java.util.LinkedHashSet<>(categories);
                            }));
                })
                .bounds(cx, rowY, cw, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Keep this quest locked until every counted quest in the picked chapters (or in every\n" +
                                "chapter of a picked category) is complete. This adds to the quest's own\n" +
                                "prerequisites; it doesn't replace them.")))
                .build());
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fTask completion gate", C_TEXT_FAINT));
        boolean anyMode = cachedTaskMinCount > 0;
        String gateLabel = anyMode ? "§e◑ Complete any " + cachedTaskMinCount + " task(s)" :
                "§a✔ Complete all tasks";
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit(gateLabel), b -> {
            cachedTaskMinCount = cachedTaskMinCount == 0 ? 1 : 0;
            rebuildWidgets();
        }).bounds(cx, rowY, anyMode ? cw - 50 : cw, FIELD_H).build());
        if (anyMode) {
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f−"), b -> {
                if (cachedTaskMinCount > 1) cachedTaskMinCount--;
                rebuildWidgets();
            }).bounds(cx + cw - 48, rowY, 22, FIELD_H).build());
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f+"), b -> {
                cachedTaskMinCount++;
                rebuildWidgets();
            }).bounds(cx + cw - 24, rowY, 22, FIELD_H).build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fenable_if", C_TEXT_FAINT));
        EditBox enableIfBox = new EditBox(font, cx, rowY, cw, FIELD_H, Component.empty());
        enableIfBox.setMaxLength(128);
        enableIfBox.setHint(ChroniclesUIKit.lit("§fenable_if…"));
        enableIfBox.setValue(cachedEnableIf);
        enableIfBox.setResponder(v -> {
            cachedEnableIf = v;
            if (editingNode != null) {
                editingNode.setEnableIf(v);
                QuestFileSaver.updateNodeEnableIf(editingNode);
            }
        });
        addRenderableWidget(enableIfBox);
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y,
                "§fPrerequisites  §f(" + cachedPrerequisites.size() + ")", C_TEXT_FAINT));
        String prereqsSummary = cachedPrerequisites.isEmpty() ? "§fNo prerequisites" :
                "§a" + cachedPrerequisites.stream().map(n -> n.getId().getPath())
                        .reduce((a, b) -> a + ", " + b).orElse("");
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§fManage Prerequisites…  " + prereqsSummary), b -> {
            chapterDropdownOpen = false;
            visibilityDropdownOpen = false;
            Minecraft.getInstance().setScreen(ParentSelectorScreen.multiSelect(this, editingNode,
                    new ArrayList<>(cachedPrerequisites), picked -> {
                        cachedPrerequisites.clear();
                        cachedPrerequisites.addAll(picked);
                        rebuildWidgets();
                    }));
        }).bounds(cx, rowY, cw, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Quests that must be completed before this one unlocks - multiple allowed. " +
                                "The first one picked also becomes this quest's canvas-tree parent " +
                                "(used only for grouping/layout, not for unlocking). " +
                                "You can also drag a link between quests directly on the canvas.")))
                .build());
        y = rowY + FIELD_H;
        return y;
    }

    private String visibilityPrereqsSummary() {
        return "§f" + cachedVisibility.name();
    }

    private int buildRewardsRepeats(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fRepeat mode", C_TEXT_FAINT));
        repeatRowY = rowY;
        boolean hasCooldown = cachedRepeatMode == QuestNode.RepeatMode.COOLDOWN;
        int repeatBtnWLocal = hasCooldown ? (int) (cw * 0.50f) : cw;
        repeatBtnW = repeatBtnWLocal;
        String repeatIcon = switch (cachedRepeatMode) {
            case NONE -> "§f⊘ One-time  §f▸";
            case DAILY -> "§b☀ Daily  §f▸";
            case COOLDOWN -> "§e⏱ Cooldown  §f▸";
            case INFINITE -> "§a∞ Infinite  §f▸";
        };
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit(repeatIcon), b -> {
            QuestNode.RepeatMode[] modes = QuestNode.RepeatMode.values();
            cachedRepeatMode = modes[(cachedRepeatMode.ordinal() + 1) % modes.length];
            rebuildWidgets();
        }).bounds(cx, rowY, repeatBtnWLocal, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "NONE = one-time only  ·  DAILY = resets at midnight  ·  COOLDOWN = custom wait  ·  INFINITE = repeats immediately")))
                .build());
        if (hasCooldown) {
            int coolW = cw - repeatBtnWLocal - COL_GAP;
            int coolX = cx + repeatBtnWLocal + COL_GAP;
            labels.add(new LabelEntry(coolX + 22, y, "§fCooldown hours", C_TEXT_FAINT));
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f−"), b -> {
                if (cachedRepeatCooldownHours > 1) cachedRepeatCooldownHours--;
                rebuildWidgets();
            }).bounds(coolX, rowY, 18, FIELD_H).build());
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f+"), b -> {
                cachedRepeatCooldownHours++;
                rebuildWidgets();
            }).bounds(coolX + coolW - 18, rowY, 18, FIELD_H).build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fRewards", C_TEXT_FAINT));
        String autoLabel = cachedAutoClaimRewards ? "§a⚡ Auto-claim rewards" : "§f⚡ Auto-claim rewards";
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit(autoLabel),
                b -> {
                    cachedAutoClaimRewards = !cachedAutoClaimRewards;
                    rebuildWidgets();
                })
                .bounds(cx, rowY, cw, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Automatically grant rewards on completion. No claim button needed")))
                .build());
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fChoice reward", C_TEXT_FAINT));
        String choiceLabel = cachedRewardChoice ? "§6◈ Reward choice: ON" : "§f◈ Reward choice: OFF";
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit(choiceLabel),
                b -> {
                    cachedRewardChoice = !cachedRewardChoice;
                    rebuildWidgets();
                })
                .bounds(cx, rowY, cachedRewardChoice ? cw - 54 : cw, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Player picks a reward from the list instead of receiving all")))
                .build());
        if (cachedRewardChoice) {
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f−"),
                    b -> {
                        if (cachedRewardChoiceCount > 1) {
                            cachedRewardChoiceCount--;
                            rebuildWidgets();
                        }
                    })
                    .bounds(cx + cw - 52, rowY, 16, FIELD_H).build());
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f" + cachedRewardChoiceCount), b -> {})
                    .bounds(cx + cw - 34, rowY, 18, FIELD_H).build());
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f+"),
                    b -> {
                        cachedRewardChoiceCount++;
                        rebuildWidgets();
                    })
                    .bounds(cx + cw - 14, rowY, 16, FIELD_H).build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        int hdepW = (int) (cw * 0.48f);
        labels.add(new LabelEntry(cx, y, "§fDependencies", C_TEXT_FAINT));
        String depToggleLabel = cachedHideDepLine ? "§e⊖ Dependency Lines" : "§f⊕ Dependency Lines";
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit(depToggleLabel),
                b -> {
                    cachedHideDepLine = !cachedHideDepLine;
                    rebuildWidgets();
                })
                .bounds(cx, rowY, hdepW, FIELD_H)
                .tooltip(Tooltip.create(
                        ChroniclesUIKit.lit("Hide all dependency lines connected to this node on the quest canvas")))
                .build());
        if (editingNode != null) {
            int childCount = editingNode.getChildren().size();
            String childStr = childCount == 0 ? "§fNo Dependents" :
                    "§f" + childCount + " quest" + (childCount == 1 ? "" : "s") + " unlock after this";
            labels.add(new LabelEntry(cx + hdepW + COL_GAP, rowY + (FIELD_H - 8) / 2 - LABEL_H - LABEL_GAP,
                    childStr, C_TEXT_DIM));
        }
        y = rowY + FIELD_H;
        return y;
    }

    private String rewardsRepeatsSummary() {
        return cachedRepeatMode == QuestNode.RepeatMode.NONE ? "§fOne-time" : "§f" + cachedRepeatMode.name();
    }

    private int buildAdvanced(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        idRowLabelY = y;
        int lockW = 36;
        int copyW = 36;
        idBox = new EditBox(font, cx, rowY, cw - lockW - copyW - 4, FIELD_H, Component.empty());
        idBox.setMaxLength(128);
        idBox.setHint(ChroniclesUIKit.lit("§fauto-generated from title"));
        idBox.setValue(cachedId);
        idBox.setResponder(v -> {
            cachedId = v;
            if (!suppressIdResponder) idManuallySet = !v.isEmpty();
        });
        addRenderableWidget(idBox);
        addRenderableWidget(Button.builder(
                ChroniclesUIKit.lit(idManuallySet ? "§cLocked" : "§aAuto"),
                b -> {
                    idManuallySet = !idManuallySet;
                    if (!idManuallySet) {
                        cachedId = cachedTitle.trim().toLowerCase()
                                .replaceAll("[^a-z0-9 /._-]", "").replaceAll("\\s+", "_");
                        if (idBox != null) {
                            suppressIdResponder = true;
                            idBox.setValue(cachedId);
                            suppressIdResponder = false;
                        }
                    }
                    rebuildWidgets();
                }).bounds(cx + cw - lockW - copyW - 2, rowY, lockW, FIELD_H).build());
        addRenderableWidget(Button.builder(
                ChroniclesUIKit.lit("§f⎘"),
                b -> {
                    String fullId = "phoenix_chronicles:" + (cachedId.isEmpty() ? "_unnamed_" : cachedId);
                    Minecraft.getInstance().keyboardHandler.setClipboard(fullId);
                })
                .bounds(cx + cw - copyW, rowY, copyW, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Copy full quest ID to clipboard"))).build());
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fDev Notes", C_TEXT_FAINT));
        EditBox devNotesBox = new EditBox(font, cx, rowY, cw, FIELD_H, Component.empty());
        devNotesBox.setMaxLength(512);
        devNotesBox.setHint(ChroniclesUIKit.lit("§fDev notes (internal, never shown to players)…"));
        devNotesBox.setValue(cachedDevNotes);
        devNotesBox.setResponder(v -> cachedDevNotes = v);
        addRenderableWidget(devNotesBox);
        y = rowY + FIELD_H;

        if (PhantasiaCompat.isAvailable()) {
            y += ROW_GAP;
            rowY = y + LABEL_H + LABEL_GAP;
            labels.add(new LabelEntry(cx, y, "§fPhantasia Preview", C_TEXT_FAINT));
            EditBox previewMachineBox = new EditBox(font, cx, rowY, cw, FIELD_H, Component.empty());
            previewMachineBox.setMaxLength(128);
            previewMachineBox
                    .setHint(ChroniclesUIKit.lit("§fPhantasia machine id shown in the quest viewer (optional)"));
            previewMachineBox.setValue(cachedPreviewMachineId);
            previewMachineBox.setResponder(v -> cachedPreviewMachineId = v);
            addRenderableWidget(previewMachineBox);
            y = rowY + FIELD_H;
        }

        y += ROW_GAP;
        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fExternal Screen ID", C_TEXT_FAINT));
        int screenPickW = 16;
        EditBox externalScreenBox = new EditBox(font, cx, rowY, cw - screenPickW - 2, FIELD_H, Component.empty());
        externalScreenBox.setMaxLength(128);
        externalScreenBox.setHint(ChroniclesUIKit
                .lit(
                        "§fRegistered external screen id (opens instead of the task viewer; used by \"Screen Opened\" tasks)"));
        externalScreenBox.setValue(cachedExternalScreenId);
        externalScreenBox.setResponder(v -> cachedExternalScreenId = v);
        addRenderableWidget(externalScreenBox);
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            java.util.Collection<ResourceLocation> ids = net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry
                    .registeredIds();
            if (minecraft != null && !ids.isEmpty()) {
                minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick external screen", ids, id -> {
                    cachedExternalScreenId = id.toString();
                    externalScreenBox.setValue(cachedExternalScreenId);
                }));
            }
        }).bounds(cx + cw - screenPickW, rowY, screenPickW, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse registered external screens")))
                .build());
        y = rowY + FIELD_H;

        y += ROW_GAP;
        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fUnlock Sound", C_TEXT_FAINT));
        int soundPickW = 16;
        EditBox unlockSoundBox = new EditBox(font, cx, rowY, cw - soundPickW - 2, FIELD_H, Component.empty());
        unlockSoundBox.setMaxLength(128);
        unlockSoundBox.setHint(ChroniclesUIKit.lit("§fSound played when this quest unlocks (blank = chapter/default)"));
        unlockSoundBox.setValue(cachedUnlockSoundId);
        unlockSoundBox.setResponder(v -> cachedUnlockSoundId = v);
        addRenderableWidget(unlockSoundBox);
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick unlock sound",
                        net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getKeys(), id -> {
                            cachedUnlockSoundId = id.toString();
                            unlockSoundBox.setValue(cachedUnlockSoundId);
                        }));
            }
        }).bounds(cx + cw - soundPickW, rowY, soundPickW, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse registered sound events")))
                .build());
        y = rowY + FIELD_H;

        y += ROW_GAP;
        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fComplete Sound", C_TEXT_FAINT));
        EditBox completeSoundBox = new EditBox(font, cx, rowY, cw - soundPickW - 2, FIELD_H, Component.empty());
        completeSoundBox.setMaxLength(128);
        completeSoundBox
                .setHint(ChroniclesUIKit.lit("§fSound played when this quest completes (blank = chapter/default)"));
        completeSoundBox.setValue(cachedCompleteSoundId);
        completeSoundBox.setResponder(v -> cachedCompleteSoundId = v);
        addRenderableWidget(completeSoundBox);
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick complete sound",
                        net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getKeys(), id -> {
                            cachedCompleteSoundId = id.toString();
                            completeSoundBox.setValue(cachedCompleteSoundId);
                        }));
            }
        }).bounds(cx + cw - soundPickW, rowY, soundPickW, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse registered sound events")))
                .build());
        y = rowY + FIELD_H;
        return y;
    }

    private String audioSummary() {
        boolean voice = !cachedVoiceId.isBlank();
        boolean music = !cachedMusicId.isBlank();
        if (!voice && !music) return "§8none";
        return "§f" + (voice ? "♪ voice" : "") + (voice && music ? "  " : "") + (music ? "♫ music" : "");
    }

    private int buildAudioSection(int y) {
        int soundPickW = 16;
        int rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fNarration (voice line)", C_TEXT_FAINT));
        int autoW = 56;
        EditBox voiceBox = new EditBox(font, cx, rowY, cw - soundPickW - autoW - 4, FIELD_H, Component.empty());
        voiceBox.setMaxLength(128);
        voiceBox.setHint(ChroniclesUIKit.lit("§fsound event id from a resource pack / KubeJS (blank = none)"));
        voiceBox.setValue(cachedVoiceId);
        voiceBox.setResponder(v -> cachedVoiceId = v);
        addRenderableWidget(voiceBox);
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick voice line",
                        net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getKeys(), id -> {
                            cachedVoiceId = id.toString();
                            voiceBox.setValue(cachedVoiceId);
                        }));
            }
        }).bounds(cx + cw - soundPickW - autoW - 2, rowY, soundPickW, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse registered sound events")))
                .build());
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit(cachedVoiceAuto ? "§aAuto" : "§8Auto"), b -> {
            cachedVoiceAuto = !cachedVoiceAuto;
            b.setMessage(ChroniclesUIKit.lit(cachedVoiceAuto ? "§aAuto" : "§8Auto"));
        }).bounds(cx + cw - autoW, rowY, autoW, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Auto: play the narration the moment the quest opens.\n" +
                                "Off: players start it with the ♪ button in the quest footer.")))
                .build());
        y = rowY + FIELD_H;

        y += ROW_GAP;
        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§fMusic  §8(volume %  /  fade seconds)", C_TEXT_FAINT));
        int numW = 34;
        EditBox musicBox = new EditBox(font, cx, rowY, cw - soundPickW - numW * 2 - 6, FIELD_H, Component.empty());
        musicBox.setMaxLength(128);
        musicBox.setHint(ChroniclesUIKit.lit("§floops while this quest is open (blank = chapter music)"));
        musicBox.setValue(cachedMusicId);
        musicBox.setResponder(v -> cachedMusicId = v);
        addRenderableWidget(musicBox);
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick music",
                        net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getKeys(), id -> {
                            cachedMusicId = id.toString();
                            musicBox.setValue(cachedMusicId);
                        }));
            }
        }).bounds(cx + cw - soundPickW - numW * 2 - 4, rowY, soundPickW, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse registered sound events")))
                .build());
        EditBox volBox = new EditBox(font, cx + cw - numW * 2 - 2, rowY, numW, FIELD_H, Component.empty());
        volBox.setMaxLength(3);
        volBox.setValue(cachedMusicVolumePct);
        volBox.setResponder(v -> cachedMusicVolumePct = v);
        volBox.setTooltip(Tooltip.create(ChroniclesUIKit.lit("Music volume, 0-100%")));
        addRenderableWidget(volBox);
        EditBox fadeBox = new EditBox(font, cx + cw - numW, rowY, numW, FIELD_H, Component.empty());
        fadeBox.setMaxLength(5);
        fadeBox.setValue(cachedMusicFadeSec);
        fadeBox.setResponder(v -> cachedMusicFadeSec = v);
        fadeBox.setTooltip(Tooltip.create(ChroniclesUIKit.lit("Fade in / out time in seconds")));
        addRenderableWidget(fadeBox);
        y = rowY + FIELD_H;
        return y;
    }

    private net.phoenixvine.chronicles.common.model.QuestAudio buildAudio() {
        float volume = 1f;
        int fadeMs = net.phoenixvine.chronicles.common.model.QuestAudio.DEFAULT_FADE_MS;
        try {
            volume = Integer.parseInt(cachedMusicVolumePct.trim()) / 100f;
        } catch (NumberFormatException ignored) {}
        try {
            fadeMs = Math.round(Float.parseFloat(cachedMusicFadeSec.trim()) * 1000f);
        } catch (NumberFormatException ignored) {}
        return new net.phoenixvine.chronicles.common.model.QuestAudio(cachedVoiceId, cachedVoiceAuto, cachedMusicId,
                volume, fadeMs);
    }

    private String advancedSummary() {
        return idManuallySet ? "§c" + cachedId + " (manual)" : "§f" + cachedId;
    }

    private int buildRaw(int y) {
        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§f⎘ Copy SNBT"),
                b -> {
                    if (minecraft != null) minecraft.keyboardHandler.setClipboard(buildCurrentSnbt());
                })
                .bounds(cx + cw - 80, y, 80, 14).build());
        y += 14 + ROW_GAP;

        int rawTextH = Math.min(220, Math.max(60, scrollContentBottom - scrollContentTop - 40));
        rawSnbtTop = y;
        rawSnbtBottom = y + rawTextH;
        y += rawTextH;
        return y;
    }

    private int rawSnbtTop, rawSnbtBottom;

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        lastMouseX = mx;
        lastMouseY = my;
        g.fill(0, 0, width, height, C_BG);

        int panelL = cx - SEC_PAD;
        int panelR = cx + cw + SEC_PAD;

        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, 0, width, 2, C_ACCENT);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        String heading = editingNode != null ? "§fEdit Quest  §f: §f" + editingNode.getId().getPath() : "§fNew Quest";
        ChroniclesUIKit.drawCenteredString(g, font, heading, width / 2, (HEADER_H - 8) / 2, C_TEXT);

        g.fill(0, height - FOOTER_H, width, height, C_HEADER);
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, C_BORDER);

        g.enableScissor(0, scrollContentTop, width, scrollContentBottom);
        g.fill(panelL, scrollContentTop, panelR, scrollContentBottom, C_PANEL);
        drawBorder(g, panelL, scrollContentTop, panelR - panelL, scrollContentBottom - scrollContentTop, C_BORDER);

        for (SectionHeaderRect r : sectionHeaderRects) {
            int hy = r.y();
            if (hy + r.h() <= scrollContentTop || hy >= scrollContentBottom) continue;
            boolean collapsed = collapsedSections.contains(r.section());
            boolean hov = mx >= panelL && mx < panelR && my >= hy && my < hy + r.h();
            g.fill(panelL, hy, panelR, hy + r.h(), hov ? C_SECTION_HEADER_HOV : C_SECTION_HEADER);
            String chevron = collapsed ? "▶" : "▼";
            ChroniclesUIKit.drawString(g, font, "§f" + chevron + " §f" + r.section().label, cx, hy + (r.h() - 8) / 2,
                    C_TEXT, false);
            if (collapsed) {
                String summary = sectionSummary(r.section());
                if (summary != null && !summary.isEmpty()) {
                    int sw = font.width(summary);
                    ChroniclesUIKit.drawString(g, font, summary, panelR - SEC_PAD - sw, hy + (r.h() - 8) / 2,
                            C_TEXT_DIM, false);
                }
            }
        }

        for (LabelEntry le : labels) {
            if (le.y() + LABEL_H <= scrollContentTop || le.y() >= scrollContentBottom) continue;
            ChroniclesUIKit.drawString(g, font, le.text(), le.x(), le.y(), le.color(), false);
        }

        if (!collapsedSections.contains(Section.BASIC_INFO) &&
                shapeRowY + FIELD_H > scrollContentTop && shapeRowY < scrollContentBottom) {
            int shapeSlot = shapeColW / SHAPES.length;
            for (int i = 0; i < SHAPES.length; i++) {
                if (SHAPES[i].id().equals(cachedShape))
                    g.fill(shapeX + i * shapeSlot, shapeRowY, shapeX + i * shapeSlot + shapeSlot - 1,
                            shapeRowY + FIELD_H, C_SHAPE_SEL);
            }
            if (!cachedIconItemId.isBlank()) {
                try {
                    net.minecraft.world.item.Item prev = ForgeRegistries.ITEMS
                            .getValue(ResourceLocation.parse(cachedIconItemId));
                    if (prev != null && prev != net.minecraft.world.item.Items.AIR)
                        g.renderItem(new net.minecraft.world.item.ItemStack(prev), cx + iconColW - 18,
                                shapeRowY - 1);
                } catch (Exception ignored) {}
            }
        }

        if (!collapsedSections.contains(Section.RAW) &&
                rawSnbtBottom > scrollContentTop && rawSnbtTop < scrollContentBottom) {
            int panTop = Math.max(rawSnbtTop, scrollContentTop);
            int panBot = Math.min(rawSnbtBottom, scrollContentBottom);
            g.enableScissor(cx - SEC_PAD, panTop, cx + cw + SEC_PAD, panBot);
            String raw = buildCurrentSnbt();
            int lineY = rawSnbtTop + 2;
            int lineH = 9;
            for (int ci = 0; ci < raw.length() && lineY + lineH < rawSnbtBottom;) {
                int end = Math.min(raw.length(), ci + (cw / 6));
                if (end < raw.length()) {
                    int lb = raw.lastIndexOf('\n', end);
                    if (lb > ci) end = lb + 1;
                }
                String seg = raw.substring(ci, end).replace("\n", " ");

                ChroniclesUIKit.drawString(g, font, seg, cx, lineY, 0xFFFFFFFF, false);
                lineY += lineH;
                ci = end;
            }
            g.disableScissor();
        }

        g.disableScissor();

        if (!statusMsg.isEmpty()) {
            ChroniclesUIKit.drawCenteredString(g, font, (statusIsErr ? "§c" : "§a") + statusMsg,
                    width / 2, height - FOOTER_H - 12, statusIsErr ? C_ERR : C_OK);
        }

        int viewH = scrollContentBottom - scrollContentTop;
        int maxScroll = Math.max(0, totalContentH - viewH);
        if (maxScroll > 0) {
            int trackX = panelR + 2;
            g.fill(trackX, scrollContentTop, trackX + 3, scrollContentBottom, 0x22FFFFFF);
            int thumbH = Math.max(16, viewH * viewH / (viewH + maxScroll));
            int thumbY = scrollContentTop + (int) ((long) panelScrollY * (viewH - thumbH) / maxScroll);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0x88AAAACC);
        }

        g.enableScissor(cx - SEC_PAD, scrollContentTop, cx + cw + SEC_PAD, scrollContentBottom);
        for (int i = 0; i < footerStartIndex && i < this.renderables.size(); i++) {
            if (this.renderables.get(i) instanceof AbstractWidget w) w.render(g, mx, my, partial);
        }
        g.disableScissor();
        for (int i = footerStartIndex; i < this.renderables.size(); i++) {
            if (this.renderables.get(i) instanceof AbstractWidget w) w.render(g, mx, my, partial);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.flush();

        if (visibilityDropdownOpen && !collapsedSections.contains(Section.VISIBILITY_PREREQS)) {
            int dropH = VISIBILITIES.length * (FIELD_H + 1);
            int dropY = visRowY + FIELD_H + 1;
            g.fill(cx, dropY, cx + visW, dropY + dropH, C_PANEL);
            drawBorder(g, cx, dropY, visW, dropH, C_ACCENT);
            for (int i = 0; i < VISIBILITIES.length; i++) {
                int ry = dropY + i * (FIELD_H + 1);
                boolean hov = mx >= cx && mx < cx + visW && my >= ry && my < ry + FIELD_H + 1;
                if (hov) g.fill(cx + 1, ry, cx + visW - 1, ry + FIELD_H + 1, 0xFF1E1E2A);
                ChroniclesUIKit.drawString(g, font, "§f" + VISIBILITIES[i].name(), cx + 5, ry + 3,
                        hov ? C_TEXT : C_TEXT_DIM, false);
            }
        }

        if (chapterDropdownOpen && !collapsedSections.contains(Section.BASIC_INFO)) {
            List<String> cats = buildExistingCategories();
            int dropW = catW;
            int dropH = Math.max(FIELD_H + 1, cats.size() * (FIELD_H + 1));
            int dropY = catRowY + FIELD_H + 1;
            g.fill(cx, dropY, cx + dropW, dropY + dropH, C_PANEL);
            drawBorder(g, cx, dropY, dropW, dropH, C_ACCENT);
            if (cats.isEmpty()) {
                ChroniclesUIKit.drawString(g, font, "§fNo categories yet", cx + 5, dropY + 3, C_TEXT_FAINT, false);
            } else {
                for (int i = 0; i < cats.size(); i++) {
                    int ry = dropY + i * (FIELD_H + 1);
                    boolean hov = mx >= cx && mx < cx + dropW && my >= ry && my < ry + FIELD_H + 1;
                    if (hov) g.fill(cx + 1, ry, cx + dropW - 1, ry + FIELD_H + 1, 0xFF1E1E2A);
                    ChroniclesUIKit.drawString(g, font, "§f" + cats.get(i), cx + 5, ry + 3, hov ? C_TEXT : C_TEXT_DIM,
                            false);
                }
            }
        }

        if (cancelConfirmOpen) {
            int pw = 260, ph = 70;
            int px = (width - pw) / 2, py = (height - ph) / 2;
            g.fill(0, 0, width, height, 0x88000000);
            g.fill(px, py, px + pw, py + ph, C_PANEL);
            drawBorder(g, px, py, pw, ph, C_ACCENT);
            ChroniclesUIKit.drawCenteredString(g, font, "§fDiscard unsaved changes?", px + pw / 2, py + 10, C_TEXT);
            int byY = py + ph - 26;
            int bw = (pw - 30) / 2;
            confirmDiscardX = px + 10;
            confirmKeepX = px + pw - 10 - bw;
            confirmBtnY = byY;
            confirmBtnW = bw;
            boolean hovDiscard = mx >= confirmDiscardX && mx < confirmDiscardX + bw && my >= byY && my < byY + 18;
            boolean hovKeep = mx >= confirmKeepX && mx < confirmKeepX + bw && my >= byY && my < byY + 18;
            g.fill(confirmDiscardX, byY, confirmDiscardX + bw, byY + 18, hovDiscard ? 0xFF3A1A1A : 0xFF241010);
            drawBorder(g, confirmDiscardX, byY, bw, 18, C_BORDER);
            ChroniclesUIKit.drawCenteredString(g, font, "§cDiscard", confirmDiscardX + bw / 2, byY + 5, 0xFFFF6666);
            g.fill(confirmKeepX, byY, confirmKeepX + bw, byY + 18, hovKeep ? 0xFF1A3A1A : 0xFF102410);
            drawBorder(g, confirmKeepX, byY, bw, 18, C_BORDER);
            ChroniclesUIKit.drawCenteredString(g, font, "§aKeep editing", confirmKeepX + bw / 2, byY + 5, C_OK);
        }

        g.pose().popPose();
    }

    private int confirmDiscardX, confirmKeepX, confirmBtnY, confirmBtnW;

    private String sectionSummary(Section sec) {
        return switch (sec) {
            case BASIC_INFO -> basicInfoSummary();
            case POSITION_SIZE -> positionSizeSummary();
            case TASKS_REWARDS -> tasksRewardsSummary();
            case EMERGENCY -> emergencySummary();
            case VARIANTS -> variantsSummary();
            case VISIBILITY_PREREQS -> visibilityPrereqsSummary();
            case REWARDS_REPEATS -> rewardsRepeatsSummary();
            case AUDIO -> audioSummary();
            case ADVANCED -> advancedSummary();
            case RAW -> "";
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (cancelConfirmOpen) {
            if (btn == 0) {
                if (mx >= confirmDiscardX && mx < confirmDiscardX + confirmBtnW && my >= confirmBtnY &&
                        my < confirmBtnY + 18) {
                    cancelConfirmOpen = false;
                    if (minecraft != null) minecraft.setScreen(parent);
                    return true;
                }
                if (mx >= confirmKeepX && mx < confirmKeepX + confirmBtnW && my >= confirmBtnY &&
                        my < confirmBtnY + 18) {
                    cancelConfirmOpen = false;
                    return true;
                }
            }
            return true;
        }
        if (btn == 0) {
            for (SectionHeaderRect r : sectionHeaderRects) {

                if (r.y() + r.h() <= scrollContentTop || r.y() >= scrollContentBottom) continue;
                if (mx >= cx - SEC_PAD && mx < cx + cw + SEC_PAD && my >= r.y() && my < r.y() + r.h()) {

                    if (collapsedSections.contains(r.section())) collapsedSections.remove(r.section());
                    else collapsedSections.add(r.section());
                    visibilityDropdownOpen = false;
                    chapterDropdownOpen = false;
                    rebuildWidgets();
                    return true;
                }
            }
            if (visibilityDropdownOpen && !collapsedSections.contains(Section.VISIBILITY_PREREQS)) {
                int dropY = visRowY + FIELD_H + 1;
                int dropH = VISIBILITIES.length * (FIELD_H + 1);
                if (mx >= cx && mx < cx + visW && my >= dropY && my < dropY + dropH) {
                    for (int i = 0; i < VISIBILITIES.length; i++) {
                        int ry = dropY + i * (FIELD_H + 1);
                        if (my >= ry && my < ry + FIELD_H + 1) {
                            cachedVisibility = VISIBILITIES[i];
                            break;
                        }
                    }
                    visibilityDropdownOpen = false;
                    rebuildWidgets();
                    return true;
                }

                visibilityDropdownOpen = false;
            }
            if (chapterDropdownOpen && !collapsedSections.contains(Section.BASIC_INFO)) {
                List<String> cats = buildExistingCategories();
                int dropW = catW;
                int dropY = catRowY + FIELD_H + 1;
                int dropH = Math.max(FIELD_H + 1, cats.size() * (FIELD_H + 1));
                if (mx >= cx && mx < cx + dropW && my >= dropY && my < dropY + dropH) {
                    for (int i = 0; i < cats.size(); i++) {
                        int ry = dropY + i * (FIELD_H + 1);
                        if (my >= ry && my < ry + FIELD_H + 1) {
                            cachedChapter = cats.get(i);
                            if (chapterBox != null) chapterBox.setValue(cachedChapter);
                            break;
                        }
                    }
                    chapterDropdownOpen = false;
                    return true;
                }
                chapterDropdownOpen = false;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= cx - SEC_PAD && mx < cx + cw + SEC_PAD && my >= scrollContentTop && my < scrollContentBottom) {
            panelScrollY = Math.max(0, panelScrollY - (int) Math.round(delta * 16));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (cancelConfirmOpen) {
                cancelConfirmOpen = false;
                return true;
            }
            if (!visibilityDropdownOpen && !chapterDropdownOpen) {
                if (hasUnsavedChanges()) {
                    cancelConfirmOpen = true;
                } else if (minecraft != null) {
                    minecraft.setScreen(parent);
                }
                return true;
            }
        }
        if (key == 32 && !cancelConfirmOpen) {
            for (SectionHeaderRect r : sectionHeaderRects) {
                if (r.y() + r.h() <= scrollContentTop || r.y() >= scrollContentBottom) continue;
                if (lastMouseX >= cx - SEC_PAD && lastMouseX < cx + cw + SEC_PAD && lastMouseY >= r.y() &&
                        lastMouseY < r.y() + r.h()) {
                    if (collapsedSections.contains(r.section())) collapsedSections.remove(r.section());
                    else collapsedSections.add(r.section());
                    rebuildWidgets();
                    return true;
                }
            }
        }
        visibilityDropdownOpen = false;
        chapterDropdownOpen = false;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        LangSyncScheduler.flushNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void repositionElements() {
        rebuildWidgets();
    }

    private String buildCurrentSnbt() {
        try {
            String id = cachedId.trim().toLowerCase().replaceAll("[^a-z0-9/._-]", "");
            if (id.isEmpty()) id = "_unsaved_";
            String chapter = cachedChapter.trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
            if (chapter.isEmpty()) chapter = "MAIN";
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("id", id);
            tag.putString("title", cachedTitle.trim());
            tag.putString("description", cachedDesc.trim());
            if (!cachedSubtitle.isBlank()) tag.putString("subtitle", cachedSubtitle.trim());
            tag.putString("chapter", chapter);
            tag.putString("shape", cachedShape);
            if (!cachedShapeTexture.isBlank()) tag.putString("shape_texture", cachedShapeTexture);
            if (!cachedBackgroundType.isBlank()) tag.putString("background", cachedBackgroundType);
            tag.putString("visibility", cachedVisibility.name());
            if (cachedDisabledBlocksChildren) tag.putBoolean("disabled_blocks_children", true);
            if (!cachedEnableIf.isBlank()) tag.putString("enable_if", cachedEnableIf.trim());
            tag.putString("parent", cachedPrerequisites.isEmpty() ? "none" : cachedPrerequisites.get(0).getId()
                    .getPath());
            if (cachedRequireAll != null) tag.putBoolean("require_all_prereqs", cachedRequireAll);
            if (!cachedChapterPrereqs.isEmpty()) {
                net.minecraft.nbt.ListTag chapterList = new net.minecraft.nbt.ListTag();
                for (String c : cachedChapterPrereqs) chapterList.add(net.minecraft.nbt.StringTag.valueOf(c));
                tag.put("chapter_prereqs", chapterList);
            }
            if (!cachedCategoryPrereqs.isEmpty()) {
                net.minecraft.nbt.ListTag categoryList = new net.minecraft.nbt.ListTag();
                for (String c : cachedCategoryPrereqs) categoryList.add(net.minecraft.nbt.StringTag.valueOf(c));
                tag.put("category_prereqs", categoryList);
            }
            if (cachedTaskMinCount > 0) tag.putInt("task_min_count", cachedTaskMinCount);
            tag.putInt("positionX", cachedPosX);
            tag.putInt("positionY", cachedPosY);
            tag.putBoolean("position_is_center", true);
            if (cachedRepeatMode != QuestNode.RepeatMode.NONE) {
                tag.putString("repeat_mode", cachedRepeatMode.name());
                if (cachedRepeatMode == QuestNode.RepeatMode.COOLDOWN)
                    tag.putInt("repeat_cooldown_hours", cachedRepeatCooldownHours);
            }
            if (cachedHideDepLine) tag.putBoolean("hide_dep_line", true);
            if (cachedAutoClaimRewards) tag.putBoolean("auto_claim_rewards", true);
            if (cachedRewardChoice) {
                tag.putBoolean("reward_choice", true);
                if (cachedRewardChoiceCount != 1) tag.putInt("reward_choice_count", cachedRewardChoiceCount);
            }
            if (cachedNodeSize != QuestNode.NodeSize.NORMAL) tag.putString("node_size", cachedNodeSize.name());
            if (cachedSizeOverridePx > 0) tag.putInt("node_size_px", cachedSizeOverridePx);
            if (!cachedDevNotes.isBlank()) tag.putString("dev_notes", cachedDevNotes.trim());
            if (!cachedPreviewMachineId.isBlank())
                tag.putString("preview_machine_id", cachedPreviewMachineId.trim());
            if (!cachedExternalScreenId.isBlank())
                tag.putString("external_screen_id", cachedExternalScreenId.trim());
            if (!cachedUnlockSoundId.isBlank()) tag.putString("unlock_sound", cachedUnlockSoundId.trim());
            if (!cachedCompleteSoundId.isBlank()) tag.putString("complete_sound", cachedCompleteSoundId.trim());
            if (!cachedIconItemId.isBlank()) tag.putString("icon_item", cachedIconItemId.trim());
            if (editingNode != null && !editingNode.getTasks().isEmpty()) {
                net.minecraft.nbt.ListTag tl = new net.minecraft.nbt.ListTag();
                for (QuestTask t : editingNode.getTasks()) {
                    net.minecraft.nbt.CompoundTag tt = t.serializeNBT();
                    tt.putString("task_id", t.getTaskId().toString());
                    tl.add(tt);
                }
                tag.put("tasks", tl);
            }
            return tag.toString();
        } catch (Exception e) {
            return "{error: \"" + e.getMessage() + "\"}";
        }
    }

    private void save() {
        String id = cachedId.trim().toLowerCase().replaceAll("[^a-z0-9/._-]", "");
        String title = cachedTitle.trim();
        String desc = cachedDesc.trim();
        String chapter = cachedChapter.trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
        if (chapter.isEmpty()) chapter = "MAIN";

        if (id.isEmpty() || title.isEmpty()) {
            statusMsg = id.isEmpty() ? "Title is required (ID auto-generates from it)" : "Title is required";
            statusIsErr = true;
            return;
        }

        try {
            ResourceLocation questId = ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", id);

            ResourceLocation parentLoc = cachedPrerequisites.isEmpty() ? null : cachedPrerequisites.get(0).getId();
            boolean idChanged = editingNode != null && !editingNode.getId().equals(questId);

            if (editingNode != null && !idChanged) {

                editingNode.setTitle(Component.literal(title));
                editingNode.setDescription(Component.literal(desc));
                persistLangOverride(questId, "title", title);
                persistLangOverride(questId, "description", desc);
                persistLangOverride(questId, "subtitle", cachedSubtitle.trim());
                editingNode.setChapter(chapter);
                editingNode.setShapeType(cachedShape);
                editingNode.setLabelPosition(cachedLabelPosition);
                editingNode.setShapeTexture(cachedShapeTexture);
                editingNode.setBackgroundType(cachedBackgroundType);
                editingNode.setSubtitle(cachedSubtitle.trim());
                editingNode.setVisibility(cachedVisibility);
                editingNode.setEnableIf(cachedEnableIf);
                editingNode.setDisabledBlocksChildren(cachedDisabledBlocksChildren);
                editingNode.setRequireAllPrerequisites(cachedRequireAll);
                editingNode.setChapterPrereqs(cachedChapterPrereqs);
                editingNode.setCategoryPrereqs(cachedCategoryPrereqs);
                editingNode.setTaskMinCount(cachedTaskMinCount);
                editingNode.setRepeatMode(cachedRepeatMode);
                if (cachedRepeatMode == QuestNode.RepeatMode.COOLDOWN)
                    editingNode.setRepeatCooldownHours(cachedRepeatCooldownHours);
                editingNode.setHideDepLine(cachedHideDepLine);
                editingNode.setAutoClaimRewards(cachedAutoClaimRewards);
                editingNode.setRewardChoice(cachedRewardChoice);
                editingNode.setRewardChoiceCount(cachedRewardChoiceCount);
                editingNode.setNodeSize(cachedNodeSize);
                if (cachedSizeOverridePx > 0) editingNode.setSizeOverridePx(cachedSizeOverridePx);
                editingNode.setDevNotes(cachedDevNotes.trim());
                editingNode.setPreviewMachineId(cachedPreviewMachineId.trim());
                editingNode.setExternalScreenId(cachedExternalScreenId.trim());
                editingNode.setUnlockSoundId(cachedUnlockSoundId.trim());
                editingNode.setCompleteSoundId(cachedCompleteSoundId.trim());
                editingNode.setAudio(buildAudio());
                editingNode.setCustomPosition(cachedPosX, cachedPosY);
                if (!cachedIconItemId.isBlank()) editingNode.setIconItemById(cachedIconItemId.trim());

                for (QuestNode existingPrereq : new ArrayList<>(editingNode.getPrerequisites())) {
                    if (!cachedPrerequisites.contains(existingPrereq)) editingNode.removePrerequisite(existingPrereq);
                }
                for (QuestNode newPrereq : cachedPrerequisites) editingNode.addPrerequisite(newPrereq);

                for (QuestNode candidate : QuestTreeRegistry.getAllQuests().values()) {
                    if (candidate != editingNode && candidate.getChildren().contains(editingNode) &&
                            !candidate.getId().equals(parentLoc)) {
                        candidate.removeChild(editingNode);
                    }
                }
                QuestTreeRegistry.injectDynamicQuestNode(editingNode, parentLoc);
                QuestFileSaver.saveOneQuestToDisk(editingNode);
                lastSavedNode = editingNode;
            } else {
                QuestNode node = new QuestNode(questId, Component.literal(title), Component.literal(desc));
                node.setChapter(chapter);
                node.setShapeType(cachedShape);
                node.setLabelPosition(cachedLabelPosition);
                node.setShapeTexture(cachedShapeTexture);
                node.setBackgroundType(cachedBackgroundType);
                node.setSubtitle(cachedSubtitle.trim());
                node.setVisibility(cachedVisibility);
                node.setEnableIf(cachedEnableIf);
                node.setDisabledBlocksChildren(cachedDisabledBlocksChildren);
                node.setRequireAllPrerequisites(cachedRequireAll);
                node.setChapterPrereqs(cachedChapterPrereqs);
                node.setCategoryPrereqs(cachedCategoryPrereqs);
                node.setTaskMinCount(cachedTaskMinCount);
                node.setRepeatMode(cachedRepeatMode);
                if (cachedRepeatMode == QuestNode.RepeatMode.COOLDOWN)
                    node.setRepeatCooldownHours(cachedRepeatCooldownHours);
                node.setHideDepLine(cachedHideDepLine);
                node.setAutoClaimRewards(cachedAutoClaimRewards);
                node.setRewardChoice(cachedRewardChoice);
                node.setRewardChoiceCount(cachedRewardChoiceCount);
                node.setNodeSize(cachedNodeSize);
                if (cachedSizeOverridePx > 0) node.setSizeOverridePx(cachedSizeOverridePx);
                node.setDevNotes(cachedDevNotes.trim());
                node.setPreviewMachineId(cachedPreviewMachineId.trim());
                node.setExternalScreenId(cachedExternalScreenId.trim());
                node.setUnlockSoundId(cachedUnlockSoundId.trim());
                node.setCompleteSoundId(cachedCompleteSoundId.trim());
                node.setAudio(buildAudio());
                node.setCustomPosition(cachedPosX, cachedPosY);
                if (!cachedIconItemId.isBlank()) node.setIconItemById(cachedIconItemId.trim());

                if (editingNode != null) {

                    for (QuestNode c : editingNode.getChildren()) node.addChild(c);
                    for (QuestTask t : editingNode.getTasks()) node.addTask(t);
                    for (QuestReward r : editingNode.getRewards()) node.addReward(r);
                    for (QuestNode.QuestVariant v : editingNode.getVariants()) node.addVariant(v);
                    node.setShared(editingNode.isShared());
                    node.setPooledProgress(editingNode.isPooledProgress());
                    if (editingNode.getOptionalPrereqMinCount() != null)
                        node.setOptionalPrereqMinCount(editingNode.getOptionalPrereqMinCount());
                    node.setEmergencyKit(editingNode.getEmergencyKit());
                    for (QuestNode p : cachedPrerequisites) {
                        node.addPrerequisite(p);
                        boolean hadFlags = editingNode.getPrerequisites().contains(p);
                        if (hadFlags) {
                            node.setPrereqRequired(p.getId(), editingNode.isPrereqRequired(p.getId()));
                            node.setPrereqForbidden(p.getId(), editingNode.isPrereqForbidden(p.getId()));
                            node.setPrereqLink(p.getId(), editingNode.isPrereqLink(p.getId()));
                            node.setPrereqCosmetic(p.getId(), editingNode.isPrereqCosmetic(p.getId()));
                            node.setPrereqLineShape(p.getId(), editingNode.getPrereqLineShape(p.getId()));
                            node.setPrereqLineVisual(p.getId(), editingNode.getPrereqLineVisual(p.getId()));
                            node.setPrereqLineSpeed(p.getId(), editingNode.getPrereqLineSpeed(p.getId()));
                            node.setPrereqLineArrow(p.getId(), editingNode.getPrereqLineArrow(p.getId()));
                        }
                    }

                    QuestTreeRegistry.removeQuest(editingNode.getId());
                    QuestFileSaver.deleteQuestFiles(editingNode);
                } else if (pendingWorkingNode != null) {

                    for (QuestTask t : pendingWorkingNode.getTasks()) node.addTask(t);
                    for (QuestReward r : pendingWorkingNode.getRewards()) node.addReward(r);
                    for (QuestNode.QuestVariant v : pendingWorkingNode.getVariants()) node.addVariant(v);
                    node.setEmergencyKit(pendingWorkingNode.getEmergencyKit());
                }

                if (editingNode == null) {
                    for (QuestNode p : cachedPrerequisites) node.addPrerequisite(p);
                }

                QuestTreeRegistry.injectDynamicQuestNode(node, parentLoc);
                QuestFileSaver.saveOneQuestToDisk(node);
                lastSavedNode = node;
            }

            LangSyncScheduler.markDirty();

            statusMsg = "Saved!";
            statusIsErr = false;

        } catch (Exception e) {
            statusMsg = "Save failed: " + e.getMessage();
            statusIsErr = true;
        }
    }

    private void persistLangOverride(ResourceLocation questId, String field, String value) {
        if (value.isEmpty() || minecraft == null) return;
        String key = "phoenix_chronicles.quest." + questId.getPath().replace('/', '.') + "." + field;
        java.nio.file.Path base = minecraft.gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        QuestLangRegistry.writeKey(base, key, value);
    }

    private List<String> buildExistingCategories() {
        List<String> cats = new ArrayList<>();
        cats.add("MAIN");
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            String c = n.getChapter();
            if (c != null && !cats.contains(c)) cats.add(c);
        }

        try {
            Path f = ChronicleOverviewScreen.chaptersFile();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String cat = line.trim().toUpperCase();
                    if (!cat.isEmpty() && !cats.contains(cat)) cats.add(cat);
                }
            }
        } catch (IOException ignored) {}

        for (CategoryDefinition cd : CategoryRegistry
                .getCategories()) {
            for (String chap : cd.chapters()) {
                if (chap != null && !chap.isBlank() && !cats.contains(chap.toUpperCase())) cats.add(chap.toUpperCase());
            }
        }
        return cats;
    }

    protected void rebuildWidgets() {
        clearWidgets();
        init();
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        ChroniclesUIKit.drawBorder(g, x, y, w, h, color);
    }
}
