package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuestCreatorScreen extends Screen {

    // ── Colours ───────────────────────────────────────────────────────────────
    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_OK;
    private static final int C_ERR = 0xFFCC4444;
    private static final int C_SHAPE_SEL = 0x775533AA;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 32;
    private static final int MARGIN = 14;
    private static final int MAX_W = 520;
    private static final int LABEL_H = 8;
    private static final int FIELD_H = 16;
    private static final int ROW_GAP = 10;
    private static final int STRIDE = LABEL_H + 3 + FIELD_H + ROW_GAP; // 37
    private static final int DIV_H = 14; // divider between sections
    private static final int EDIT_W = 20;
    private static final int COL_GAP = 8;
    private static final int SEC_PAD = 6;  // panel padding around section rows

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private static final String[] TAB_LABELS = { "Info", "Settings", "Advanced", "Raw" };
    private static final int TAB_H = 20;

    // ── Shapes ───────────────────────────────────────────────────────────────
    private record ShapeMeta(String id, String glyph) {}

    private static final ShapeMeta[] SHAPES = {
            new ShapeMeta("SQUARE", "■"), new ShapeMeta("CIRCLE", "●"),
            new ShapeMeta("DIAMOND", "◆"), new ShapeMeta("HEXAGON", "⬡"),
            new ShapeMeta("TRIANGLE", "▲"), new ShapeMeta("STAR", "★"),
            new ShapeMeta("PENTAGON", "⬠"), new ShapeMeta("SHIELD", "❖"),
            new ShapeMeta("CROSS", "✚"),
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final QuestNode editingNode;

    private String cachedTitle = "";
    private String cachedDesc = "";
    private String cachedSubtitle = "";
    private String cachedCategory = "MAIN";
    private String cachedIconItemId = "";
    private String cachedShape = "SQUARE";
    private QuestNode.Visibility cachedVisibility = QuestNode.Visibility.NORMAL;
    private String cachedEnableIf = "";
    /** null = inherit from category default */
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
    private String cachedDevNotes = "";
    private int cachedPosX = 40;
    private int cachedPosY = 70;

    // Widgets
    private EditBox titleBox, descBox, subtitleBox, categoryBox, idBox, posXBox, posYBox;

    // Dropdowns
    private boolean visibilityDropdownOpen = false;
    private boolean categoryDropdownOpen = false;
    private static final QuestNode.Visibility[] VISIBILITIES = QuestNode.Visibility.values();

    // Status
    private String statusMsg = "";
    private boolean statusIsErr = false;

    // Computed geometry (set in init, used in render + mouseClicked)
    private int cx, cw;                    // content x and width
    private int[] fieldY;                  // field top y for each row (0-8)
    private int secPanelTop, secPanelBot;  // active tab section panel bounds
    private int activeTab = 0;             // 0=Info 1=Settings 2=Advanced

    // ── Constructors ──────────────────────────────────────────────────────────

    public QuestCreatorScreen(Screen parent) {
        super(Component.literal("New Quest"));
        this.parent = parent;
        this.editingNode = null;
    }

    /** Opens the creator with the canvas drop position pre-filled. */
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
        // Raw (untranslated) defaults - this screen always writes these fields back
        // unconditionally on save, so prefilling with a resolved translation would silently
        // re-bake it as the new SNBT default the moment any unrelated field is edited.
        cachedTitle = editingNode.getTitleRaw().getString();
        cachedDesc = editingNode.getDescriptionRaw().getString();
        cachedSubtitle = editingNode.getSubtitleRaw() != null ? editingNode.getSubtitleRaw() : "";
        cachedCategory = editingNode.getCategory();
        cachedIconItemId = editingNode.getIconItemId();
        cachedShape = editingNode.getShapeType() != null ? editingNode.getShapeType() : "SQUARE";
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
        cachedDevNotes = editingNode.getDevNotes();
        cachedPosX = editingNode.getCustomX();
        cachedPosY = editingNode.getCustomY();
        if (!editingNode.getPrerequisites().isEmpty())
            cachedParent = editingNode.getPrerequisites().get(0);
        idManuallySet = true;
        initialized = true;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

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
        if (!initialized) initialized = true;

        cw = Math.min(width - MARGIN * 2, MAX_W);
        cx = (width - cw) / 2;

        fieldY = new int[13];

        // ── Tab buttons ───────────────────────────────────────────────────────
        String[] tabTooltips = {
                "Title, description, category, icon and shape",
                "Reward options and auto-claim settings",
                "Visibility, prerequisites, completion gate and parent quest",
                "Quest ID and task/reward editor"
        };
        int tabW = cw / TAB_LABELS.length;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int ti = i;
            addRenderableWidget(Button.builder(
                    Component.literal((activeTab == i ? "§f" : "§8") + TAB_LABELS[i]),
                    b -> {
                        activeTab = ti;
                        rebuildWidgets();
                    })
                    .bounds(cx + i * tabW, HEADER_H + 3, tabW - 2, TAB_H - 6)
                    .tooltip(Tooltip.create(Component.literal(tabTooltips[i]))).build());
        }

        // ── Adaptive content geometry ─────────────────────────────────────────
        int contentTop = HEADER_H + TAB_H + 4;
        int contentBottom = height - FOOTER_H - 4;
        int tabRows = switch (activeTab) {
            case 1 -> 7;
            case 2 -> 5;
            default -> 4;
        };
        int availH = contentBottom - contentTop - SEC_PAD * 2;
        int dynStride = Math.max(28, Math.min(STRIDE, availH / tabRows));

        int y = contentTop + SEC_PAD;
        secPanelTop = contentTop;

        // ── Tab 0: Basic Info ─────────────────────────────────────────────────
        if (activeTab == 0) {
            // Row 0: Title
            fieldY[0] = y + LABEL_H + 2;
            titleBox = new EditBox(font, cx, fieldY[0], cw - EDIT_W - 2, FIELD_H, Component.empty());
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
            addRenderableWidget(Button.builder(Component.literal("§7✎"),
                    b -> Minecraft.getInstance().setScreen(new QuestTextInputScreen(this, "Title", cachedTitle, 64,
                            v -> {
                                cachedTitle = v;
                                if (titleBox != null) titleBox.setValue(v);
                            })))
                    .bounds(cx + cw - EDIT_W, fieldY[0], EDIT_W, FIELD_H).build());
            y += dynStride;

            // Row 1: Description
            fieldY[1] = y + LABEL_H + 2;
            descBox = new EditBox(font, cx, fieldY[1], cw - EDIT_W - 2, FIELD_H, Component.empty());
            descBox.setMaxLength(512);
            descBox.setHint(Component.literal("§8Short description / lore text"));
            descBox.setValue(cachedDesc);
            descBox.setResponder(v -> cachedDesc = v);
            addRenderableWidget(descBox);
            addRenderableWidget(Button.builder(Component.literal("§7✎"),
                    b -> Minecraft.getInstance()
                            .setScreen(new QuestTextInputScreen(this, "Description", cachedDesc, 512,
                                    v -> {
                                        cachedDesc = v;
                                        if (descBox != null) descBox.setValue(v);
                                    })))
                    .bounds(cx + cw - EDIT_W, fieldY[1], EDIT_W, FIELD_H).build());
            y += dynStride;

            // Row 2: Category (55%) | Subtitle (45%)
            fieldY[2] = y + LABEL_H + 2;
            int catW = (int) (cw * 0.55f);
            int subW = cw - catW - COL_GAP;
            int subX = cx + catW + COL_GAP;
            int catPickW = 16, newCatW = 32;
            int catBoxW = catW - catPickW - 2 - newCatW - 2;
            categoryBox = new EditBox(font, cx, fieldY[2], catBoxW, FIELD_H, Component.empty());
            categoryBox.setMaxLength(32);
            categoryBox.setHint(Component.literal("§8MAIN  CHAPTER_1  …"));
            categoryBox.setValue(cachedCategory);
            categoryBox.setResponder(v -> {
                cachedCategory = v;
                categoryDropdownOpen = false;
            });
            addRenderableWidget(categoryBox);
            addRenderableWidget(Button.builder(Component.literal("§7▾"), b -> {
                categoryDropdownOpen = !categoryDropdownOpen;
                visibilityDropdownOpen = false;
            }).bounds(cx + catBoxW + 2, fieldY[2], catPickW, FIELD_H).build());
            addRenderableWidget(Button.builder(Component.literal("§a+New"), b -> {
                categoryDropdownOpen = false;
                cachedCategory = "";
                if (categoryBox != null) {
                    categoryBox.setValue("");
                    categoryBox.setFocused(true);
                }
            }).bounds(cx + catBoxW + 2 + catPickW + 2, fieldY[2], newCatW, FIELD_H).build());
            subtitleBox = new EditBox(font, subX, fieldY[2], subW - EDIT_W - 2, FIELD_H, Component.empty());
            subtitleBox.setMaxLength(128);
            subtitleBox.setHint(Component.literal("§8Subtitle…"));
            subtitleBox.setValue(cachedSubtitle);
            subtitleBox.setResponder(v -> cachedSubtitle = v);
            addRenderableWidget(subtitleBox);
            addRenderableWidget(Button.builder(Component.literal("§7✎"),
                    b -> Minecraft.getInstance()
                            .setScreen(new QuestTextInputScreen(this, "Subtitle", cachedSubtitle, 128,
                                    v -> {
                                        cachedSubtitle = v;
                                        if (subtitleBox != null) subtitleBox.setValue(v);
                                    })))
                    .bounds(subX + subW - EDIT_W, fieldY[2], EDIT_W, FIELD_H).build());
            y += dynStride;

            // Row 3: Icon (35%) | Shape (65%)
            fieldY[3] = y + LABEL_H + 2;
            int iconColW = (int) (cw * 0.35f);
            int shapeColW = cw - iconColW - COL_GAP;
            int shapeX = cx + iconColW + COL_GAP;
            net.minecraft.world.item.Item iconItem = cachedIconItemId.isBlank() ? null :
                    ForgeRegistries.ITEMS.getValue(new ResourceLocation(cachedIconItemId));
            String iconBtnLabel = (iconItem != null && iconItem != net.minecraft.world.item.Items.AIR) ?
                    "§f" + new net.minecraft.world.item.ItemStack(iconItem).getHoverName().getString() : "§8Pick icon…";
            addRenderableWidget(Button.builder(Component.literal(iconBtnLabel), b -> {
                if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    cachedIconItemId = id != null ? id.toString() : "";
                    rebuildWidgets();
                }));
            }).bounds(cx, fieldY[3], iconColW - EDIT_W - 2, FIELD_H).build());
            addRenderableWidget(Button.builder(Component.literal("§c×"), b -> {
                cachedIconItemId = "";
                rebuildWidgets();
            }).bounds(cx + iconColW - EDIT_W, fieldY[3], EDIT_W, FIELD_H).build());
            int shapeSlot = shapeColW / SHAPES.length;
            for (int i = 0; i < SHAPES.length; i++) {
                ShapeMeta sm = SHAPES[i];
                boolean sel = sm.id().equals(cachedShape);
                addRenderableWidget(Button.builder(
                        Component.literal((sel ? "§d" : "§7") + sm.glyph()),
                        b -> {
                            cachedShape = sm.id();
                            rebuildWidgets();
                        })
                        .bounds(shapeX + i * shapeSlot, fieldY[3], shapeSlot - 1, FIELD_H).build());
            }
            secPanelBot = Math.min(fieldY[3] + FIELD_H + SEC_PAD, contentBottom);
        }

        // ── Tab 1: Quest Settings ──────────────────────────────────────────────
        if (activeTab == 1) {
            // Row 4: Visibility | Prereq gate | [Blocks children]
            fieldY[4] = y + LABEL_H + 2;
            int visW = 90;
            addRenderableWidget(Button.builder(
                    Component.literal("§7" + cachedVisibility.name() + " §8▾"),
                    b -> {
                        visibilityDropdownOpen = !visibilityDropdownOpen;
                        categoryDropdownOpen = false;
                    })
                    .bounds(cx, fieldY[4], visW, FIELD_H).build());
            boolean showBlock = cachedVisibility == QuestNode.Visibility.DISABLED;
            int blockW = showBlock ? 90 : 0;
            int prereqW = cw - visW - COL_GAP - (showBlock ? blockW + COL_GAP : 0);
            String prereqLabel;
            if (cachedRequireAll == null) {
                boolean effective = editingNode.getEffectiveRequireAllPrerequisites();
                prereqLabel = "§8Inherit (" + (effective ? "ALL" : "ANY") + ") §8▾";
            } else if (cachedRequireAll) {
                prereqLabel = "§a✔ ALL prereqs required";
            } else {
                prereqLabel = "§e◑ ANY prereq sufficient";
            }
            addRenderableWidget(Button.builder(Component.literal(prereqLabel),
                    b -> {
                        // 3-way cycle: inherit → all → any → inherit
                        if (cachedRequireAll == null) cachedRequireAll = true;
                        else if (cachedRequireAll) cachedRequireAll = false;
                        else cachedRequireAll = null;
                        rebuildWidgets();
                    })
                    .bounds(cx + visW + COL_GAP, fieldY[4], prereqW, FIELD_H).build());
            if (showBlock) {
                String blkLabel = cachedDisabledBlocksChildren ? "§eBlocks children" : "§8Blocks children";
                addRenderableWidget(Button.builder(Component.literal(blkLabel),
                        b -> {
                            cachedDisabledBlocksChildren = !cachedDisabledBlocksChildren;
                            rebuildWidgets();
                        })
                        .bounds(cx + visW + COL_GAP + prereqW + COL_GAP, fieldY[4], blockW, FIELD_H).build());
            }
            y += dynStride;

            // Row 5: Task completion gate
            fieldY[5] = y + LABEL_H + 2;
            boolean anyMode = cachedTaskMinCount > 0;
            String gateLabel = anyMode ? "§e◑ Complete any " + cachedTaskMinCount + " task(s)" :
                    "§a✔ Complete all tasks";
            addRenderableWidget(Button.builder(Component.literal(gateLabel), b -> {
                cachedTaskMinCount = cachedTaskMinCount == 0 ? 1 : 0;
                rebuildWidgets();
            }).bounds(cx, fieldY[5], anyMode ? cw - 50 : cw, FIELD_H).build());
            if (anyMode) {
                addRenderableWidget(Button.builder(Component.literal("§7−"), b -> {
                    if (cachedTaskMinCount > 1) cachedTaskMinCount--;
                    rebuildWidgets();
                }).bounds(cx + cw - 48, fieldY[5], 22, FIELD_H).build());
                addRenderableWidget(Button.builder(Component.literal("§7+"), b -> {
                    cachedTaskMinCount++;
                    rebuildWidgets();
                }).bounds(cx + cw - 24, fieldY[5], 22, FIELD_H).build());
            }
            y += dynStride;

            // Row 6: Parent (60%) | enable_if (40%)
            fieldY[6] = y + LABEL_H + 2;
            int parentW = (int) (cw * 0.60f);
            int enableIfW = cw - parentW - COL_GAP;
            int enableIfX = cx + parentW + COL_GAP;
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
            }).bounds(cx, fieldY[6], parentW - FIELD_H - 4, FIELD_H).build());
            addRenderableWidget(Button.builder(Component.literal("§c×"), b -> {
                cachedParent = null;
                rebuildWidgets();
            }).bounds(cx + parentW - FIELD_H, fieldY[6], FIELD_H, FIELD_H).build());
            EditBox enableIfBox = new EditBox(font, enableIfX, fieldY[6], enableIfW, FIELD_H, Component.empty());
            enableIfBox.setMaxLength(128);
            enableIfBox.setHint(Component.literal("§8enable_if…"));
            enableIfBox.setValue(cachedEnableIf);
            enableIfBox.setResponder(v -> cachedEnableIf = v);
            addRenderableWidget(enableIfBox);
            y += dynStride;

            // Row 7 (new): Repeat mode + cooldown hours
            fieldY[9] = y + LABEL_H + 2;
            boolean hasCooldown = cachedRepeatMode == QuestNode.RepeatMode.COOLDOWN;
            int repeatBtnW = hasCooldown ? (int) (cw * 0.50f) : cw;
            String repeatIcon = switch (cachedRepeatMode) {
                case NONE -> "§8⊘ One-time  §8▸";
                case DAILY -> "§b☀ Daily  §8▸";
                case COOLDOWN -> "§e⏱ Cooldown  §8▸";
                case INFINITE -> "§a∞ Infinite  §8▸";
            };
            addRenderableWidget(Button.builder(Component.literal(repeatIcon), b -> {
                QuestNode.RepeatMode[] modes = QuestNode.RepeatMode.values();
                cachedRepeatMode = modes[(cachedRepeatMode.ordinal() + 1) % modes.length];
                rebuildWidgets();
            }).bounds(cx, fieldY[9], repeatBtnW, FIELD_H)
                    .tooltip(Tooltip.create(Component.literal(
                            "NONE = one-time only  ·  DAILY = resets at midnight  ·  COOLDOWN = custom wait  ·  INFINITE = repeats immediately")))
                    .build());
            if (hasCooldown) {
                int coolW = cw - repeatBtnW - COL_GAP;
                int coolX = cx + repeatBtnW + COL_GAP;
                addRenderableWidget(Button.builder(Component.literal("§7−"), b -> {
                    if (cachedRepeatCooldownHours > 1) cachedRepeatCooldownHours--;
                    rebuildWidgets();
                }).bounds(coolX, fieldY[9], 18, FIELD_H).build());
                addRenderableWidget(Button.builder(Component.literal("§7+"), b -> {
                    cachedRepeatCooldownHours++;
                    rebuildWidgets();
                }).bounds(coolX + coolW - 18, fieldY[9], 18, FIELD_H).build());
            }
            y += dynStride;

            // Row: Auto-claim rewards
            fieldY[7] = y + LABEL_H + 2;
            String autoLabel = cachedAutoClaimRewards ? "§a⚡ Auto-claim rewards" : "§8⚡ Auto-claim rewards";
            addRenderableWidget(Button.builder(Component.literal(autoLabel),
                    b -> {
                        cachedAutoClaimRewards = !cachedAutoClaimRewards;
                        rebuildWidgets();
                    })
                    .bounds(cx, fieldY[7], cw, FIELD_H)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                            "Automatically grant rewards on completion — no claim button needed")))
                    .build());
            y += dynStride;

            // Row: Reward choice
            fieldY[8] = y + LABEL_H + 2;
            String choiceLabel = cachedRewardChoice ? "§6◈ Reward choice: ON" : "§8◈ Reward choice: OFF";
            addRenderableWidget(Button.builder(Component.literal(choiceLabel),
                    b -> {
                        cachedRewardChoice = !cachedRewardChoice;
                        rebuildWidgets();
                    })
                    .bounds(cx, fieldY[8], cachedRewardChoice ? cw - 54 : cw, FIELD_H)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                            "Player picks a reward from the list instead of receiving all")))
                    .build());
            if (cachedRewardChoice) {
                addRenderableWidget(Button.builder(Component.literal("§7−"),
                        b -> {
                            if (cachedRewardChoiceCount > 1) {
                                cachedRewardChoiceCount--;
                                rebuildWidgets();
                            }
                        })
                        .bounds(cx + cw - 52, fieldY[8], 16, FIELD_H).build());
                addRenderableWidget(Button.builder(Component.literal("§f" + cachedRewardChoiceCount), b -> {})
                        .bounds(cx + cw - 34, fieldY[8], 18, FIELD_H).build());
                addRenderableWidget(Button.builder(Component.literal("§7+"),
                        b -> {
                            cachedRewardChoiceCount++;
                            rebuildWidgets();
                        })
                        .bounds(cx + cw - 14, fieldY[8], 16, FIELD_H).build());
            }
            y += dynStride;

            // Row: Node size
            fieldY[10] = y + LABEL_H + 2;
            String sizeLabel = switch (cachedNodeSize) {
                case SMALL -> "§8◦ Size: Small";
                case LARGE -> "§e● Size: Large";
                default -> "§7• Size: Normal";
            };
            addRenderableWidget(Button.builder(Component.literal(sizeLabel), b -> {
                cachedNodeSize = switch (cachedNodeSize) {
                    case SMALL -> QuestNode.NodeSize.NORMAL;
                    case NORMAL -> QuestNode.NodeSize.LARGE;
                    default -> QuestNode.NodeSize.SMALL;
                };
                rebuildWidgets();
            }).bounds(cx, fieldY[10], cw, FIELD_H)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("Node size on the quest canvas (Small=18px / Normal=32px / Large=48px)")))
                    .build());
            secPanelBot = Math.min(fieldY[10] + FIELD_H + SEC_PAD, contentBottom);
        }

        // ── Tab 2: Advanced ───────────────────────────────────────────────────
        if (activeTab == 2) {
            // Row 7: Quest ID
            fieldY[7] = y + LABEL_H + 2;
            int lockW = 36;
            int copyW = 36;
            idBox = new EditBox(font, cx, fieldY[7], cw - lockW - copyW - 4, FIELD_H, Component.empty());
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
                    }).bounds(cx + cw - lockW - copyW - 2, fieldY[7], lockW, FIELD_H).build());
            addRenderableWidget(Button.builder(
                    Component.literal("§7⎘"),
                    b -> {
                        String fullId = "phoenixcore:" + (cachedId.isEmpty() ? "_unnamed_" : cachedId);
                        Minecraft.getInstance().keyboardHandler.setClipboard(fullId);
                    })
                    .bounds(cx + cw - copyW, fieldY[7], copyW, FIELD_H)
                    .tooltip(Tooltip.create(Component.literal("Copy full quest ID to clipboard"))).build());
            y += dynStride;

            // Row 8: Tasks & Rewards
            fieldY[8] = y + LABEL_H + 2;
            addRenderableWidget(Button.builder(Component.literal("§7⊞ Tasks & Rewards…"), b -> {
                categoryDropdownOpen = false;
                visibilityDropdownOpen = false;
                QuestNode target = editingNode;
                if (target == null) {
                    String id = cachedId.trim().isEmpty() ? "_preview_" : cachedId.trim();
                    target = new QuestNode(
                            new ResourceLocation("phoenixcore", id),
                            Component.literal(cachedTitle), Component.literal(cachedDesc));
                }
                Minecraft.getInstance().setScreen(new TaskRewardEditorScreen(this, target));
            }).bounds(cx, fieldY[8], cw, FIELD_H).build());
            y += dynStride;

            // Row (new): Canvas position X / Y
            fieldY[10] = y + LABEL_H + 2;
            int halfPosW = (cw - COL_GAP) / 2;
            posXBox = new EditBox(font, cx, fieldY[10], halfPosW, FIELD_H, Component.empty());
            posXBox.setMaxLength(6);
            posXBox.setHint(Component.literal("§8X"));
            posXBox.setValue(String.valueOf(cachedPosX));
            posXBox.setResponder(v -> {
                try {
                    cachedPosX = Integer.parseInt(v.trim());
                } catch (Exception ignored) {}
            });
            addRenderableWidget(posXBox);
            posYBox = new EditBox(font, cx + halfPosW + COL_GAP, fieldY[10], halfPosW, FIELD_H, Component.empty());
            posYBox.setMaxLength(6);
            posYBox.setHint(Component.literal("§8Y"));
            posYBox.setValue(String.valueOf(cachedPosY));
            posYBox.setResponder(v -> {
                try {
                    cachedPosY = Integer.parseInt(v.trim());
                } catch (Exception ignored) {}
            });
            addRenderableWidget(posYBox);
            y += dynStride;

            // Row (new): Hide dep line + children count (read-only)
            fieldY[11] = y + LABEL_H + 2;
            int hdepW = (int) (cw * 0.48f);
            String depToggleLabel = cachedHideDepLine ? "§e⊖ Hide dep lines" : "§7⊕ Show dep lines";
            addRenderableWidget(Button.builder(Component.literal(depToggleLabel),
                    b -> {
                        cachedHideDepLine = !cachedHideDepLine;
                        rebuildWidgets();
                    })
                    .bounds(cx, fieldY[11], hdepW, FIELD_H)
                    .tooltip(Tooltip.create(
                            Component.literal("Hide all dependency lines connected to this node on the quest canvas")))
                    .build());
            y += dynStride;

            // Dev notes (internal, never shown to players)
            fieldY[12] = y + LABEL_H + 2;
            EditBox devNotesBox = new EditBox(font, cx, fieldY[12], cw, FIELD_H, Component.empty());
            devNotesBox.setMaxLength(512);
            devNotesBox.setHint(Component.literal("§8Dev notes (internal, never shown to players)…"));
            devNotesBox.setValue(cachedDevNotes);
            devNotesBox.setResponder(v -> cachedDevNotes = v);
            addRenderableWidget(devNotesBox);
            secPanelBot = Math.min(fieldY[12] + FIELD_H + SEC_PAD, contentBottom);
        }

        // ── Tab 3: Raw SNBT viewer ────────────────────────────────────────────
        if (activeTab == 3) {
            // Read-only — no widgets; SNBT is rendered in render()
            secPanelTop = HEADER_H + TAB_H + 4;
            secPanelBot = height - FOOTER_H - 4;
            // Copy-to-clipboard button
            addRenderableWidget(Button.builder(Component.literal("§7⎘ Copy SNBT"),
                    b -> {
                        if (minecraft != null) minecraft.keyboardHandler.setClipboard(buildCurrentSnbt());
                    })
                    .bounds(cx + cw - 80, secPanelTop + 2, 80, 14).build());
        }

        // ── Footer buttons ────────────────────────────────────────────────────
        int fbtnY = height - FOOTER_H + (FOOTER_H - 16) / 2;
        int halfW = (cw - COL_GAP) / 2;
        addRenderableWidget(Button.builder(Component.literal("§a✓ Save quest"),
                b -> save()).bounds(cx, fbtnY, halfW, 16)
                .tooltip(Tooltip.create(Component.literal("Write quest to disk and register it live"))).build());
        addRenderableWidget(Button.builder(Component.literal("§7< Done"), b -> {
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(cx + halfW + COL_GAP, fbtnY, halfW, 16)
                .tooltip(Tooltip.create(Component.literal("Discard unsaved changes and return"))).build());
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, C_BG);

        // Active tab section panel
        int panelL = cx - SEC_PAD;
        int panelR = cx + cw + SEC_PAD;
        int contentBottom2 = height - FOOTER_H - 4;
        if (secPanelBot > secPanelTop) {
            int clampedBot = Math.min(secPanelBot, contentBottom2);
            g.enableScissor(0, secPanelTop, width, contentBottom2);
            g.fill(panelL, secPanelTop, panelR, clampedBot, C_PANEL);
            drawBorder(g, panelL, secPanelTop, panelR - panelL, clampedBot - secPanelTop, C_BORDER);
            g.disableScissor();
        }

        // Header
        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        String heading = editingNode != null ? "§fEdit Quest  §8— §7" + editingNode.getId().getPath() : "§fNew Quest";
        g.drawCenteredString(font, heading, width / 2, (HEADER_H - 8) / 2, C_TEXT);

        // Tab strip background + active underline
        g.fill(0, HEADER_H, width, HEADER_H + TAB_H, C_HEADER);
        g.fill(0, HEADER_H + TAB_H - 1, width, HEADER_H + TAB_H, C_BORDER);
        int tabW = cw / TAB_LABELS.length;
        g.fill(cx + activeTab * tabW, HEADER_H + TAB_H - 2,
                cx + activeTab * tabW + tabW - 2, HEADER_H + TAB_H - 1, C_ACCENT);

        // Footer
        g.fill(0, height - FOOTER_H, width, height, C_HEADER);
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, C_BORDER);

        // Row labels for active tab only
        int[] labelY = new int[13];
        for (int i = 0; i < 13; i++) labelY[i] = fieldY[i] > 0 ? fieldY[i] - LABEL_H - 2 : 0;

        if (activeTab == 0) {
            g.drawString(font, "§8Title", cx, labelY[0], C_TEXT_FAINT, false);
            g.drawString(font, "§8Description", cx, labelY[1], C_TEXT_FAINT, false);
            int catW2 = (int) (cw * 0.55f);
            g.drawString(font, "§8Category", cx, labelY[2], C_TEXT_FAINT, false);
            g.drawString(font, "§8Subtitle", cx + catW2 + COL_GAP, labelY[2], C_TEXT_FAINT, false);
            int iconColW2 = (int) (cw * 0.35f);
            g.drawString(font, "§8Icon", cx, labelY[3], C_TEXT_FAINT, false);
            g.drawString(font, "§8Shape  §7" + cachedShape, cx + iconColW2 + COL_GAP, labelY[3], C_TEXT_FAINT, false);
            // Shape highlight + icon preview
            int shapeColW2 = cw - iconColW2 - COL_GAP;
            int shapeX2 = cx + iconColW2 + COL_GAP;
            int slotW = shapeColW2 / SHAPES.length;
            for (int i = 0; i < SHAPES.length; i++) {
                if (SHAPES[i].id().equals(cachedShape))
                    g.fill(shapeX2 + i * slotW, fieldY[3], shapeX2 + i * slotW + slotW - 1, fieldY[3] + FIELD_H,
                            C_SHAPE_SEL);
            }
            if (!cachedIconItemId.isBlank()) {
                try {
                    net.minecraft.world.item.Item prev = ForgeRegistries.ITEMS
                            .getValue(new ResourceLocation(cachedIconItemId));
                    if (prev != null && prev != net.minecraft.world.item.Items.AIR)
                        g.renderItem(new net.minecraft.world.item.ItemStack(prev), cx + iconColW2 - 18, fieldY[3] - 1);
                } catch (Exception ignored) {}
            }
        } else if (activeTab == 1) {
            g.drawString(font, "§8Visibility  ·  Prerequisite gate", cx, labelY[4], C_TEXT_FAINT, false);
            g.drawString(font, "§8Task completion gate", cx, labelY[5], C_TEXT_FAINT, false);
            int parentW2 = (int) (cw * 0.60f);
            g.drawString(font, "§8Parent quest", cx, labelY[6], C_TEXT_FAINT, false);
            g.drawString(font, "§8enable_if", cx + parentW2 + COL_GAP, labelY[6], C_TEXT_FAINT, false);
            g.drawString(font, "§8Repeat mode", cx, labelY[9], C_TEXT_FAINT, false);
            if (fieldY[7] > 0) g.drawString(font, "§8Rewards", cx, labelY[7], C_TEXT_FAINT, false);
            if (fieldY[8] > 0) g.drawString(font, "§8Choice reward", cx, labelY[8], C_TEXT_FAINT, false);
            if (fieldY[10] > 0) g.drawString(font, "§8Node size", cx, labelY[10], C_TEXT_FAINT, false);
            if (cachedRepeatMode == QuestNode.RepeatMode.COOLDOWN && fieldY[9] > 0) {
                int repeatBtnW2 = (int) (cw * 0.50f);
                int coolW2 = cw - repeatBtnW2 - COL_GAP;
                int coolX2 = cx + repeatBtnW2 + COL_GAP;
                g.drawString(font, "§8Cooldown hours", coolX2 + 22, labelY[9], C_TEXT_FAINT, false);
                g.drawCenteredString(font, "§f" + cachedRepeatCooldownHours + " §8h",
                        coolX2 + coolW2 / 2, fieldY[9] + (FIELD_H - 8) / 2, C_TEXT_DIM);
            }
        } else if (activeTab == 2) {
            g.drawString(font, idManuallySet ? "§8Quest ID  §c(manual)" : "§8Quest ID  §a(auto)", cx, labelY[7],
                    C_TEXT_FAINT, false);
            g.drawString(font, "§8Tasks & rewards", cx, labelY[8], C_TEXT_FAINT, false);
            if (fieldY[10] > 0) {
                g.drawString(font, "§8Canvas position  X / Y", cx, labelY[10], C_TEXT_FAINT, false);
            }
            if (fieldY[11] > 0) {
                int hdepW2 = (int) (cw * 0.48f);
                g.drawString(font, "§8Dep line  ·  Dependents", cx, labelY[11], C_TEXT_FAINT, false);
                if (editingNode != null) {
                    int childCount = editingNode.getChildren().size();
                    String childStr = childCount == 0 ? "§8No dependents" :
                            "§7" + childCount + " quest" + (childCount == 1 ? "" : "s") + " unlock after this";
                    g.drawString(font, childStr, cx + hdepW2 + COL_GAP, fieldY[11] + (FIELD_H - 8) / 2, C_TEXT_DIM,
                            false);
                }
            }
            if (fieldY[12] > 0) {
                g.drawString(font, "§8Dev notes", cx, labelY[12], C_TEXT_FAINT, false);
            }
        } else if (activeTab == 3) {
            // Raw SNBT viewer — word-wrap the SNBT string into the panel
            int panTop = secPanelTop + 2;
            int panBot = secPanelBot - 2;
            g.enableScissor(cx - SEC_PAD, panTop, cx + cw + SEC_PAD, panBot);
            String raw = buildCurrentSnbt();
            int lineY = panTop + 2;
            int lineH = 9;
            for (int ci = 0; ci < raw.length() && lineY + lineH < panBot;) {
                int end = Math.min(raw.length(), ci + (cw / 6));
                // break at space or brace if possible
                if (end < raw.length()) {
                    int lb = raw.lastIndexOf('\n', end);
                    if (lb > ci) {
                        end = lb + 1;
                    }
                }
                String seg = raw.substring(ci, end).replace("\n", " ");
                g.drawString(font, "§7" + seg, cx, lineY, 0xFFAAAAAA, false);
                lineY += lineH;
                ci = end;
            }
            g.disableScissor();
        }

        // Status
        if (!statusMsg.isEmpty()) {
            g.drawCenteredString(font, (statusIsErr ? "§c" : "§a") + statusMsg,
                    width / 2, height - FOOTER_H - 12, statusIsErr ? C_ERR : C_OK);
        }

        super.render(g, mx, my, partial);

        // Dropdowns — elevated z
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        if (visibilityDropdownOpen && activeTab == 1) {
            int visW = 90;
            int dropH = VISIBILITIES.length * (FIELD_H + 1);
            int dropY = fieldY[4] + FIELD_H + 1;
            g.fill(cx, dropY, cx + visW, dropY + dropH, C_PANEL);
            drawBorder(g, cx, dropY, visW, dropH, C_ACCENT);
            for (int i = 0; i < VISIBILITIES.length; i++) {
                int ry = dropY + i * (FIELD_H + 1);
                boolean hov = mx >= cx && mx < cx + visW && my >= ry && my < ry + FIELD_H + 1;
                if (hov) g.fill(cx + 1, ry, cx + visW - 1, ry + FIELD_H + 1, 0xFF1E1E2A);
                g.drawString(font, "§7" + VISIBILITIES[i].name(), cx + 5, ry + 3, hov ? C_TEXT : C_TEXT_DIM, false);
            }
        }

        if (categoryDropdownOpen && activeTab == 0) {
            List<String> cats = buildExistingCategories();
            int catW3 = (int) (cw * 0.55f);
            int catPickW = 16, newCatW = 32;
            int catBoxW = catW3 - catPickW - 2 - newCatW - 2;
            int dropW = catW3;
            int dropH = Math.max(FIELD_H + 1, cats.size() * (FIELD_H + 1));
            int dropY = fieldY[2] + FIELD_H + 1;
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

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            if (visibilityDropdownOpen && activeTab == 1) {
                int visW = 90;
                int dropY = fieldY[4] + FIELD_H + 1;
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
            if (categoryDropdownOpen && activeTab == 0) {
                List<String> cats = buildExistingCategories();
                int catW3 = (int) (cw * 0.55f);
                int dropW = catW3;
                int dropY = fieldY[2] + FIELD_H + 1;
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
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Raw SNBT preview ──────────────────────────────────────────────────────

    /** Builds a preview CompoundTag from current cached field values (same logic as save, but no disk write). */
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
            if (!cachedDevNotes.isBlank()) tag.putString("dev_notes", cachedDevNotes.trim());
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

    // ── Save ──────────────────────────────────────────────────────────────────

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

        Path baseDir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");

        try {
            CompoundTag snbt = new CompoundTag();
            snbt.putString("id", id);
            snbt.putString("title", title);
            snbt.putString("description", desc);
            if (!cachedSubtitle.isBlank()) snbt.putString("subtitle", cachedSubtitle.trim());
            snbt.putString("category", category);
            snbt.putString("shape", cachedShape);
            snbt.putString("visibility", cachedVisibility.name());
            if (cachedDisabledBlocksChildren) snbt.putBoolean("disabled_blocks_children", true);
            if (!cachedEnableIf.isBlank()) snbt.putString("enable_if", cachedEnableIf.trim());
            snbt.putString("parent", cachedParent != null ? cachedParent.getId().getPath() : "none");
            if (cachedRequireAll != null) snbt.putBoolean("require_all_prereqs", cachedRequireAll);
            if (cachedTaskMinCount > 0) snbt.putInt("task_min_count", cachedTaskMinCount);
            snbt.putInt("positionX", cachedPosX);
            snbt.putInt("positionY", cachedPosY);
            if (cachedRepeatMode != QuestNode.RepeatMode.NONE) {
                snbt.putString("repeat_mode", cachedRepeatMode.name());
                if (cachedRepeatMode == QuestNode.RepeatMode.COOLDOWN)
                    snbt.putInt("repeat_cooldown_hours", cachedRepeatCooldownHours);
            }
            if (cachedHideDepLine) snbt.putBoolean("hide_dep_line", true);
            if (cachedAutoClaimRewards) snbt.putBoolean("auto_claim_rewards", true);
            if (cachedRewardChoice) {
                snbt.putBoolean("reward_choice", true);
                if (cachedRewardChoiceCount != 1) snbt.putInt("reward_choice_count", cachedRewardChoiceCount);
            }
            if (cachedNodeSize != QuestNode.NodeSize.NORMAL) snbt.putString("node_size", cachedNodeSize.name());
            if (!cachedDevNotes.isBlank()) snbt.putString("dev_notes", cachedDevNotes.trim());
            if (!cachedIconItemId.isBlank()) snbt.putString("icon_item", cachedIconItemId.trim());

            // Must land in quests/<category>/ like every other quest (QuestFileSaver, the FTB
            // importer) - a flat root/quests-root path here would get it flagged as a "datapack
            // quest" with no editable file (questSnbt() only looks in the category folder) and
            // orphaned the moment anything else re-saves the registry.
            Path categoryDir = baseDir.resolve("quests").resolve(category.toLowerCase(java.util.Locale.ROOT));
            Path snbtPath = categoryDir.resolve(id + ".snbt");
            Files.createDirectories(snbtPath.getParent());
            Files.writeString(snbtPath, snbt.toString(), StandardCharsets.UTF_8);

            Path mdPath = categoryDir.resolve(id + ".md");
            Files.createDirectories(mdPath.getParent());
            if (!Files.exists(mdPath)) {
                Files.writeString(mdPath,
                        "---\ntitle: \"" + title.replace("\"", "\\\"") + "\"\n---\n\n" + desc + "\n",
                        StandardCharsets.UTF_8);
            } else {
                String existing = Files.readString(mdPath, StandardCharsets.UTF_8);
                Files.writeString(mdPath, LangEditorScreen.patchMdFile(existing, title, desc), StandardCharsets.UTF_8);
            }

            ResourceLocation questId = new ResourceLocation("phoenixcore", id);
            ResourceLocation parentLoc = cachedParent != null ? cachedParent.getId() : null;

            QuestNode node = new QuestNode(questId, Component.literal(title), Component.literal(desc));
            node.setCategory(category);
            node.setShapeType(cachedShape);
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
            node.setDevNotes(cachedDevNotes.trim());
            node.setCustomPosition(cachedPosX, cachedPosY);
            if (!cachedIconItemId.isBlank()) node.setIconItemById(cachedIconItemId.trim());

            QuestTreeRegistry.injectDynamicQuestNode(node, parentLoc);

            Path base = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("phoenix_chronicles");
            LangEditorScreen.writeEnUsJson(base);

            statusMsg = "Saved!";
            statusIsErr = false;

        } catch (IOException e) {
            statusMsg = "IO error: " + e.getMessage();
            statusIsErr = true;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> buildExistingCategories() {
        List<String> cats = new ArrayList<>();
        cats.add("MAIN");
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            String c = n.getCategory();
            if (c != null && !cats.contains(c)) cats.add(c);
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
