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
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

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
    private static final int SEC_PAD = 6;      
    private static final int SEC_HEADER_H = 18; 
    private static final int SEC_HEADER_GAP = 6;

    private enum Section {
        BASIC_INFO("Basic Info"),
        POSITION_SIZE("Position & Size"),
        TASKS_REWARDS("Tasks & Rewards"),
        VARIANTS("Variants"),
        VISIBILITY_PREREQS("Visibility & Prerequisites"),
        REWARDS_REPEATS("Rewards & Repeats"),
        ADVANCED("Advanced"),
        RAW("Raw SNBT Preview");

        final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private final Set<Section> collapsedSections = new HashSet<>(List.of(
            Section.TASKS_REWARDS, Section.VARIANTS, Section.REWARDS_REPEATS, Section.ADVANCED, Section.RAW));

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
            new ShapeMeta("SQUARE", "â– "), new ShapeMeta("CIRCLE", "â—"),
            new ShapeMeta("DIAMOND", "â—†"), new ShapeMeta("HEXAGON", "â¬¡"),
            new ShapeMeta("TRIANGLE", "â–²"), new ShapeMeta("STAR", "â˜…"),
            new ShapeMeta("PENTAGON", "â¬ "), new ShapeMeta("SHIELD", "â–"),
            new ShapeMeta("CROSS", "âœš"), new ShapeMeta("CUSTOM", "â–©"),
    };

    private final Screen parent;
    private final QuestNode editingNode;

    private String cachedTitle = "";
    private String cachedDesc = "";
    private String cachedSubtitle = "";
    private String cachedCategory = "MAIN";
    private String cachedIconItemId = "";
    private String cachedShape = "SQUARE";
    
    private String cachedShapeTexture = "";
    private QuestNode.Visibility cachedVisibility = QuestNode.Visibility.NORMAL;
    private String cachedEnableIf = "";
    
    private Boolean cachedRequireAll = null;
    private boolean cachedDisabledBlocksChildren = false;
    private QuestNode cachedParent = null;
    private int cachedTaskMinCount = 0;
    private String cachedId = "";
    private boolean idManuallySet = false;
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
    private int cachedPosX = 40;
    private int cachedPosY = 70;

    private QuestNode pendingWorkingNode = null;

    private EditBox titleBox, descBox, subtitleBox, categoryBox, idBox, posXBox, posYBox;

    private boolean visibilityDropdownOpen = false;
    private boolean categoryDropdownOpen = false;
    private static final QuestNode.Visibility[] VISIBILITIES = QuestNode.Visibility.values();

    private String statusMsg = "";
    private boolean statusIsErr = false;

    private int cx, cw;
    private int shapeRowY, shapeColW, shapeX, iconColW;
    private int visRowY, visW;
    private int catRowY, catW;
    private int repeatRowY, repeatBtnW;
    private int idRowLabelY;

    public QuestCreatorScreen(Screen parent) {
        super(Component.literal("New Quest"));
        this.parent = parent;
        this.editingNode = null;
    }

    public QuestCreatorScreen(Screen parent, int canvasX, int canvasY) {
        super(Component.literal("New Quest"));
        this.parent = parent;
        this.editingNode = null;
        this.cachedPosX = canvasX;
        this.cachedPosY = canvasY;
    }

    public QuestCreatorScreen(Screen parent, QuestNode editingNode) {
        super(Component.literal("Edit Quest"));
        this.parent = parent;
        this.editingNode = editingNode;

        cachedId = editingNode.getId().getPath();

        cachedTitle = editingNode.getTitleRaw().getString();
        cachedDesc = editingNode.getDescriptionRaw().getString();
        cachedSubtitle = editingNode.getSubtitleRaw() != null ? editingNode.getSubtitleRaw() : "";
        cachedCategory = editingNode.getCategory();
        cachedIconItemId = editingNode.getIconItemId();
        cachedShape = editingNode.getShapeType() != null ? editingNode.getShapeType() : "SQUARE";
        cachedShapeTexture = editingNode.getShapeTexture() != null ? editingNode.getShapeTexture() : "";
        cachedVisibility = editingNode.getVisibility() != null ? editingNode.getVisibility() :
                QuestNode.Visibility.NORMAL;
        cachedEnableIf = editingNode.getEnableIf() != null ? editingNode.getEnableIf() : "";
        cachedRequireAll = editingNode.getRequireAllPrerequisites();
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
        cachedPosX = editingNode.getCustomX();
        cachedPosY = editingNode.getCustomY();
        if (!editingNode.getPrerequisites().isEmpty())
            cachedParent = editingNode.getPrerequisites().get(0);
        idManuallySet = true;
        initialized = true;
    }

    @Override
    protected void init() {
        ChroniclesTheme t = ChroniclesTheme.current();
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

        scrollContentTop = HEADER_H + 4;
        scrollContentBottom = height - FOOTER_H - 4;

        int y = scrollContentTop + SEC_PAD - panelScrollY;
        int firstWidgetIndex = 0;

        y = buildSection(Section.BASIC_INFO, y, this::buildBasicInfo, this::basicInfoSummary);
        y = buildSection(Section.POSITION_SIZE, y, this::buildPositionSize, this::positionSizeSummary);
        y = buildSection(Section.TASKS_REWARDS, y, this::buildTasksRewards, this::tasksRewardsSummary);
        y = buildSection(Section.VARIANTS, y, this::buildVariants, this::variantsSummary);
        y = buildSection(Section.VISIBILITY_PREREQS, y, this::buildVisibilityPrereqs, this::visibilityPrereqsSummary);
        y = buildSection(Section.REWARDS_REPEATS, y, this::buildRewardsRepeats, this::rewardsRepeatsSummary);
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
        addRenderableWidget(Button.builder(Component.literal("§aâœ“ Save Quest"),
                b -> save()).bounds(cx, fbtnY, halfW, 16)
                .tooltip(Tooltip.create(Component.literal("Write quest to disk and register it live"))).build());
        addRenderableWidget(Button.builder(Component.literal("§7< Done"), b -> {
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(cx + halfW + COL_GAP, fbtnY, halfW, 16)
                .tooltip(Tooltip.create(Component.literal("Discard unsaved changes and return"))).build());
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
        labels.add(new LabelEntry(cx, y, "§8Title", C_TEXT_FAINT));
        titleBox = new EditBox(font, cx, rowY, cw - EDIT_W - 2, FIELD_H, Component.empty());
        titleBox.setMaxLength(64);
        titleBox.setHint(Component.literal("§8Quest title shown to players"));
        titleBox.setValue(cachedTitle);
        titleBox.setResponder(v -> {
            cachedTitle = v;
            if (!idManuallySet) {
                cachedId = v.trim().toLowerCase().replaceAll("[^a-z0-9 /._-]", "").replaceAll("\\s+", "_");
                if (idBox != null) idBox.setValue(cachedId);
            }
        });
        addRenderableWidget(titleBox);
        addRenderableWidget(Button.builder(Component.literal("§7âœŽ"),
                b -> Minecraft.getInstance().setScreen(new QuestTextInputScreen(this, "Title", cachedTitle, 64,
                        v -> {
                            cachedTitle = v;
                            if (titleBox != null) titleBox.setValue(v);
                        })))
                .bounds(cx + cw - EDIT_W, rowY, EDIT_W, FIELD_H).build());
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Description", C_TEXT_FAINT));
        descBox = new EditBox(font, cx, rowY, cw - EDIT_W - 2, FIELD_H, Component.empty());
        descBox.setMaxLength(512);
        descBox.setHint(Component.literal("§8Short description / lore text"));
        descBox.setValue(cachedDesc);
        descBox.setResponder(v -> cachedDesc = v);
        addRenderableWidget(descBox);
        addRenderableWidget(Button.builder(Component.literal("§7âœŽ"),
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
        labels.add(new LabelEntry(cx, y, "§8Category", C_TEXT_FAINT));
        labels.add(new LabelEntry(subX, y, "§8Subtitle", C_TEXT_FAINT));
        int catPickW = 16, newCatW = 32;
        int catBoxW = catColW - catPickW - 2 - newCatW - 2;
        categoryBox = new EditBox(font, cx, rowY, catBoxW, FIELD_H, Component.empty());
        categoryBox.setMaxLength(32);
        categoryBox.setHint(Component.literal("§8MAIN  CHAPTER_1  â€¦"));
        categoryBox.setValue(cachedCategory);
        categoryBox.setResponder(v -> {
            cachedCategory = v;
            categoryDropdownOpen = false;
        });
        addRenderableWidget(categoryBox);
        addRenderableWidget(Button.builder(Component.literal("§7â–¾"), b -> {
            categoryDropdownOpen = !categoryDropdownOpen;
            visibilityDropdownOpen = false;
        }).bounds(cx + catBoxW + 2, rowY, catPickW, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("§a+New"), b -> {
            categoryDropdownOpen = false;
            cachedCategory = "";
            if (categoryBox != null) {
                categoryBox.setValue("");
                categoryBox.setFocused(true);
            }
        }).bounds(cx + catBoxW + 2 + catPickW + 2, rowY, newCatW, FIELD_H).build());
        subtitleBox = new EditBox(font, subX, rowY, subW - EDIT_W - 2, FIELD_H, Component.empty());
        subtitleBox.setMaxLength(128);
        subtitleBox.setHint(Component.literal("§8Subtitleâ€¦"));
        subtitleBox.setValue(cachedSubtitle);
        subtitleBox.setResponder(v -> cachedSubtitle = v);
        addRenderableWidget(subtitleBox);
        addRenderableWidget(Button.builder(Component.literal("§7âœŽ"),
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
        labels.add(new LabelEntry(cx, y, "§8Icon", C_TEXT_FAINT));
        labels.add(new LabelEntry(sX, y, "§8Shape  §7" + cachedShape, C_TEXT_FAINT));
        net.minecraft.world.item.Item iconItem = cachedIconItemId.isBlank() ? null :
                ForgeRegistries.ITEMS.getValue(new ResourceLocation(cachedIconItemId));
        String iconBtnLabel = (iconItem != null && iconItem != net.minecraft.world.item.Items.AIR) ?
                "§f" + new net.minecraft.world.item.ItemStack(iconItem).getHoverName().getString() : "§8Pick iconâ€¦";
        addRenderableWidget(Button.builder(Component.literal(iconBtnLabel), b -> {
            if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                cachedIconItemId = id != null ? id.toString() : "";
                rebuildWidgets();
            }));
        }).bounds(cx, rowY, iconW - EDIT_W - 2, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("§cÃ—"), b -> {
            cachedIconItemId = "";
            rebuildWidgets();
        }).bounds(cx + iconW - EDIT_W, rowY, EDIT_W, FIELD_H).build());
        int shapeSlot = shapeW / SHAPES.length;
        for (int i = 0; i < SHAPES.length; i++) {
            ShapeMeta sm = SHAPES[i];
            boolean sel = sm.id().equals(cachedShape);
            addRenderableWidget(Button.builder(
                    Component.literal((sel ? "§d" : "§7") + sm.glyph()),
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

        if (editingNode != null) {
            rowY = y + LABEL_H + LABEL_GAP;
            labels.add(new LabelEntry(cx, y, "§8Appearance", C_TEXT_FAINT));
            addRenderableWidget(Button.builder(Component.literal("§7ðŸ”” Design Pop-Upâ€¦"),
                    b -> Minecraft.getInstance().setScreen(new ToastDesignerScreen(this, editingNode)))
                    .bounds(cx, rowY, cw, FIELD_H)
                    .tooltip(Tooltip.create(Component.literal(
                            "Customize this quest's unlock/completion toast popup appearance")))
                    .build());
            y = rowY + FIELD_H;
        }
        return y;
    }

    private String basicInfoSummary() {
        return cachedTitle.isBlank() ? "§8(untitled)" : "§7" + cachedTitle;
    }

    private int buildPositionSize(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Canvas Position X/Y", C_TEXT_FAINT));
        int halfPosW = (cw - COL_GAP) / 2;
        posXBox = new EditBox(font, cx, rowY, halfPosW, FIELD_H, Component.empty());
        posXBox.setMaxLength(6);
        posXBox.setHint(Component.literal("§8X"));
        posXBox.setValue(String.valueOf(cachedPosX));
        posXBox.setResponder(v -> {
            try {
                cachedPosX = Integer.parseInt(v.trim());
            } catch (Exception ignored) {}
        });
        addRenderableWidget(posXBox);
        posYBox = new EditBox(font, cx + halfPosW + COL_GAP, rowY, halfPosW, FIELD_H, Component.empty());
        posYBox.setMaxLength(6);
        posYBox.setHint(Component.literal("§8Y"));
        posYBox.setValue(String.valueOf(cachedPosY));
        posYBox.setResponder(v -> {
            try {
                cachedPosY = Integer.parseInt(v.trim());
            } catch (Exception ignored) {}
        });
        addRenderableWidget(posYBox);
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Node size", C_TEXT_FAINT));
        String sizeLabel = cachedSizeOverridePx > 0 ? "§bâ—† Size: " + cachedSizeOverridePx + "px (custom)" :
                switch (cachedNodeSize) {
                    case TINY -> "§8â—¦ Size: Tiny";
                    case SMALL -> "§8â—¦ Size: Small";
                    case LARGE -> "§eâ— Size: Large";
                    case HUGE -> "§eâ— Size: Huge";
                    default -> "§7â€¢ Size: Normal";
                };
        addRenderableWidget(Button.builder(Component.literal(sizeLabel), b -> {
            QuestNode.NodeSize[] vals = QuestNode.NodeSize.values();
            cachedNodeSize = vals[(cachedNodeSize.ordinal() + 1) % vals.length];
            cachedSizeOverridePx = 0; 
            rebuildWidgets();
        }).bounds(cx, rowY, cw, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(
                        "Node size on the quest canvas (Tiny=14px / Small=18px / Normal=32px / Large=48px / Huge=64px).\n" +
                                "For freeform pixel control, use the canvas's own right-click â†’ \"Resize (scroll + drag)â€¦\" instead - " +
                                "clicking this cycles through the 5 presets and clears any custom size.")))
                .build());
        y = rowY + FIELD_H;
        return y;
    }

    private String positionSizeSummary() {
        return "§7(" + cachedPosX + ", " + cachedPosY + ")";
    }

    private int buildTasksRewards(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        int taskCount = editingNode != null ? editingNode.getTasks().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getTasks().size() : 0);
        int rewardCount = editingNode != null ? editingNode.getRewards().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getRewards().size() : 0);
        labels.add(new LabelEntry(cx, y,
                "§8" + taskCount + " task(s)  Â·  " + rewardCount + " reward(s)", C_TEXT_FAINT));
        addRenderableWidget(Button.builder(Component.literal("§7âŠž Open Tasks & Rewards Editor"), b -> {
            categoryDropdownOpen = false;
            visibilityDropdownOpen = false;
            Minecraft.getInstance().setScreen(new TaskRewardEditorScreen(this, resolveWorkingNode()));
        }).bounds(cx, rowY, cw, FIELD_H).build());
        return rowY + FIELD_H;
    }

    private String tasksRewardsSummary() {
        int taskCount = editingNode != null ? editingNode.getTasks().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getTasks().size() : 0);
        return "§7" + taskCount + " task(s)";
    }

    private int buildVariants(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        int variantCount = editingNode != null ? editingNode.getVariants().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getVariants().size() : 0);
        labels.add(new LabelEntry(cx, y, "§8" + variantCount + " variant(s)", C_TEXT_FAINT));
        addRenderableWidget(Button.builder(Component.literal("§7â—ˆ Open Variants Editor"), b -> {
            categoryDropdownOpen = false;
            visibilityDropdownOpen = false;
            Minecraft.getInstance().setScreen(new VariantEditorScreen(this, resolveWorkingNode()));
        }).bounds(cx, rowY, cw, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(
                        "Pack-mode variants â€” override this quest's title/description/visibility/tasks/rewards based on a flag condition (e.g. config:pack_mode=expert)")))
                .build());
        return rowY + FIELD_H;
    }

    private String variantsSummary() {
        int variantCount = editingNode != null ? editingNode.getVariants().size() :
                (pendingWorkingNode != null ? pendingWorkingNode.getVariants().size() : 0);
        return "§7" + variantCount;
    }

    private QuestNode resolveWorkingNode() {
        if (editingNode != null) return editingNode;
        if (pendingWorkingNode == null) {
            String id = cachedId.trim().isEmpty() ? "_preview_" : cachedId.trim();
            pendingWorkingNode = new QuestNode(
                    new ResourceLocation("phoenixcore", id),
                    Component.literal(cachedTitle), Component.literal(cachedDesc));
        }
        return pendingWorkingNode;
    }

    private int buildVisibilityPrereqs(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Visibility  Â·  Prerequisite gate", C_TEXT_FAINT));
        int vw = 90;
        visRowY = y;
        visW = vw;
        addRenderableWidget(Button.builder(
                Component.literal("§7" + cachedVisibility.name() + " §8â–¾"),
                b -> {
                    visibilityDropdownOpen = !visibilityDropdownOpen;
                    categoryDropdownOpen = false;
                })
                .bounds(cx, rowY, vw, FIELD_H).build());
        boolean showBlock = cachedVisibility == QuestNode.Visibility.DISABLED;
        int blockW = showBlock ? 90 : 0;
        int prereqW = cw - vw - COL_GAP - (showBlock ? blockW + COL_GAP : 0);
        String prereqLabel;
        if (cachedRequireAll == null) {

            Boolean catDefault = net.phoenixvine.chronicles.registry.CategoryPrereqDefaults
                    .getRequireAll(cachedCategory);
            boolean effective = catDefault != null ? catDefault : true;
            prereqLabel = "§8Inherit (" + (effective ? "ALL" : "ANY") + ") §8â–¾";
        } else if (cachedRequireAll) {
            prereqLabel = "§aâœ” ALL prereqs required";
        } else {
            prereqLabel = "§eâ—‘ ANY prereq sufficient";
        }
        addRenderableWidget(Button.builder(Component.literal(prereqLabel),
                b -> {
                    
                    if (cachedRequireAll == null) cachedRequireAll = true;
                    else if (cachedRequireAll) cachedRequireAll = false;
                    else cachedRequireAll = null;
                    rebuildWidgets();
                })
                .bounds(cx + vw + COL_GAP, rowY, prereqW, FIELD_H).build());
        if (showBlock) {
            String blkLabel = cachedDisabledBlocksChildren ? "§eBlocks children" : "§8Blocks children";
            addRenderableWidget(Button.builder(Component.literal(blkLabel),
                    b -> {
                        cachedDisabledBlocksChildren = !cachedDisabledBlocksChildren;
                        rebuildWidgets();
                    })
                    .bounds(cx + vw + COL_GAP + prereqW + COL_GAP, rowY, blockW, FIELD_H).build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Task completion gate", C_TEXT_FAINT));
        boolean anyMode = cachedTaskMinCount > 0;
        String gateLabel = anyMode ? "§eâ—‘ Complete any " + cachedTaskMinCount + " task(s)" :
                "§aâœ” Complete all tasks";
        addRenderableWidget(Button.builder(Component.literal(gateLabel), b -> {
            cachedTaskMinCount = cachedTaskMinCount == 0 ? 1 : 0;
            rebuildWidgets();
        }).bounds(cx, rowY, anyMode ? cw - 50 : cw, FIELD_H).build());
        if (anyMode) {
            addRenderableWidget(Button.builder(Component.literal("§7âˆ’"), b -> {
                if (cachedTaskMinCount > 1) cachedTaskMinCount--;
                rebuildWidgets();
            }).bounds(cx + cw - 48, rowY, 22, FIELD_H).build());
            addRenderableWidget(Button.builder(Component.literal("§7+"), b -> {
                cachedTaskMinCount++;
                rebuildWidgets();
            }).bounds(cx + cw - 24, rowY, 22, FIELD_H).build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        int parentW = (int) (cw * 0.60f);
        int enableIfW = cw - parentW - COL_GAP;
        int enableIfX = cx + parentW + COL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Parent quest", C_TEXT_FAINT));
        labels.add(new LabelEntry(enableIfX, y, "§8enable_if", C_TEXT_FAINT));
        String parentLabel = cachedParent != null ? "§a" + cachedParent.getId().getPath() : "§8No parent quest";
        addRenderableWidget(Button.builder(Component.literal(parentLabel), b -> {
            categoryDropdownOpen = false;
            visibilityDropdownOpen = false;
            Minecraft.getInstance().setScreen(new ParentSelectorScreen(this, editingNode, node -> {
                cachedParent = node;
                if (node != null && (cachedCategory.equals("MAIN") || cachedCategory.isBlank()))
                    cachedCategory = node.getCategory();
                rebuildWidgets();
            }));
        }).bounds(cx, rowY, parentW - FIELD_H - 4, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("§cÃ—"), b -> {
            cachedParent = null;
            rebuildWidgets();
        }).bounds(cx + parentW - FIELD_H, rowY, FIELD_H, FIELD_H).build());
        EditBox enableIfBox = new EditBox(font, enableIfX, rowY, enableIfW, FIELD_H, Component.empty());
        enableIfBox.setMaxLength(128);
        enableIfBox.setHint(Component.literal("§8enable_ifâ€¦"));
        enableIfBox.setValue(cachedEnableIf);
        enableIfBox.setResponder(v -> cachedEnableIf = v);
        addRenderableWidget(enableIfBox);
        y = rowY + FIELD_H;
        return y;
    }

    private String visibilityPrereqsSummary() {
        return "§7" + cachedVisibility.name();
    }

    private int buildRewardsRepeats(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Repeat mode", C_TEXT_FAINT));
        repeatRowY = rowY;
        boolean hasCooldown = cachedRepeatMode == QuestNode.RepeatMode.COOLDOWN;
        int repeatBtnWLocal = hasCooldown ? (int) (cw * 0.50f) : cw;
        repeatBtnW = repeatBtnWLocal;
        String repeatIcon = switch (cachedRepeatMode) {
            case NONE -> "§8âŠ˜ One-time  §8â–¸";
            case DAILY -> "§bâ˜€ Daily  §8â–¸";
            case COOLDOWN -> "§eâ± Cooldown  §8â–¸";
            case INFINITE -> "§aâˆž Infinite  §8â–¸";
        };
        addRenderableWidget(Button.builder(Component.literal(repeatIcon), b -> {
            QuestNode.RepeatMode[] modes = QuestNode.RepeatMode.values();
            cachedRepeatMode = modes[(cachedRepeatMode.ordinal() + 1) % modes.length];
            rebuildWidgets();
        }).bounds(cx, rowY, repeatBtnWLocal, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(
                        "NONE = one-time only  Â·  DAILY = resets at midnight  Â·  COOLDOWN = custom wait  Â·  INFINITE = repeats immediately")))
                .build());
        if (hasCooldown) {
            int coolW = cw - repeatBtnWLocal - COL_GAP;
            int coolX = cx + repeatBtnWLocal + COL_GAP;
            labels.add(new LabelEntry(coolX + 22, y, "§8Cooldown hours", C_TEXT_FAINT));
            addRenderableWidget(Button.builder(Component.literal("§7âˆ’"), b -> {
                if (cachedRepeatCooldownHours > 1) cachedRepeatCooldownHours--;
                rebuildWidgets();
            }).bounds(coolX, rowY, 18, FIELD_H).build());
            addRenderableWidget(Button.builder(Component.literal("§7+"), b -> {
                cachedRepeatCooldownHours++;
                rebuildWidgets();
            }).bounds(coolX + coolW - 18, rowY, 18, FIELD_H).build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Rewards", C_TEXT_FAINT));
        String autoLabel = cachedAutoClaimRewards ? "§aâš¡ Auto-claim rewards" : "§8âš¡ Auto-claim rewards";
        addRenderableWidget(Button.builder(Component.literal(autoLabel),
                b -> {
                    cachedAutoClaimRewards = !cachedAutoClaimRewards;
                    rebuildWidgets();
                })
                .bounds(cx, rowY, cw, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(
                        "Automatically grant rewards on completion â€” no claim button needed")))
                .build());
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Choice reward", C_TEXT_FAINT));
        String choiceLabel = cachedRewardChoice ? "§6â—ˆ Reward choice: ON" : "§8â—ˆ Reward choice: OFF";
        addRenderableWidget(Button.builder(Component.literal(choiceLabel),
                b -> {
                    cachedRewardChoice = !cachedRewardChoice;
                    rebuildWidgets();
                })
                .bounds(cx, rowY, cachedRewardChoice ? cw - 54 : cw, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(
                        "Player picks a reward from the list instead of receiving all")))
                .build());
        if (cachedRewardChoice) {
            addRenderableWidget(Button.builder(Component.literal("§7âˆ’"),
                    b -> {
                        if (cachedRewardChoiceCount > 1) {
                            cachedRewardChoiceCount--;
                            rebuildWidgets();
                        }
                    })
                    .bounds(cx + cw - 52, rowY, 16, FIELD_H).build());
            addRenderableWidget(Button.builder(Component.literal("§f" + cachedRewardChoiceCount), b -> {})
                    .bounds(cx + cw - 34, rowY, 18, FIELD_H).build());
            addRenderableWidget(Button.builder(Component.literal("§7+"),
                    b -> {
                        cachedRewardChoiceCount++;
                        rebuildWidgets();
                    })
                    .bounds(cx + cw - 14, rowY, 16, FIELD_H).build());
        }
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        int hdepW = (int) (cw * 0.48f);
        labels.add(new LabelEntry(cx, y, "§8Dependencies", C_TEXT_FAINT));
        String depToggleLabel = cachedHideDepLine ? "§eâŠ– Dependency Lines" : "§7âŠ• Dependency Lines";
        addRenderableWidget(Button.builder(Component.literal(depToggleLabel),
                b -> {
                    cachedHideDepLine = !cachedHideDepLine;
                    rebuildWidgets();
                })
                .bounds(cx, rowY, hdepW, FIELD_H)
                .tooltip(Tooltip.create(
                        Component.literal("Hide all dependency lines connected to this node on the quest canvas")))
                .build());
        if (editingNode != null) {
            int childCount = editingNode.getChildren().size();
            String childStr = childCount == 0 ? "§8No Dependents" :
                    "§7" + childCount + " quest" + (childCount == 1 ? "" : "s") + " unlock after this";
            labels.add(new LabelEntry(cx + hdepW + COL_GAP, rowY + (FIELD_H - 8) / 2 - LABEL_H - LABEL_GAP,
                    childStr, C_TEXT_DIM));
        }
        y = rowY + FIELD_H;
        return y;
    }

    private String rewardsRepeatsSummary() {
        return cachedRepeatMode == QuestNode.RepeatMode.NONE ? "§8One-time" : "§7" + cachedRepeatMode.name();
    }

    private int buildAdvanced(int y) {
        int rowY = y + LABEL_H + LABEL_GAP;
        idRowLabelY = y;
        int lockW = 36;
        int copyW = 36;
        idBox = new EditBox(font, cx, rowY, cw - lockW - copyW - 4, FIELD_H, Component.empty());
        idBox.setMaxLength(128);
        idBox.setHint(Component.literal("§8auto-generated from title"));
        idBox.setValue(cachedId);
        idBox.setResponder(v -> {
            cachedId = v;
            idManuallySet = !v.isEmpty();
        });
        addRenderableWidget(idBox);
        addRenderableWidget(Button.builder(
                Component.literal(idManuallySet ? "§cLocked" : "§aAuto"),
                b -> {
                    idManuallySet = !idManuallySet;
                    if (!idManuallySet) {
                        cachedId = cachedTitle.trim().toLowerCase()
                                .replaceAll("[^a-z0-9 /._-]", "").replaceAll("\\s+", "_");
                        if (idBox != null) idBox.setValue(cachedId);
                    }
                    rebuildWidgets();
                }).bounds(cx + cw - lockW - copyW - 2, rowY, lockW, FIELD_H).build());
        addRenderableWidget(Button.builder(
                Component.literal("§7âŽ˜"),
                b -> {
                    String fullId = "phoenixcore:" + (cachedId.isEmpty() ? "_unnamed_" : cachedId);
                    Minecraft.getInstance().keyboardHandler.setClipboard(fullId);
                })
                .bounds(cx + cw - copyW, rowY, copyW, FIELD_H)
                .tooltip(Tooltip.create(Component.literal("Copy full quest ID to clipboard"))).build());
        y = rowY + FIELD_H + ROW_GAP;

        rowY = y + LABEL_H + LABEL_GAP;
        labels.add(new LabelEntry(cx, y, "§8Dev Notes", C_TEXT_FAINT));
        EditBox devNotesBox = new EditBox(font, cx, rowY, cw, FIELD_H, Component.empty());
        devNotesBox.setMaxLength(512);
        devNotesBox.setHint(Component.literal("§8Dev notes (internal, never shown to players)â€¦"));
        devNotesBox.setValue(cachedDevNotes);
        devNotesBox.setResponder(v -> cachedDevNotes = v);
        addRenderableWidget(devNotesBox);
        y = rowY + FIELD_H;

        if (PhantasiaCompat.isAvailable()) {
            y += ROW_GAP;
            rowY = y + LABEL_H + LABEL_GAP;
            labels.add(new LabelEntry(cx, y, "§8Phantasia Preview", C_TEXT_FAINT));
            EditBox previewMachineBox = new EditBox(font, cx, rowY, cw, FIELD_H, Component.empty());
            previewMachineBox.setMaxLength(128);
            previewMachineBox
                    .setHint(Component.literal("§8Phantasia machine id shown in the quest viewer (optional)"));
            previewMachineBox.setValue(cachedPreviewMachineId);
            previewMachineBox.setResponder(v -> cachedPreviewMachineId = v);
            addRenderableWidget(previewMachineBox);
            y = rowY + FIELD_H;
        }
        return y;
    }

    private String advancedSummary() {
        return idManuallySet ? "§c" + cachedId + " (manual)" : "§7" + cachedId;
    }

    private int buildRaw(int y) {
        addRenderableWidget(Button.builder(Component.literal("§7âŽ˜ Copy SNBT"),
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
        g.fill(0, 0, width, height, C_BG);

        int panelL = cx - SEC_PAD;
        int panelR = cx + cw + SEC_PAD;
        g.enableScissor(0, scrollContentTop, width, scrollContentBottom);
        g.fill(panelL, scrollContentTop, panelR, scrollContentBottom, C_PANEL);
        drawBorder(g, panelL, scrollContentTop, panelR - panelL, scrollContentBottom - scrollContentTop, C_BORDER);

        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, 0, width, 2, C_ACCENT);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        String heading = editingNode != null ? "§fEdit Quest  §8â€” §7" + editingNode.getId().getPath() : "§fNew Quest";
        g.drawCenteredString(font, heading, width / 2, (HEADER_H - 8) / 2, C_TEXT);

        g.fill(0, height - FOOTER_H, width, height, C_HEADER);
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, C_BORDER);

        for (SectionHeaderRect r : sectionHeaderRects) {
            int hy = r.y();
            if (hy + r.h() <= scrollContentTop || hy >= scrollContentBottom) continue;
            boolean collapsed = collapsedSections.contains(r.section());
            boolean hov = mx >= panelL && mx < panelR && my >= hy && my < hy + r.h();
            g.fill(panelL, hy, panelR, hy + r.h(), hov ? C_SECTION_HEADER_HOV : C_SECTION_HEADER);
            String chevron = collapsed ? "â–¶" : "â–¼";
            g.drawString(font, "§7" + chevron + " §f" + r.section().label, cx, hy + (r.h() - 8) / 2, C_TEXT, false);
            if (collapsed) {
                String summary = sectionSummary(r.section());
                if (summary != null && !summary.isEmpty()) {
                    int sw = font.width(summary);
                    g.drawString(font, summary, panelR - SEC_PAD - sw, hy + (r.h() - 8) / 2, C_TEXT_DIM, false);
                }
            }
        }

        for (LabelEntry le : labels) {
            if (le.y() + LABEL_H <= scrollContentTop || le.y() >= scrollContentBottom) continue;
            g.drawString(font, le.text(), le.x(), le.y(), le.color(), false);
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
                            .getValue(new ResourceLocation(cachedIconItemId));
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
                g.drawString(font, "§7" + seg, cx, lineY, 0xFFAAAAAA, false);
                lineY += lineH;
                ci = end;
            }
            g.disableScissor();
        }

        g.disableScissor();

        if (!statusMsg.isEmpty()) {
            g.drawCenteredString(font, (statusIsErr ? "§c" : "§a") + statusMsg,
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
                g.drawString(font, "§7" + VISIBILITIES[i].name(), cx + 5, ry + 3, hov ? C_TEXT : C_TEXT_DIM, false);
            }
        }

        if (categoryDropdownOpen && !collapsedSections.contains(Section.BASIC_INFO)) {
            List<String> cats = buildExistingCategories();
            int dropW = catW;
            int dropH = Math.max(FIELD_H + 1, cats.size() * (FIELD_H + 1));
            int dropY = catRowY + FIELD_H + 1;
            g.fill(cx, dropY, cx + dropW, dropY + dropH, C_PANEL);
            drawBorder(g, cx, dropY, dropW, dropH, C_ACCENT);
            if (cats.isEmpty()) {
                g.drawString(font, "§8No categories yet", cx + 5, dropY + 3, C_TEXT_FAINT, false);
            } else {
                for (int i = 0; i < cats.size(); i++) {
                    int ry = dropY + i * (FIELD_H + 1);
                    boolean hov = mx >= cx && mx < cx + dropW && my >= ry && my < ry + FIELD_H + 1;
                    if (hov) g.fill(cx + 1, ry, cx + dropW - 1, ry + FIELD_H + 1, 0xFF1E1E2A);
                    g.drawString(font, "§7" + cats.get(i), cx + 5, ry + 3, hov ? C_TEXT : C_TEXT_DIM, false);
                }
            }
        }

        g.pose().popPose();
    }

    private String sectionSummary(Section sec) {
        return switch (sec) {
            case BASIC_INFO -> basicInfoSummary();
            case POSITION_SIZE -> positionSizeSummary();
            case TASKS_REWARDS -> tasksRewardsSummary();
            case VARIANTS -> variantsSummary();
            case VISIBILITY_PREREQS -> visibilityPrereqsSummary();
            case REWARDS_REPEATS -> rewardsRepeatsSummary();
            case ADVANCED -> advancedSummary();
            case RAW -> "";
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            for (SectionHeaderRect r : sectionHeaderRects) {

                if (r.y() + r.h() <= scrollContentTop || r.y() >= scrollContentBottom) continue;
                if (mx >= cx - SEC_PAD && mx < cx + cw + SEC_PAD && my >= r.y() && my < r.y() + r.h()) {
                    if (collapsedSections.contains(r.section())) collapsedSections.remove(r.section());
                    else collapsedSections.add(r.section());
                    visibilityDropdownOpen = false;
                    categoryDropdownOpen = false;
                    rebuildWidgets();
                    return true;
                }
            }
            if (visibilityDropdownOpen && !collapsedSections.contains(Section.VISIBILITY_PREREQS)) {
                int dropY = visRowY + FIELD_H + 1;
                for (int i = 0; i < VISIBILITIES.length; i++) {
                    int ry = dropY + i * (FIELD_H + 1);
                    if (mx >= cx && mx < cx + visW && my >= ry && my < ry + FIELD_H + 1) {
                        cachedVisibility = VISIBILITIES[i];
                        visibilityDropdownOpen = false;
                        rebuildWidgets();
                        return true;
                    }
                }
                visibilityDropdownOpen = false;
                rebuildWidgets();
                return true;
            }
            if (categoryDropdownOpen && !collapsedSections.contains(Section.BASIC_INFO)) {
                List<String> cats = buildExistingCategories();
                int dropW = catW;
                int dropY = catRowY + FIELD_H + 1;
                for (int i = 0; i < cats.size(); i++) {
                    int ry = dropY + i * (FIELD_H + 1);
                    if (mx >= cx && mx < cx + dropW && my >= ry && my < ry + FIELD_H + 1) {
                        cachedCategory = cats.get(i);
                        if (categoryBox != null) categoryBox.setValue(cachedCategory);
                        categoryDropdownOpen = false;
                        return true;
                    }
                }
                categoryDropdownOpen = false;
                return true;
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
        if (key == 256 && !visibilityDropdownOpen && !categoryDropdownOpen) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        visibilityDropdownOpen = false;
        categoryDropdownOpen = false;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        net.phoenixvine.chronicles.client.LangSyncScheduler.flushNow();
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
            String category = cachedCategory.trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
            if (category.isEmpty()) category = "MAIN";
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("id", id);
            tag.putString("title", cachedTitle.trim());
            tag.putString("description", cachedDesc.trim());
            if (!cachedSubtitle.isBlank()) tag.putString("subtitle", cachedSubtitle.trim());
            tag.putString("category", category);
            tag.putString("shape", cachedShape);
            if (!cachedShapeTexture.isBlank()) tag.putString("shape_texture", cachedShapeTexture);
            tag.putString("visibility", cachedVisibility.name());
            if (cachedDisabledBlocksChildren) tag.putBoolean("disabled_blocks_children", true);
            if (!cachedEnableIf.isBlank()) tag.putString("enable_if", cachedEnableIf.trim());
            tag.putString("parent", cachedParent != null ? cachedParent.getId().getPath() : "none");
            if (cachedRequireAll != null) tag.putBoolean("require_all_prereqs", cachedRequireAll);
            if (cachedTaskMinCount > 0) tag.putInt("task_min_count", cachedTaskMinCount);
            tag.putInt("positionX", cachedPosX);
            tag.putInt("positionY", cachedPosY);
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
            if (!cachedIconItemId.isBlank()) tag.putString("icon_item", cachedIconItemId.trim());
            if (editingNode != null && !editingNode.getTasks().isEmpty()) {
                net.minecraft.nbt.ListTag tl = new net.minecraft.nbt.ListTag();
                for (net.phoenixvine.chronicles.model.QuestTask t : editingNode.getTasks()) {
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
        String category = cachedCategory.trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
        if (category.isEmpty()) category = "MAIN";

        if (id.isEmpty() || title.isEmpty()) {
            statusMsg = id.isEmpty() ? "Title is required (ID auto-generates from it)" : "Title is required";
            statusIsErr = true;
            return;
        }

        try {
            ResourceLocation questId = new ResourceLocation("phoenixcore", id);
            ResourceLocation parentLoc = cachedParent != null ? cachedParent.getId() : null;
            boolean idChanged = editingNode != null && !editingNode.getId().equals(questId);

            if (editingNode != null && !idChanged) {

                editingNode.setTitle(Component.literal(title));
                editingNode.setDescription(Component.literal(desc));
                editingNode.setCategory(category);
                editingNode.setShapeType(cachedShape);
                editingNode.setShapeTexture(cachedShapeTexture);
                editingNode.setSubtitle(cachedSubtitle.trim());
                editingNode.setVisibility(cachedVisibility);
                editingNode.setDisabledBlocksChildren(cachedDisabledBlocksChildren);
                editingNode.setRequireAllPrerequisites(cachedRequireAll);
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
                editingNode.setCustomPosition(cachedPosX, cachedPosY);
                if (!cachedIconItemId.isBlank()) editingNode.setIconItemById(cachedIconItemId.trim());

                for (QuestNode candidate : QuestTreeRegistry.getAllQuests().values()) {
                    if (candidate != editingNode && candidate.getChildren().contains(editingNode)
                            && !candidate.getId().equals(parentLoc)) {
                        candidate.removeChild(editingNode);
                    }
                }
                QuestTreeRegistry.injectDynamicQuestNode(editingNode, parentLoc);
                QuestFileSaver.saveOneQuestToDisk(editingNode);
            } else {
                QuestNode node = new QuestNode(questId, Component.literal(title), Component.literal(desc));
                node.setCategory(category);
                node.setShapeType(cachedShape);
                node.setShapeTexture(cachedShapeTexture);
                node.setSubtitle(cachedSubtitle.trim());
                node.setVisibility(cachedVisibility);
                node.setDisabledBlocksChildren(cachedDisabledBlocksChildren);
                node.setRequireAllPrerequisites(cachedRequireAll);
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
                    if (!editingNode.getEmergencyItems().isEmpty())
                        node.deserializeEmergencyItems(editingNode.serializeEmergencyItems());
                    for (QuestNode p : editingNode.getPrerequisites()) {
                        node.addPrerequisite(p);
                        node.setPrereqRequired(p.getId(), editingNode.isPrereqRequired(p.getId()));
                        node.setPrereqForbidden(p.getId(), editingNode.isPrereqForbidden(p.getId()));
                        node.setPrereqLink(p.getId(), editingNode.isPrereqLink(p.getId()));
                        node.setPrereqCosmetic(p.getId(), editingNode.isPrereqCosmetic(p.getId()));
                        node.setPrereqLineShape(p.getId(), editingNode.getPrereqLineShape(p.getId()));
                        node.setPrereqLineVisual(p.getId(), editingNode.getPrereqLineVisual(p.getId()));
                        node.setPrereqLineSpeed(p.getId(), editingNode.getPrereqLineSpeed(p.getId()));
                        node.setPrereqLineArrow(p.getId(), editingNode.getPrereqLineArrow(p.getId()));
                    }

                    QuestTreeRegistry.removeQuest(editingNode.getId());
                    QuestFileSaver.deleteQuestFiles(editingNode);
                } else if (pendingWorkingNode != null) {

                    for (QuestTask t : pendingWorkingNode.getTasks()) node.addTask(t);
                    for (QuestReward r : pendingWorkingNode.getRewards()) node.addReward(r);
                    for (QuestNode.QuestVariant v : pendingWorkingNode.getVariants()) node.addVariant(v);
                }

                QuestTreeRegistry.injectDynamicQuestNode(node, parentLoc);
                QuestFileSaver.saveOneQuestToDisk(node);
            }

            net.phoenixvine.chronicles.client.LangSyncScheduler.markDirty();

            statusMsg = "Saved!";
            statusIsErr = false;

        } catch (Exception e) {
            statusMsg = "Save failed: " + e.getMessage();
            statusIsErr = true;
        }
    }

    private List<String> buildExistingCategories() {
        List<String> cats = new ArrayList<>();
        cats.add("MAIN");
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            String c = n.getCategory();
            if (c != null && !cats.contains(c)) cats.add(c);
        }

        try {
            Path f = ChronicleOverviewScreen.categoriesFile();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String cat = line.trim().toUpperCase();
                    if (!cat.isEmpty() && !cats.contains(cat)) cats.add(cat);
                }
            }
        } catch (IOException ignored) {}

        net.phoenixvine.chronicles.codec.ChapterLoader.reloadAllChaptersFromDisk();
        for (net.phoenixvine.chronicles.model.ChapterDefinition ch :
                net.phoenixvine.chronicles.registry.ChapterRegistry.getAllChapters()) {
            String cat = ch.getCategory();
            if (cat != null && !cat.isBlank() && !cats.contains(cat.toUpperCase())) cats.add(cat.toUpperCase());
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

