package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.registry.LangSyncScheduler;
import net.phoenixvine.chronicles.client.render.ChroniclesThemeRenderer;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.screen.utils.UndoRedoManager;
import net.phoenixvine.chronicles.filter.IFluidFilter;
import net.phoenixvine.chronicles.filter.IItemFilter;
import net.phoenixvine.chronicles.filter.ItemFilters;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;
import net.phoenixvine.chronicles.item.FluidFilterTokenItem;
import net.phoenixvine.chronicles.item.ItemFilterTokenItem;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.tasks.*;
import net.phoenixvine.chronicles.tasks.BlockBreakTask;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskRewardEditorScreen extends Screen {

    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_OK;
    private static final int C_ROW_HOVER = 0x22FFFFFF;
    private static final int C_FORM_BG = 0x33000000;
    private static final int C_SPLIT = 0xFF2A2A3A;
    private static final int C_TOOLTIP_BG = 0xFF0E0E16;

    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 28;
    private static final int MARGIN = 10;
    private static final int COL_GAP = 6;
    private static final int ROW_H = 26;
    private static final int FIELD_H = 15;
    private static final int FIELD_GAP = 5;

    private static final int FORM_ROWS = 5;

    private static final int MIN_W = 560;
    private static final int MIN_H = 380;
    private float uiScale = 1f;
    private int vw, vh;

    private int splitX;
    private int colW;
    private int listTop;
    private int listBottom;
    private int formTop;
    private int formBottom;

    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;

    private final Screen parent;

    Screen getParentScreen() {
        return parent;
    }

    private final QuestNode questNode;

    @Nullable
    private final QuestNode.QuestVariant variantTarget;

    private final List<QuestTask> tasks = new ArrayList<>();
    private final List<QuestReward> rewards = new ArrayList<>();

    private String taskType = "item_check";
    private boolean taskConsume = false;
    private boolean taskOptional = false;

    private boolean taskSticky = true;
    private boolean taskCheckAe2Storage = AE2Compat.isAvailable();
    private boolean taskTypeDropOpen = false;
    private int taskTypeDropScroll = 0;
    private EditBox taskDescBox, taskTargetBox, taskCountBox, taskSecondaryBox, taskNbtBox;

    private int editingTaskIndex = -1;
    private int editingRewardIndex = -1;

    private IItemFilter pendingPickedItemFilter = null;
    private IFluidFilter pendingPickedFluidFilter = null;

    private boolean forcePendingTaskValues = false;
    private String pendingTaskDesc = "", pendingTaskTarget = "", pendingTaskSecondary = "", pendingTaskCount = "",
            pendingTaskNbt = "";
    private boolean forcePendingRewardValues = false;
    private String pendingRewardCount = "", pendingRewardCommand = "", pendingRewardEventData = "";

    private String rewardType = "item";
    private boolean rewardTypeDropOpen = false;
    private ItemStack rewardPickedItem = null;
    private EditBox rewardCountBox, rewardCommandBox;

    private QuestReward.ChoiceBoxReward.Mode boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
    private final List<QuestReward> boxOptions = new ArrayList<>();
    private static final int BOX_OPTION_ROW_H = 14;
    private int boxOptionsListX, boxOptionsListY, boxOptionsListW, boxOptionsListBottom;
    private int boxOptionsScroll = 0;
    private final List<int[]> boxOptionRowRects = new ArrayList<>();

    private int editingBoxOptionIndex = -1;
    private ItemStack boxOptionPickedItem = null;
    private EditBox boxOptionCountBox, boxOptionNbtBox;
    private boolean forcePendingBoxOptionValues = false;
    private String pendingBoxOptionCount = "1", pendingBoxOptionNbt = "";

    private int hoveredTaskRow = -1;
    private int hoveredRewardRow = -1;
    private int hoveredDropRow = -1;

    private int draggingTaskIndex = -1;
    private int draggingRewardIndex = -1;
    private boolean dragMovedTask = false;
    private boolean dragMovedReward = false;

    private static final int ROW_HEADER_H = 14;
    private List<Integer> rewardDisplayOrder = List.of();

    private static CompoundTag copiedTaskNBT = null;

    private final UndoRedoManager undoRedo = new UndoRedoManager(msg -> {});

    private static final String[] REWARD_TYPES = { "item", "xp", "command", "loot_table", "script_event",
            "reward_table", "choice_box" };

    private static String rewardTypeLabel(String type) {
        return switch (type) {
            case "item" -> "Item";
            case "xp" -> "XP";
            case "command" -> "Command";
            case "loot_table" -> "Loot Table";
            case "script_event" -> "Script Event";
            case "reward_table" -> "Reward Table";
            case "choice_box" -> "Choice Box";
            default -> type;
        };
    }

    private EditBox rewardEventDataBox;

    public TaskRewardEditorScreen(Screen parent, QuestNode questNode) {
        this(parent, questNode, null);
    }

    public TaskRewardEditorScreen(Screen parent, QuestNode questNode, @Nullable QuestNode.QuestVariant variantTarget) {
        super(Component.literal(variantTarget != null ? "Tasks & Rewards (variant)" : "Tasks & Rewards"));
        this.parent = parent;
        this.questNode = questNode;
        this.variantTarget = variantTarget;
        if (variantTarget != null) {
            this.tasks.addAll(variantTarget.tasks != null ? variantTarget.tasks : questNode.getTasks());
            this.rewards.addAll(variantTarget.rewards != null ? variantTarget.rewards : questNode.getRewards());
        } else {
            this.tasks.addAll(questNode.getTasks());
            this.rewards.addAll(questNode.getRewards());
        }
    }

    @Override
    protected void init() {
        PhoenixTheme th = PhoenixTheme.current();
        C_BG = th.bg.getColor();
        C_PANEL = th.panel.getColor();
        C_HEADER = th.header.getColor();
        C_BORDER = th.border.getColor();
        C_ACCENT = th.accent.getColor();
        C_TEXT = th.text.getColor();
        C_TEXT_DIM = th.textDim.getColor();
        C_TEXT_FAINT = th.textFaint.getColor();
        C_OK = th.done.getColor();

        uiScale = (width < MIN_W || height < MIN_H) ? Math.min(width / (float) MIN_W, height / (float) MIN_H) : 1f;
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);

        colW = (vw - MARGIN * 2 - COL_GAP) / 2;
        splitX = MARGIN + colW + COL_GAP;
        formBottom = vh - FOOTER_H;
        formTop = formBottom - MARGIN - FORM_ROWS * (FIELD_H + FIELD_GAP) - 8;
        listTop = HEADER_H + 22;
        listBottom = formTop - 22;

        lastLayoutWidth = width;
        lastLayoutHeight = height;

        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        String descVal = forcePendingTaskValues ? pendingTaskDesc : (taskDescBox != null ? taskDescBox.getValue() : "");
        String targetVal = forcePendingTaskValues ? pendingTaskTarget :
                (taskTargetBox != null ? taskTargetBox.getValue() : "");
        String secondVal = forcePendingTaskValues ? pendingTaskSecondary :
                (taskSecondaryBox != null ? taskSecondaryBox.getValue() : "");
        String countVal = forcePendingTaskValues ? pendingTaskCount :
                (taskCountBox != null ? taskCountBox.getValue() : "1");
        String nbtVal = forcePendingTaskValues ? pendingTaskNbt : (taskNbtBox != null ? taskNbtBox.getValue() : "");
        forcePendingTaskValues = false;

        String rCountVal = forcePendingRewardValues ? pendingRewardCount :
                (rewardCountBox != null ? rewardCountBox.getValue() : "");
        String rCommandVal = forcePendingRewardValues ? pendingRewardCommand :
                (rewardCommandBox != null ? rewardCommandBox.getValue() : "");
        String rEventDataVal = forcePendingRewardValues ? pendingRewardEventData :
                (rewardEventDataBox != null ? rewardEventDataBox.getValue() : "");
        forcePendingRewardValues = false;

        String boCountVal = forcePendingBoxOptionValues ? pendingBoxOptionCount :
                (boxOptionCountBox != null ? boxOptionCountBox.getValue() : "1");
        String boNbtVal = forcePendingBoxOptionValues ? pendingBoxOptionNbt :
                (boxOptionNbtBox != null ? boxOptionNbtBox.getValue() : "");
        forcePendingBoxOptionValues = false;

        clearWidgets();

        addRenderableWidget(Button.builder(Component.literal("§7‹ Done"), b -> {
            flushToQuestNode();
            ChronicleOverviewScreen.invalidateNodeCachesUpChain(parent, questNode);
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(vw / 2 - 40, vh - FOOTER_H + (FOOTER_H - 14) / 2, 80, 14)
                .tooltip(Tooltip.create(Component.literal("Save changes and return to quest editor"))).build());

        int tx = MARGIN;
        int fy = formTop + 8;

        PhoenixTaskRegistry.TaskEntry curMeta = getTaskMeta(taskType);
        String typeTooltip = curMeta != null && curMeta.editorTooltip() != null ?
                curMeta.editorTooltip().split("\n")[0] : "Choose the type of task to add";
        addRenderableWidget(Button.builder(
                Component.literal("§8Type: §7" + (curMeta != null ? curMeta.editorLabel() : taskType) + " §8▾"),
                b -> {
                    taskTypeDropOpen = !taskTypeDropOpen;
                    taskTypeDropScroll = 0;
                    rewardTypeDropOpen = false;
                })
                .bounds(tx, fy, colW, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(typeTooltip))).build());
        fy += FIELD_H + FIELD_GAP;

        boolean isInfo = taskType.equals("info");
        boolean needsTarget = switch (taskType) {
            case "experience", "dimension", "checkmark" -> false;
            default -> {
                PhoenixTaskRegistry.TaskEntry re = PhoenixTaskRegistry.get(taskType);
                if (re != null && !re.fields().isEmpty())
                    yield re.fields().stream()
                            .anyMatch(f -> f.type() != PhoenixTaskRegistry.FieldDef.FieldType.INTEGER &&
                                    f.type() != PhoenixTaskRegistry.FieldDef.FieldType.BOOLEAN);
                yield true;
            }
        };
        boolean needsSecond = taskType.equals("block_interact") || taskType.equals("stat") ||
                taskType.equals("dimension") || taskType.equals("energy_check");
        boolean needsCount = switch (taskType) {
            case "kill_entity", "item_check", "craft_item", "experience", "fluid_check", "stat", "tag_item", "energy_check", "external_trigger", "view_machine", "view_scene" -> true;
            default -> {
                PhoenixTaskRegistry.TaskEntry re = PhoenixTaskRegistry.get(taskType);
                yield re != null &&
                        re.fields().stream().anyMatch(f -> f.type() == PhoenixTaskRegistry.FieldDef.FieldType.INTEGER);
            }
        };
        boolean showConsume = switch (taskType) {
            case "kill_entity", "item_check", "craft_item", "fluid_check", "location_terminal", "stat", "block_interact", "filter_item", "filter_fluid" -> true;
            default -> false;
        };

        boolean showSticky = switch (taskType) {
            case "item_check", "tag_item", "fluid_check", "energy_check", "filter_item", "filter_fluid" -> true;
            default -> false;
        };

        boolean showAe2Toggle = AE2Compat.isAvailable() && switch (taskType) {
            case "item_check", "fluid_check", "filter_item", "filter_fluid" -> true;
            default -> false;
        };

        taskDescBox = new EditBox(font, tx, fy, colW, FIELD_H, Component.empty());
        taskDescBox.setHint(Component.literal("§8Task label shown to player"));
        taskDescBox.setMaxLength(128);
        taskDescBox.setValue(descVal);
        addRenderableWidget(taskDescBox);
        fy += FIELD_H + FIELD_GAP;

        if (needsTarget) {
            String hint = isInfo ? "§8Body text shown to the player" : switch (taskType) {
                case "kill_entity" -> "§8Entity id  (e.g. minecraft:zombie)";
                case "item_check", "craft_item" -> "§8Item id  (e.g. minecraft:iron_ingot)";
                case "location_terminal" -> "§8Terminal id";
                case "advancement" -> "§8Advancement id";
                case "block_interact" -> "§8Block id";
                case "fluid_check" -> "§8Fluid id";
                case "stat" -> "§8Stat id  (e.g. minecraft:jump)";
                case "biome" -> "§8Biome id";
                case "structure" -> "§8Structure id";
                case "tag_item" -> "§8Item tag  (e.g. c:ores/iron)";
                case "energy_check" -> "§8FE / EU / ANY";
                case "filter_item" -> "§8Item id(s), semicolon-separated: ANY match  (e.g. wire;cable)";
                case "filter_fluid" -> "§8Fluid id(s), semicolon-separated: ANY match  (e.g. water;lava)";
                case "external_trigger" -> "§8Trigger id";
                case "view_machine" -> "§8Machine id  (Phantasia multiblock definition id)";
                case "view_scene" -> "§8Scene id  (Phantasia scene definition id)";
                case "view_guide" -> "§8Guide id  (Phantasia guide definition id)";
                default -> {
                    PhoenixTaskRegistry.TaskEntry re = PhoenixTaskRegistry.get(taskType);
                    if (re != null) {
                        for (PhoenixTaskRegistry.FieldDef f : re.fields()) {
                            if (f.type() != PhoenixTaskRegistry.FieldDef.FieldType.INTEGER &&
                                    f.type() != PhoenixTaskRegistry.FieldDef.FieldType.BOOLEAN)
                                yield "§8" + f.label() + (f.hint() != null ? "  (" + f.hint() + ")" : "");
                        }
                    }
                    yield "§8Target id";
                }
            };
            boolean hasItemPicker = taskType.equals("item_check") || taskType.equals("craft_item");

            boolean hasItemListPicker = taskType.equals("filter_item");
            boolean hasFluidPicker = taskType.equals("fluid_check");
            boolean hasFluidListPicker = taskType.equals("filter_fluid");
            boolean hasAnyItemPicker = hasItemPicker || hasItemListPicker;
            boolean hasAnyFluidPicker = hasFluidPicker || hasFluidListPicker;
            int tw = (hasAnyItemPicker || hasAnyFluidPicker) ? colW - 36 : colW;
            int tmaxLen = isInfo ? 512 : 160;
            taskTargetBox = new EditBox(font, tx, fy, tw, FIELD_H, Component.empty());
            taskTargetBox.setHint(Component.literal(hint));
            taskTargetBox.setMaxLength(tmaxLen);
            taskTargetBox.setValue(targetVal);
            addRenderableWidget(taskTargetBox);
            if (hasItemPicker) {
                addRenderableWidget(Button.builder(Component.literal("§7⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        if (applyPickedItemFilter(stack)) return;
                        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        if (id != null && taskTargetBox != null) taskTargetBox.setValue(id.toString());
                    }));
                }).bounds(tx + tw, fy, 16, FIELD_H).build());
            } else if (hasItemListPicker) {
                addRenderableWidget(Button.builder(Component.literal("§7⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        if (applyPickedItemFilter(stack)) return;
                        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        if (id == null || taskTargetBox == null) return;
                        String cur = taskTargetBox.getValue().trim();
                        taskTargetBox.setValue(cur.isEmpty() ? id.toString() : cur + ";" + id);
                    }));
                }).bounds(tx + tw, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(Component.literal("Add another item to the ANY-match list")))
                        .build());
            } else if (hasFluidPicker) {
                addRenderableWidget(Button.builder(Component.literal("§3⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new FluidPickerScreen(this, fluidId -> {
                        if (taskTargetBox != null) taskTargetBox.setValue(fluidId);
                    }));
                }).bounds(tx + tw, fy, 16, FIELD_H).build());
            } else if (hasFluidListPicker) {
                addRenderableWidget(Button.builder(Component.literal("§3⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new FluidPickerScreen(this, fluidId -> {
                        if (taskTargetBox == null) return;
                        String cur = taskTargetBox.getValue().trim();
                        taskTargetBox.setValue(cur.isEmpty() ? fluidId : cur + ";" + fluidId);
                    }));
                }).bounds(tx + tw, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(Component.literal("Add another fluid to the ANY-match list")))
                        .build());
            }
            if (hasAnyItemPicker) {
                addRenderableWidget(Button.builder(Component.literal("§d⚡"), b -> applyHeldItemFilter())
                        .bounds(tx + tw + 18, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(Component.literal(
                                "Use the configured filter token held in your hand\n(from the Item/Fluid Filter items) as this task's match rule")))
                        .build());
            } else if (hasAnyFluidPicker) {
                addRenderableWidget(Button.builder(Component.literal("§d⚡"), b -> applyHeldFluidFilter())
                        .bounds(tx + tw + 18, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(Component.literal(
                                "Use the configured filter token held in your hand\n(from the Item/Fluid Filter items) as this task's match rule")))
                        .build());
            }
            fy += FIELD_H + FIELD_GAP;
        }

        taskNbtBox = null;
        if (taskType.equals("item_check")) {
            taskNbtBox = new EditBox(font, tx, fy, colW, FIELD_H, Component.empty());
            taskNbtBox.setHint(Component.literal("§8NBT filter (optional)"));
            taskNbtBox.setMaxLength(512);
            taskNbtBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal(
                            "Subset NBT match. Item must contain ALL keys listed here.\nLeave blank to match any stack of the item.")));
            taskNbtBox.setValue(nbtVal);
            addRenderableWidget(taskNbtBox);
            fy += FIELD_H + FIELD_GAP;
        }

        if (needsSecond) {
            String hint2 = switch (taskType) {
                case "block_interact" -> "§8PLACE or RIGHT_CLICK";
                case "dimension" -> "§8Dimension id  (e.g. minecraft:the_nether)";
                case "energy_check" -> "§8INVENTORY / HELD / BLOCK";
                default -> "§8Secondary value";
            };
            taskSecondaryBox = new EditBox(font, tx, fy, colW, FIELD_H, Component.empty());
            taskSecondaryBox.setHint(Component.literal(hint2));
            taskSecondaryBox.setMaxLength(128);
            taskSecondaryBox.setValue(secondVal);
            addRenderableWidget(taskSecondaryBox);
            fy += FIELD_H + FIELD_GAP;
        }

        int rowY = formBottom - FIELD_H - 4;
        int flexGap = 4;
        int flexX = tx;
        int flexAvail = colW;
        if (needsCount) {
            String countHint = switch (taskType) {
                case "experience" -> "§8XP level";
                case "fluid_check" -> "§8mB amount";
                case "stat" -> "§8Target value";
                case "energy_check" -> "§8FE required";
                case "external_trigger" -> "§8Times fired";
                case "view_machine", "view_scene" -> "§8Min seconds";
                default -> {
                    PhoenixTaskRegistry.TaskEntry re = PhoenixTaskRegistry.get(taskType);
                    if (re != null) for (PhoenixTaskRegistry.FieldDef f : re.fields())
                        if (f.type() == PhoenixTaskRegistry.FieldDef.FieldType.INTEGER) yield "§8" + f.label();
                    yield "§8Count";
                }
            };
            taskCountBox = new EditBox(font, tx, rowY, 52, FIELD_H, Component.empty());
            taskCountBox.setHint(Component.literal(countHint));
            taskCountBox.setMaxLength(8);
            taskCountBox.setValue(countVal);
            addRenderableWidget(taskCountBox);
            flexX = tx + 52 + flexGap;
            flexAvail = colW - 52 - flexGap;
        }

        record FlexBtn(int idealW, java.util.function.Supplier<Button.Builder> factory) {}
        List<FlexBtn> flexBtns = new ArrayList<>();
        if (showConsume) {
            flexBtns.add(new FlexBtn(54, () -> Button.builder(
                    Component.literal(taskConsume ? "§aConsume" : "§8Consume"),
                    b -> {
                        taskConsume = !taskConsume;
                        rebuildWidgets();
                    }).tooltip(Tooltip.create(
                            Component.literal("Remove the item/fluid from the player's inventory on completion")))));
        }
        if (showAe2Toggle) {
            flexBtns.add(new FlexBtn(40, () -> Button.builder(
                    Component.literal(taskCheckAe2Storage ? "§bAE2" : "§8AE2"),
                    b -> {
                        taskCheckAe2Storage = !taskCheckAe2Storage;
                        rebuildWidgets();
                    }).tooltip(Tooltip.create(Component.literal(
                            "ON (default when AE2 is installed): also count/withdraw matching items or\n" +
                                    "fluid stored in your linked Applied Energistics 2 ME network, in addition\n" +
                                    "to the player's inventory.")))));
        }
        if (showSticky) {
            flexBtns.add(new FlexBtn(56, () -> Button.builder(
                    Component.literal(taskSticky ? "§bSticky" : "§8Sticky"),
                    b -> {
                        taskSticky = !taskSticky;
                        rebuildWidgets();
                    }).tooltip(Tooltip.create(Component.literal(
                            "ON (default): once satisfied, stays satisfied - placing/using the item\n" +
                                    "later won't un-complete this task.\n" +
                                    "OFF: re-checked live - task un-completes if you stop holding enough.")))));
        }
        flexBtns.add(new FlexBtn(50, () -> Button.builder(
                Component.literal(taskOptional ? "§eOptional" : "§8Optional"),
                b -> {
                    taskOptional = !taskOptional;
                    rebuildWidgets();
                }).tooltip(Tooltip.create(Component.literal("Task is optional: won't block quest completion")))));
        flexBtns.add(new FlexBtn(46, () -> Button.builder(
                Component.literal(editingTaskIndex >= 0 ? "§b✎ Update" : "§a✔ Add"),
                b -> commitTaskFromForm())
                .tooltip(Tooltip.create(Component.literal(editingTaskIndex >= 0 ?
                        "Save changes to this task (right-click it again to cancel)" :
                        "Add this task to the quest (Ctrl+Z to undo)")))));

        int idealTotal = flexBtns.stream().mapToInt(FlexBtn::idealW).sum() + flexGap * (flexBtns.size() - 1);
        int minFlexBtnW = 26;
        double scale = idealTotal > flexAvail && idealTotal > 0 ? Math.max(
                minFlexBtnW * flexBtns.size() / (double) idealTotal, flexAvail / (double) idealTotal) : 1.0;
        int flexCursor = flexX;
        for (FlexBtn fb : flexBtns) {
            int w = Math.max(minFlexBtnW, (int) Math.round(fb.idealW() * scale));
            addRenderableWidget(fb.factory().get().bounds(flexCursor, rowY, w, FIELD_H).build());
            flexCursor += w + flexGap;
        }

        int rx = splitX;
        int rfy = formTop + 8;

        String rewardTypeTooltip = switch (rewardType) {
            case "item" -> "Give the player one or more items";
            case "xp" -> "Award experience levels";
            case "command" -> "Run a server command (%player% = player name)";
            case "loot_table" -> "Roll a loot table and give all resulting items";
            case "script_event" -> "Fire a Forge event for KubeJS or Java handlers";
            case "reward_table" -> "Reference a named reward table (config/phoenix_chronicles/reward_tables/)";
            case "choice_box" -> "A single slot the player resolves themselves - Menu mode lets them " +
                    "pick one of the options below, Lootbox mode grants a random one instantly";
            default -> "Choose a reward type";
        };
        addRenderableWidget(Button.builder(
                Component.literal("§8Type: §7" + rewardTypeLabel(rewardType) + " §8▾"),
                b -> {
                    rewardTypeDropOpen = !rewardTypeDropOpen;
                    taskTypeDropOpen = false;
                })
                .bounds(rx, rfy, colW, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(rewardTypeTooltip))).build());
        rfy += FIELD_H + FIELD_GAP;

        if (rewardType.equals("item")) {
            String itemLabel = rewardPickedItem != null ? "§f" + rewardPickedItem.getHoverName().getString() :
                    "§8Pick Item";
            addRenderableWidget(Button.builder(Component.literal(itemLabel), b -> {
                if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                    rewardPickedItem = stack;
                    rebuildWidgets();
                }));
            }).bounds(rx, rfy, colW - 44, FIELD_H).build());
            rewardCountBox = new EditBox(font, rx + colW - 42, rfy, 42, FIELD_H, Component.empty());
            rewardCountBox.setHint(Component.literal("§8Qty"));
            rewardCountBox.setMaxLength(4);
            rewardCountBox.setValue(rCountVal);
            addRenderableWidget(rewardCountBox);
        } else if (rewardType.equals("xp")) {
            rewardCountBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            rewardCountBox.setHint(Component.literal("§8XP levels to award"));
            rewardCountBox.setMaxLength(5);
            rewardCountBox.setValue(rCountVal);
            addRenderableWidget(rewardCountBox);
        } else if (rewardType.equals("script_event")) {
            rewardCommandBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            rewardCommandBox.setHint(Component.literal("§8Event ID  (e.g. unlock_end)"));
            rewardCommandBox.setMaxLength(128);
            rewardCommandBox.setValue(rCommandVal);
            addRenderableWidget(rewardCommandBox);
            rfy += FIELD_H + FIELD_GAP;
            rewardEventDataBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            rewardEventDataBox.setHint(Component.literal("§8NBT data  {key:\"val\"}  (optional)"));
            rewardEventDataBox.setMaxLength(256);
            rewardEventDataBox.setValue(rEventDataVal);
            addRenderableWidget(rewardEventDataBox);
        } else if (rewardType.equals("reward_table")) {
            String knownTables = net.phoenixvine.chronicles.registry.RewardTableRegistry.getAll().keySet()
                    .stream().reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b);
            String hint = knownTables.isEmpty() ? "§8Table ID  (no tables loaded yet)" :
                    "§8Table ID: known: " + knownTables;
            rewardCommandBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            rewardCommandBox.setHint(Component.literal(hint));
            rewardCommandBox.setMaxLength(128);
            rewardCommandBox.setValue(rCommandVal);
            addRenderableWidget(rewardCommandBox);
        } else if (rewardType.equals("choice_box") && editingBoxOptionIndex >= 0) {
            String itemLabel = boxOptionPickedItem != null ?
                    "§f" + boxOptionPickedItem.getHoverName().getString() : "§8Pick Item";
            addRenderableWidget(Button.builder(Component.literal(itemLabel), b -> {
                if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                    boxOptionPickedItem = stack;
                    rebuildWidgets();
                }));
            }).bounds(rx, rfy, colW - 44, FIELD_H).build());
            boxOptionCountBox = new EditBox(font, rx + colW - 42, rfy, 42, FIELD_H, Component.empty());
            boxOptionCountBox.setHint(Component.literal("§8Qty"));
            boxOptionCountBox.setMaxLength(4);
            boxOptionCountBox.setValue(boCountVal);
            addRenderableWidget(boxOptionCountBox);
            rfy += FIELD_H + FIELD_GAP;

            boxOptionNbtBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            boxOptionNbtBox.setHint(Component.literal("§8NBT  {display:{Name:'...'}}  (optional)"));
            boxOptionNbtBox.setMaxLength(256);
            boxOptionNbtBox.setValue(boNbtVal);
            addRenderableWidget(boxOptionNbtBox);
            rfy += FIELD_H + FIELD_GAP;

            addRenderableWidget(Button.builder(Component.literal("§a✔ Save Option"), b -> commitBoxOptionEdit())
                    .bounds(rx, rfy, colW / 2 - 2, FIELD_H)
                    .tooltip(Tooltip.create(Component.literal("Save changes to this option")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("§7Cancel"), b -> cancelBoxOptionEdit())
                    .bounds(rx + colW / 2 + 2, rfy, colW / 2 - 2, FIELD_H).build());
        } else if (rewardType.equals("choice_box")) {
            addRenderableWidget(Button.builder(
                    Component.literal("§8Mode: §7" +
                            (boxMode == QuestReward.ChoiceBoxReward.Mode.LOOTBOX ? "Lootbox" : "Menu") + " §8▾"),
                    b -> {
                        boxMode = boxMode == QuestReward.ChoiceBoxReward.Mode.MENU ?
                                QuestReward.ChoiceBoxReward.Mode.LOOTBOX : QuestReward.ChoiceBoxReward.Mode.MENU;
                        rebuildWidgets();
                    })
                    .bounds(rx, rfy, colW - 76, FIELD_H)
                    .tooltip(Tooltip.create(Component.literal(
                            "Menu: player clicks the box and picks which option they get.\n" +
                                    "Lootbox: player clicks the box and the server grants a random option.")))
                    .build());
            addRenderableWidget(Button.builder(Component.literal("§a+ Item"), b -> {
                if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                    boxOptions.add(new QuestReward.ItemReward(stack.getItem(), Math.max(1, stack.getCount())));
                    rebuildWidgets();
                }));
            }).bounds(rx + colW - 72, rfy, 72, FIELD_H)
                    .tooltip(Tooltip.create(Component.literal(
                            "Add an item option to this choice box (right-click an option to edit its qty/NBT)")))
                    .build());
            rfy += FIELD_H + FIELD_GAP;

            boxOptionsListX = rx;
            boxOptionsListY = rfy;
            boxOptionsListW = colW;
            boxOptionsListBottom = formBottom - FIELD_H - 4 - FIELD_GAP;
            int visibleRows = Math.max(1, (boxOptionsListBottom - boxOptionsListY) / BOX_OPTION_ROW_H);
            int maxScroll = Math.max(0, boxOptions.size() - visibleRows);
            boxOptionsScroll = Math.max(0, Math.min(boxOptionsScroll, maxScroll));
        } else {

            String hint = rewardType.equals("loot_table") ? "§8Loot table id  (e.g. minecraft:chests/simple_dungeon)" :
                    "§8/give %player% …";
            rewardCommandBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            rewardCommandBox.setHint(Component.literal(hint));
            rewardCommandBox.setMaxLength(256);
            rewardCommandBox.setValue(rCommandVal);
            addRenderableWidget(rewardCommandBox);
        }

        addRenderableWidget(Button.builder(
                Component.literal(editingRewardIndex >= 0 ? "§b✎ Update Reward" : "§a✔ Add Reward"),
                b -> commitRewardFromForm())
                .bounds(rx + colW - 80, formBottom - FIELD_H - 4, 80, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(editingRewardIndex >= 0 ?
                        "Save changes to this reward (right-click it again to cancel)" :
                        "Add this reward to the quest (Ctrl+Z to undo)")))
                .build());
    }

    private List<QuestTask> dragBeforeTasks;
    private List<QuestReward> dragBeforeRewards;

    private void pushUndo(Runnable mutation) {
        List<QuestTask> beforeTasks = new ArrayList<>(tasks);
        List<QuestReward> beforeRewards = new ArrayList<>(rewards);

        mutation.run();

        registerUndoRedo(beforeTasks, beforeRewards, new ArrayList<>(tasks), new ArrayList<>(rewards));
    }

    private void beginDragUndo() {
        dragBeforeTasks = new ArrayList<>(tasks);
        dragBeforeRewards = new ArrayList<>(rewards);
    }

    private void finishDragUndo() {
        if (dragBeforeTasks == null) return;
        List<QuestTask> beforeTasks = dragBeforeTasks;
        List<QuestReward> beforeRewards = dragBeforeRewards;
        dragBeforeTasks = null;
        dragBeforeRewards = null;
        registerUndoRedo(beforeTasks, beforeRewards, new ArrayList<>(tasks), new ArrayList<>(rewards));
    }

    private void registerUndoRedo(List<QuestTask> beforeTasks, List<QuestReward> beforeRewards,
                                  List<QuestTask> afterTasks, List<QuestReward> afterRewards) {
        undoRedo.push(
                () -> {
                    tasks.clear();
                    tasks.addAll(beforeTasks);
                    rewards.clear();
                    rewards.addAll(beforeRewards);
                    editingTaskIndex = -1;
                    editingRewardIndex = -1;
                    rebuildWidgets();
                },
                () -> {
                    tasks.clear();
                    tasks.addAll(afterTasks);
                    rewards.clear();
                    rewards.addAll(afterRewards);
                    editingTaskIndex = -1;
                    editingRewardIndex = -1;
                    rebuildWidgets();
                });
    }

    private void undoLastChange() {
        undoRedo.undo();
    }

    private void redoLastChange() {
        undoRedo.redo();
    }

    private boolean applyPickedItemFilter(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFilterTokenItem)) return false;
        IItemFilter filter = ItemFilterTokenItem.getFilter(stack);
        if (filter == null) return false;
        taskType = "filter_item";
        pendingPickedItemFilter = filter;
        pendingTaskTarget = filter.describe();
        forcePendingTaskValues = true;
        rebuildWidgets();
        return true;
    }

    private void applyHeldItemFilter() {
        if (minecraft == null || minecraft.player == null) return;
        if (applyPickedItemFilter(minecraft.player.getMainHandItem())) return;
        applyPickedItemFilter(minecraft.player.getOffhandItem());
    }

    private void applyHeldFluidFilter() {
        if (minecraft == null || minecraft.player == null) return;
        ItemStack held = minecraft.player.getMainHandItem();
        IFluidFilter filter = held.getItem() instanceof FluidFilterTokenItem ?
                FluidFilterTokenItem.getFilter(held) : null;
        if (filter == null) {
            held = minecraft.player.getOffhandItem();
            filter = held.getItem() instanceof FluidFilterTokenItem ? FluidFilterTokenItem.getFilter(held) : null;
        }
        if (filter == null) return;
        taskType = "filter_fluid";
        pendingPickedFluidFilter = filter;
        pendingTaskTarget = filter.describe();
        forcePendingTaskValues = true;
        rebuildWidgets();
    }

    private void commitTaskFromForm() {
        String desc = taskDescBox != null ? taskDescBox.getValue().trim() : "";
        String target = taskTargetBox != null ? taskTargetBox.getValue().trim() : "";
        String second = taskSecondaryBox != null ? taskSecondaryBox.getValue().trim() : "";
        String countS = taskCountBox != null ? taskCountBox.getValue().trim() : "1";
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countS));
        } catch (NumberFormatException ignored) {}

        if (desc.isEmpty() && taskType.equals("item_check") && !target.isEmpty()) {
            try {
                Item targetItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(target));
                if (targetItem != null) {
                    String itemName = new net.minecraft.world.item.ItemStack(targetItem).getHoverName().getString();
                    desc = count > 1 ? "Collect " + count + "x " + itemName : "Collect " + itemName;
                    if (taskDescBox != null) taskDescBox.setValue(desc);
                }
            } catch (Exception ignored) {}
        }

        boolean needsTarget = !taskType.equals("experience") && !taskType.equals("dimension") &&
                !taskType.equals("checkmark") && !taskType.equals("timer");
        if (desc.isEmpty() || (needsTarget && !taskType.equals("info") && target.isEmpty())) return;

        ResourceLocation taskId = (editingTaskIndex >= 0 && editingTaskIndex < tasks.size()) ?
                tasks.get(editingTaskIndex).getTaskId() :
                ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "task_" + taskType + "_" +
                        java.util.UUID.randomUUID().toString().replace("-", ""));
        Component descComp = Component.literal(desc);
        QuestTask task = null;
        try {
            task = switch (taskType) {
                case "kill_entity" -> new KillEntityTask(taskId, descComp, ResourceLocation.parse(target), count,
                        taskConsume);
                case "item_check" -> {
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(target));
                    if (item == null) yield null;
                    ItemRequirementTask irt = new ItemRequirementTask(taskId, descComp, item, count, taskConsume);
                    String nbtStr = taskNbtBox != null ? taskNbtBox.getValue().trim() : "";
                    if (!nbtStr.isEmpty()) {
                        try {
                            irt.setNbtFilter(net.minecraft.nbt.TagParser.parseTag(nbtStr));
                        } catch (Exception e) {}
                    }
                    irt.setCheckAe2Storage(taskCheckAe2Storage);
                    yield irt;
                }
                case "craft_item" -> new CraftItemTask(taskId, descComp, ResourceLocation.parse(target), count);
                case "experience" -> new ExperienceTask(taskId, descComp, count);
                case "location_terminal" -> new LocationOrTerminalTask(taskId, descComp, ResourceLocation.parse(target),
                        taskConsume);
                case "advancement" -> new AdvancementTask(taskId, descComp, ResourceLocation.parse(target));
                case "filter_item" -> {
                    IItemFilter filter = pendingPickedItemFilter;
                    if (filter == null) {
                        List<IItemFilter> alts = new ArrayList<>();
                        for (String part : target.split(";")) {
                            String id = part.trim();
                            if (id.isEmpty()) continue;
                            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
                            if (item != null && item != Items.AIR) alts.add(ItemFilters.exact(item));
                        }
                        if (alts.isEmpty()) yield null;
                        filter = alts.size() == 1 ? alts.get(0) : ItemFilters.anyOf(alts.toArray(new IItemFilter[0]));
                    }
                    FilterItemTask fit = new FilterItemTask(taskId, descComp, filter, count, taskConsume);
                    fit.setCheckAe2Storage(taskCheckAe2Storage);
                    yield fit;
                }
                case "filter_fluid" -> {
                    IFluidFilter filter = pendingPickedFluidFilter;
                    if (filter == null) {
                        List<IFluidFilter> alts = new ArrayList<>();
                        for (String part : target.split(";")) {
                            String id = part.trim();
                            if (id.isEmpty()) continue;
                            var fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(id));
                            if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY)
                                alts.add(net.phoenixvine.chronicles.filter.FluidFilters
                                        .exact(ResourceLocation.parse(id)));
                        }
                        if (alts.isEmpty()) yield null;
                        filter = alts.size() == 1 ? alts.get(0) :
                                net.phoenixvine.chronicles.filter.FluidFilters
                                        .anyOf(alts.toArray(new IFluidFilter[0]));
                    }
                    FilterFluidTask fft = new FilterFluidTask(taskId, descComp, filter, count, taskConsume);
                    fft.setCheckAe2Storage(taskCheckAe2Storage);
                    yield fft;
                }
                case "block_interact" -> {
                    var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(target));
                    String mode = second.isEmpty() ? "PLACE" : second.toUpperCase();
                    yield block != null ? new BlockInteractTask(taskId, descComp, block, mode) : null;
                }
                case "block_break" -> {
                    var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(target));
                    yield block != null ? new BlockBreakTask(taskId, descComp, block, count) : null;
                }
                case "enchantment" -> new EnchantmentTask(taskId, descComp, ResourceLocation.parse(target), count);
                case "fluid_check" -> {
                    FluidRequirementTask frt = new FluidRequirementTask(taskId, descComp,
                            ResourceLocation.parse(target),
                            count, taskConsume);
                    frt.setCheckAe2Storage(taskCheckAe2Storage);
                    yield frt;
                }
                case "stat" -> new StatTrackerTask(taskId, descComp, ResourceLocation.parse(target), count,
                        taskConsume);
                case "dimension" -> {
                    String dim = second.isEmpty() ? "minecraft:overworld" : second;
                    yield new DimensionTask(taskId, descComp,
                            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
                }
                case "biome" -> new BiomeTask(taskId, descComp, ResourceLocation.parse(target));
                case "structure" -> new StructureTask(taskId, descComp, ResourceLocation.parse(target));
                case "checkmark" -> new CheckmarkTask(taskId, descComp);
                case "timer" -> new TimerTask(taskId, descComp, count);
                case "tag_item" -> new TagItemTask(taskId, descComp, ItemTags.create(ResourceLocation.parse(target)),
                        count);
                case "info" -> new InfoTask(taskId, descComp, target);
                case "external_trigger" -> new ExternalTriggerTask(taskId, descComp, target, count);
                case "view_machine" -> new net.phoenixvine.chronicles.tasks.ViewMachineTask(taskId, descComp, target,
                        (float) count);
                case "view_scene" -> new net.phoenixvine.chronicles.tasks.ViewSceneTask(taskId, descComp, target,
                        (float) count);
                case "view_guide" -> new net.phoenixvine.chronicles.tasks.ViewGuideTask(taskId, descComp, target);
                case "energy_check" -> {
                    var eType = EnergyStorageTask.EnergyType.FE;
                    if (!target.isBlank()) {
                        try {
                            eType = EnergyStorageTask.EnergyType.valueOf(target.trim().toUpperCase());
                        } catch (Exception ignored2) {}
                    }
                    var eSrc = EnergyStorageTask.Source.INVENTORY;
                    if (!second.isBlank()) {
                        try {
                            eSrc = EnergyStorageTask.Source.valueOf(second.trim().toUpperCase());
                        } catch (Exception ignored2) {}
                    }
                    yield new EnergyStorageTask(taskId, descComp, (long) count, eType, eSrc);
                }
                default -> {
                    PhoenixTaskRegistry.TaskEntry re = PhoenixTaskRegistry.get(taskType);
                    if (re != null) {
                        ExternalTriggerTask ext = new ExternalTriggerTask(taskId, descComp, target, count);
                        ext.setKjsTypeId(taskType);
                        yield ext;
                    }
                    yield null;
                }
            };
        } catch (Exception ignored) {}

        if (task != null) {
            task.setOptional(taskOptional);
            applyStickyIfSupported(task, taskSticky);
            QuestTask finalTask = task;
            pushUndo(() -> {
                if (editingTaskIndex >= 0 && editingTaskIndex < tasks.size()) {
                    tasks.set(editingTaskIndex, finalTask);
                } else {
                    tasks.add(finalTask);
                }
                editingTaskIndex = -1;
            });
            taskTypeDropOpen = false;
            taskOptional = false;
            taskSticky = true;
            taskConsume = false;
            taskCheckAe2Storage = AE2Compat.isAvailable();
            pendingTaskDesc = pendingTaskTarget = pendingTaskSecondary = pendingTaskNbt = "";
            pendingTaskCount = "1";
            pendingPickedItemFilter = null;
            pendingPickedFluidFilter = null;
            forcePendingTaskValues = true;
            rebuildWidgets();
        }
    }

    private void startEditingTask(int idx) {
        if (idx < 0 || idx >= tasks.size()) return;
        pendingPickedItemFilter = null;
        pendingPickedFluidFilter = null;
        QuestTask t = tasks.get(idx);
        editingTaskIndex = idx;
        taskType = taskTypeIdFor(t);
        taskOptional = t.isOptional();
        taskConsume = true;
        taskSticky = true;
        taskCheckAe2Storage = AE2Compat.isAvailable();
        if (t instanceof ItemRequirementTask x) taskSticky = x.isSticky();
        else if (t instanceof TagItemTask x) taskSticky = x.isSticky();
        else if (t instanceof FilterItemTask x) taskSticky = x.isSticky();
        else if (t instanceof FluidRequirementTask x) taskSticky = x.isSticky();
        else if (t instanceof FilterFluidTask x) taskSticky = x.isSticky();
        else if (t instanceof EnergyStorageTask x) taskSticky = x.isSticky();
        if (t instanceof ItemRequirementTask x) taskCheckAe2Storage = x.isCheckAe2Storage();
        else if (t instanceof FluidRequirementTask x) taskCheckAe2Storage = x.isCheckAe2Storage();
        else if (t instanceof FilterItemTask x) taskCheckAe2Storage = x.isCheckAe2Storage();
        else if (t instanceof FilterFluidTask x) taskCheckAe2Storage = x.isCheckAe2Storage();
        pendingTaskDesc = t.getDescriptionRaw().getString();
        pendingTaskTarget = "";
        pendingTaskSecondary = "";
        pendingTaskCount = "1";
        pendingTaskNbt = "";

        if (t instanceof KillEntityTask kt) {
            pendingTaskTarget = kt.getEntityId().toString();
            pendingTaskCount = String.valueOf(kt.getRequiredCount());
            taskConsume = kt.shouldConsume();
        } else if (t instanceof ItemRequirementTask it) {
            ResourceLocation id = it.getItem() != null ? ForgeRegistries.ITEMS.getKey(it.getItem()) : null;
            pendingTaskTarget = id != null ? id.toString() : "";
            pendingTaskCount = String.valueOf(it.getRequiredCount());
            taskConsume = it.shouldConsume();
            pendingTaskNbt = it.getNbtFilter() != null ? it.getNbtFilter().toString() : "";
        } else if (t instanceof CraftItemTask ct) {
            pendingTaskTarget = ct.getItemId().toString();
            pendingTaskCount = String.valueOf(ct.getRequiredCount());
        } else if (t instanceof ExperienceTask et) {
            pendingTaskCount = String.valueOf(et.getRequiredLevel());
        } else if (t instanceof LocationOrTerminalTask lt) {
            pendingTaskTarget = lt.getTargetTerminalId().toString();
            taskConsume = lt.shouldConsume();
        } else if (t instanceof AdvancementTask at) {
            pendingTaskTarget = at.getAdvancementId().toString();
        } else if (t instanceof BlockInteractTask bit) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(bit.getTargetBlock());
            pendingTaskTarget = id != null ? id.toString() : "";
            pendingTaskSecondary = bit.getMode();
        } else if (t instanceof BlockBreakTask bbt) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(bbt.getTargetBlock());
            pendingTaskTarget = id != null ? id.toString() : "";
            pendingTaskCount = String.valueOf(bbt.getRequired());
        } else if (t instanceof EnchantmentTask ent) {
            pendingTaskTarget = ent.getEnchantmentId().toString();
            pendingTaskCount = String.valueOf(ent.getRequiredLevel());
        } else if (t instanceof FluidRequirementTask ft) {
            pendingTaskTarget = ft.getFluidId().toString();
            pendingTaskCount = String.valueOf(ft.getRequiredAmount());
            taskConsume = ft.shouldConsume();
        } else if (t instanceof StatTrackerTask st) {
            pendingTaskTarget = st.getStatId().toString();
            pendingTaskCount = String.valueOf(st.getTargetValue());
            taskConsume = st.shouldConsume();
        } else if (t instanceof DimensionTask dt) {
            pendingTaskSecondary = dt.getTargetDimension().location().toString();
        } else if (t instanceof BiomeTask biot) {
            pendingTaskTarget = biot.getBiomeId().toString();
        } else if (t instanceof StructureTask strt) {
            pendingTaskTarget = strt.getStructureId().toString();
        } else if (t instanceof TagItemTask tit) {
            pendingTaskTarget = tit.getTag().location().toString();
            pendingTaskCount = String.valueOf(tit.getRequired());
        } else if (t instanceof InfoTask ift) {
            pendingTaskTarget = ift.getBody();
        } else if (t instanceof TimerTask timt) {
            pendingTaskCount = String.valueOf(timt.getDurationSeconds());
        } else if (t instanceof net.phoenixvine.chronicles.tasks.ViewMachineTask vmt) {
            pendingTaskTarget = vmt.getMachineId();
            pendingTaskCount = String.valueOf((int) vmt.getMinSeconds());
        } else if (t instanceof net.phoenixvine.chronicles.tasks.ViewSceneTask vst) {
            pendingTaskTarget = vst.getSceneId();
            pendingTaskCount = String.valueOf((int) vst.getMinSeconds());
        } else if (t instanceof net.phoenixvine.chronicles.tasks.ViewGuideTask vgt) {
            pendingTaskTarget = vgt.getGuideId();
        } else if (t instanceof EnergyStorageTask est) {
            pendingTaskTarget = est.getEnergyType().name();
            pendingTaskSecondary = est.getSource().name();
            pendingTaskCount = String.valueOf(est.getRequiredEnergy());
        } else if (t instanceof ExternalTriggerTask xt) {
            pendingTaskTarget = xt.getTriggerId();
            pendingTaskCount = String.valueOf(xt.getRequired());
        } else if (t instanceof FilterItemTask fit) {
            pendingTaskTarget = describeItemFilterAsIdList(fit.getFilter());
            pendingTaskCount = String.valueOf(fit.getCount());
            taskConsume = fit.isConsume();
        } else if (t instanceof FilterFluidTask fft) {
            pendingTaskTarget = describeFluidFilterAsIdList(fft.getFilter());
            pendingTaskCount = String.valueOf(fft.getAmount());
            taskConsume = fft.isConsume();
        }

        forcePendingTaskValues = true;
        taskTypeDropOpen = false;
        rewardTypeDropOpen = false;
        rebuildWidgets();
    }

    private static String describeItemFilterAsIdList(IItemFilter f) {
        if (f instanceof ItemFilters.ExactItem ex) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(ex.item());
            return id != null ? id.toString() : "";
        }
        if (f instanceof ItemFilters.AnyOf any) {
            List<String> ids = new ArrayList<>();
            for (IItemFilter child : any.children()) {
                String s = describeItemFilterAsIdList(child);
                if (!s.isEmpty()) ids.add(s);
            }
            return String.join(";", ids);
        }
        return f.describe();
    }

    private static String describeFluidFilterAsIdList(net.phoenixvine.chronicles.filter.IFluidFilter f) {
        if (f instanceof net.phoenixvine.chronicles.filter.FluidFilters.ExactFluid ex) {
            return ex.fluidId().toString();
        }
        if (f instanceof net.phoenixvine.chronicles.filter.FluidFilters.AnyOf any) {
            List<String> ids = new ArrayList<>();
            for (var child : any.children()) {
                String s = describeFluidFilterAsIdList(child);
                if (!s.isEmpty()) ids.add(s);
            }
            return String.join(";", ids);
        }
        return f.describe();
    }

    private static void applyStickyIfSupported(QuestTask t, boolean sticky) {
        if (t instanceof ItemRequirementTask x) x.setSticky(sticky);
        else if (t instanceof TagItemTask x) x.setSticky(sticky);
        else if (t instanceof FilterItemTask x) x.setSticky(sticky);
        else if (t instanceof FluidRequirementTask x) x.setSticky(sticky);
        else if (t instanceof FilterFluidTask x) x.setSticky(sticky);
        else if (t instanceof EnergyStorageTask x) x.setSticky(sticky);
    }

    private static String taskTypeIdFor(QuestTask t) {
        if (t instanceof ExternalTriggerTask ext)
            return ext.getKjsTypeId() != null ? ext.getKjsTypeId() : "external_trigger";
        if (t instanceof KillEntityTask) return "kill_entity";
        if (t instanceof ItemRequirementTask) return "item_check";
        if (t instanceof CraftItemTask) return "craft_item";
        if (t instanceof ExperienceTask) return "experience";
        if (t instanceof LocationOrTerminalTask) return "location_terminal";
        if (t instanceof AdvancementTask) return "advancement";
        if (t instanceof BlockInteractTask) return "block_interact";
        if (t instanceof BlockBreakTask) return "block_break";
        if (t instanceof EnchantmentTask) return "enchantment";
        if (t instanceof FluidRequirementTask) return "fluid_check";
        if (t instanceof StatTrackerTask) return "stat";
        if (t instanceof DimensionTask) return "dimension";
        if (t instanceof BiomeTask) return "biome";
        if (t instanceof StructureTask) return "structure";
        if (t instanceof CheckmarkTask) return "checkmark";
        if (t instanceof TimerTask) return "timer";
        if (t instanceof TagItemTask) return "tag_item";
        if (t instanceof InfoTask) return "info";
        if (t instanceof net.phoenixvine.chronicles.tasks.ViewMachineTask) return "view_machine";
        if (t instanceof net.phoenixvine.chronicles.tasks.ViewSceneTask) return "view_scene";
        if (t instanceof net.phoenixvine.chronicles.tasks.ViewGuideTask) return "view_guide";
        if (t instanceof EnergyStorageTask) return "energy_check";
        if (t instanceof FilterItemTask) return "filter_item";
        if (t instanceof FilterFluidTask) return "filter_fluid";
        return "checkmark";
    }

    private void cancelTaskEdit() {
        editingTaskIndex = -1;
        taskOptional = false;
        taskCheckAe2Storage = AE2Compat.isAvailable();
        pendingTaskDesc = pendingTaskTarget = pendingTaskSecondary = pendingTaskCount = pendingTaskNbt = "";
        pendingPickedItemFilter = null;
        pendingPickedFluidFilter = null;
        forcePendingTaskValues = true;
        rebuildWidgets();
    }

    private void commitRewardFromForm() {
        String countS = rewardCountBox != null ? rewardCountBox.getValue().trim() : "1";
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countS));
        } catch (NumberFormatException ignored) {}

        QuestReward reward = switch (rewardType) {
            case "item" -> rewardPickedItem != null ?
                    new QuestReward.ItemReward(rewardPickedItem.getItem(), count, rewardPickedItem.getTag()) : null;
            case "xp" -> new QuestReward.XPReward(count);
            case "command" -> {
                String cmd = rewardCommandBox != null ? rewardCommandBox.getValue().trim() : "";
                yield cmd.isEmpty() ? null : new QuestReward.CommandReward(cmd);
            }
            case "loot_table" -> {
                String lt = rewardCommandBox != null ? rewardCommandBox.getValue().trim() : "";
                yield lt.isEmpty() ? null : new QuestReward.LootTableReward(ResourceLocation.parse(lt));
            }
            case "reward_table" -> {
                String tid = rewardCommandBox != null ? rewardCommandBox.getValue().trim() : "";
                yield tid.isEmpty() ? null : new QuestReward.RewardTableReward(tid);
            }
            case "script_event" -> {
                String eid = rewardCommandBox != null ? rewardCommandBox.getValue().trim() : "";
                if (eid.isEmpty()) yield null;
                net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
                if (rewardEventDataBox != null && !rewardEventDataBox.getValue().isBlank()) {
                    try {
                        data = net.minecraft.nbt.TagParser.parseTag(rewardEventDataBox.getValue().trim());
                    } catch (Exception ignored) {}
                }
                yield new QuestReward.ScriptEventReward(eid, data);
            }
            case "choice_box" -> boxOptions.isEmpty() ? null :
                    new QuestReward.ChoiceBoxReward(new ArrayList<>(boxOptions), boxMode);
            default -> null;
        };

        if (reward != null) {
            pushUndo(() -> {
                if (editingRewardIndex >= 0 && editingRewardIndex < rewards.size()) {
                    rewards.set(editingRewardIndex, reward);
                } else {
                    rewards.add(reward);
                }
                editingRewardIndex = -1;
            });
            rewardPickedItem = null;
            rewardTypeDropOpen = false;
            pendingRewardCount = pendingRewardCommand = pendingRewardEventData = "";
            boxOptions.clear();
            boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
            editingBoxOptionIndex = -1;
            boxOptionPickedItem = null;
            forcePendingRewardValues = true;
            rebuildWidgets();
        }
    }

    private void startEditingReward(int idx) {
        if (idx < 0 || idx >= rewards.size()) return;
        QuestReward r = rewards.get(idx);
        rewardPickedItem = null;
        pendingRewardCount = "1";
        pendingRewardCommand = "";
        pendingRewardEventData = "";
        boxOptions.clear();
        boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
        editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;

        if (r instanceof QuestReward.ItemReward ir) {
            rewardType = "item";
            rewardPickedItem = new ItemStack(ir.getItem(), ir.getCount());
            if (ir.getNbt() != null) rewardPickedItem.setTag(ir.getNbt().copy());
            pendingRewardCount = String.valueOf(ir.getCount());
        } else if (r instanceof QuestReward.XPReward xr) {
            rewardType = "xp";
            pendingRewardCount = String.valueOf(xr.getLevels());
        } else if (r instanceof QuestReward.CommandReward cr) {
            rewardType = "command";
            pendingRewardCommand = cr.getCommand();
        } else if (r instanceof QuestReward.LootTableReward lr) {
            rewardType = "loot_table";
            pendingRewardCommand = lr.getLootTableId().toString();
        } else if (r instanceof QuestReward.RewardTableReward rtr) {
            rewardType = "reward_table";
            pendingRewardCommand = rtr.getTableId();
        } else if (r instanceof QuestReward.ScriptEventReward ser) {
            rewardType = "script_event";
            pendingRewardCommand = ser.getEventId();
            pendingRewardEventData = ser.getData() != null && !ser.getData().isEmpty() ? ser.getData().toString() : "";
        } else if (r instanceof QuestReward.ChoiceBoxReward box) {
            rewardType = "choice_box";
            boxMode = box.getMode();
            boxOptions.addAll(box.getOptions());
        } else {
            return;
        }

        editingRewardIndex = idx;
        forcePendingRewardValues = true;
        taskTypeDropOpen = false;
        rewardTypeDropOpen = false;
        rebuildWidgets();
    }

    private void cancelRewardEdit() {
        editingRewardIndex = -1;
        rewardPickedItem = null;
        pendingRewardCount = pendingRewardCommand = pendingRewardEventData = "";
        boxOptions.clear();
        boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
        editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;
        forcePendingRewardValues = true;
        rebuildWidgets();
    }

    private void startEditingBoxOption(int idx) {
        if (idx < 0 || idx >= boxOptions.size()) return;
        if (!(boxOptions.get(idx) instanceof QuestReward.ItemReward ir)) return;

        editingBoxOptionIndex = idx;
        boxOptionPickedItem = new ItemStack(ir.getItem(), ir.getCount());
        if (ir.getNbt() != null) boxOptionPickedItem.setTag(ir.getNbt().copy());
        pendingBoxOptionCount = String.valueOf(ir.getCount());
        pendingBoxOptionNbt = ir.getNbt() != null && !ir.getNbt().isEmpty() ? ir.getNbt().toString() : "";
        forcePendingBoxOptionValues = true;
        rebuildWidgets();
    }

    private void commitBoxOptionEdit() {
        if (editingBoxOptionIndex < 0 || editingBoxOptionIndex >= boxOptions.size() || boxOptionPickedItem == null) {
            cancelBoxOptionEdit();
            return;
        }

        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(boxOptionCountBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}

        net.minecraft.nbt.CompoundTag nbt = null;
        String nbtStr = boxOptionNbtBox != null ? boxOptionNbtBox.getValue().trim() : "";
        if (!nbtStr.isEmpty()) {
            try {
                nbt = net.minecraft.nbt.TagParser.parseTag(nbtStr);
            } catch (Exception ignored) {}
        }

        boxOptions.set(editingBoxOptionIndex, new QuestReward.ItemReward(boxOptionPickedItem.getItem(), count, nbt));
        cancelBoxOptionEdit();
    }

    private void cancelBoxOptionEdit() {
        editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;
        pendingBoxOptionCount = "1";
        pendingBoxOptionNbt = "";
        forcePendingBoxOptionValues = true;
        rebuildWidgets();
    }

    private void flushToQuestNode() {
        if (variantTarget != null) {
            variantTarget.tasks = new ArrayList<>(tasks);
            variantTarget.rewards = new ArrayList<>(rewards);
        } else {
            questNode.clearTasks();
            for (QuestTask t : tasks) questNode.addTask(t);
            questNode.clearRewards();
            for (QuestReward r : rewards) questNode.addReward(r);
        }

        if (net.phoenixvine.chronicles.registry.QuestTreeRegistry.getQuest(questNode.getId()) == questNode) {
            net.phoenixvine.chronicles.codec.QuestFileSaver.saveOneQuestToDisk(questNode);
            LangSyncScheduler.markDirty();
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int rawMx, int rawMy, float partial) {
        if (width != lastLayoutWidth || height != lastLayoutHeight) init();

        int mx = Math.round(rawMx / uiScale);
        int my = Math.round(rawMy / uiScale);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        g.fill(0, 0, vw, vh, C_BG);

        g.fill(0, 0, vw, HEADER_H, C_HEADER);
        g.fill(0, 0, vw, 2, C_ACCENT);
        g.fill(0, HEADER_H - 1, vw, HEADER_H, C_BORDER);
        String repeatBadge = switch (questNode.getRepeatMode()) {
            case DAILY -> "  §b[Daily]";
            case COOLDOWN -> "  §e[Cooldown " + questNode.getRepeatCooldownHours() + "h]";
            case INFINITE -> "  §a[∞]";
            default -> "";
        };
        String variantBadge = variantTarget != null ? "  §d[variant: " + variantTarget.condition + "]" : "";
        g.drawCenteredString(font,
                "§fTasks & Rewards  §8: §7" + questNode.getId().getPath() + repeatBadge + variantBadge,
                vw / 2, (HEADER_H - 8) / 2, C_TEXT);

        g.fill(0, HEADER_H, vw, listTop - 1, C_PANEL);
        g.fill(0, listTop - 1, vw, listTop, C_BORDER);
        String taskSubHeader;
        if (tasks.isEmpty()) {
            taskSubHeader = "§c⚠ No tasks: quest auto-completes on unlock";
        } else {
            long optCount = tasks.stream().filter(QuestTask::isOptional).count();
            long reqCount = tasks.size() - optCount;
            taskSubHeader = "§8Tasks  §7" + reqCount + (optCount > 0 ? "  §8+  §e" + optCount + " opt" : "");
        }
        g.drawString(font, taskSubHeader, MARGIN + 4, HEADER_H + 6, C_TEXT_FAINT, false);
        if (copiedTaskNBT != null)
            g.drawString(font, "§b[Ctrl+V]", MARGIN + colW - font.width("[Ctrl+V]") - 4, HEADER_H + 6, 0xFF55BBFF,
                    false);
        g.drawString(font, "§8Rewards  §7" + rewards.size(), splitX + 4, HEADER_H + 6, C_TEXT_FAINT, false);

        g.fill(splitX - COL_GAP / 2, HEADER_H, splitX - COL_GAP / 2 + 1, vh - FOOTER_H, C_SPLIT);

        int formPanelTop = formTop - 20;
        g.fill(0, formPanelTop, vw, formBottom, C_PANEL);
        g.fill(0, formPanelTop, vw, formPanelTop + 1, C_BORDER);

        g.fill(MARGIN, formPanelTop + 2, MARGIN + colW, formBottom - 2, C_FORM_BG);
        drawBorder(g, MARGIN, formPanelTop + 2, colW, formBottom - 2 - (formPanelTop + 2), C_BORDER);
        g.fill(splitX, formPanelTop + 2, splitX + colW, formBottom - 2, C_FORM_BG);
        drawBorder(g, splitX, formPanelTop + 2, colW, formBottom - 2 - (formPanelTop + 2), C_BORDER);
        g.drawString(font, editingTaskIndex >= 0 ? "§b✎ Editing Task (right-click to cancel)" : "§8Add Task",
                MARGIN + 6, formPanelTop + 6, C_TEXT_FAINT, false);
        g.drawString(font,
                editingRewardIndex >= 0 ? "§b✎ Editing Reward (right-click to cancel)" : "§8Add Reward",
                splitX + 6, formPanelTop + 6, C_TEXT_FAINT, false);

        if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0) renderBoxOptionsList(g, mx, my);

        g.fill(0, vh - FOOTER_H, vw, vh, C_HEADER);
        g.fill(0, vh - FOOTER_H, vw, vh - FOOTER_H + 1, C_BORDER);

        hoveredTaskRow = -1;
        int ty = listTop;
        for (int i = 0; i < tasks.size(); i++) {
            QuestTask task = tasks.get(i);
            if (ty + ROW_H > listBottom) break;
            boolean hov = mx >= MARGIN && mx < splitX - COL_GAP && my >= ty && my < ty + ROW_H;
            if (hov) {
                g.fill(MARGIN, ty, splitX - COL_GAP, ty + ROW_H, C_ROW_HOVER);
                hoveredTaskRow = i;
            }
            if (draggingTaskIndex == i) drawBorder(g, MARGIN, ty, splitX - COL_GAP - MARGIN, ROW_H, 0xFF55DD55);

            g.fill(MARGIN, ty + 2, MARGIN + 2, ty + ROW_H - 2,
                    task.isOptional() ? 0xFF22AA55 : C_ACCENT);
            PhoenixTaskRegistry.TaskEntry meta = getTaskMetaByClass(task);
            ItemStack taskIcon = getTaskIconStack(task);
            int textX = MARGIN + 5;
            if (!taskIcon.isEmpty()) {
                g.renderItem(taskIcon, textX, ty + 4);
                textX += 18;
            } else if (meta != null && meta.editorIcon() != null) {
                g.drawString(font, meta.editorIcon(), textX, ty + 9, 0xFFFFFFFF, false);
                textX += 10;
            }
            int maxW = (splitX - COL_GAP) - textX - (hov ? 34 : 6);
            String rawLabel = task.getDescription().getString();
            if (task.isOptional()) rawLabel = "[opt] " + rawLabel;
            String detail = getTaskDetailString(task);
            String[] wrapped = wordWrap(rawLabel, maxW);
            String line1Color = task.isOptional() ? "§8" : "§7";
            g.drawString(font, line1Color + wrapped[0], textX, ty + 4, C_TEXT_DIM, false);
            if (wrapped[1] != null) {

                g.drawString(font, "§8" + wrapped[1], textX, ty + 15, C_TEXT_FAINT, false);
            } else if (detail != null) {
                String dl = detail;
                if (font.width(dl) > maxW) dl = font.plainSubstrByWidth(dl, maxW - 4) + "…";
                g.drawString(font, "§8" + dl, textX, ty + 15, C_TEXT_FAINT, false);
            }
            if (hov) {
                g.drawString(font, "§b⧉", splitX - COL_GAP - 26, ty + 9, 0xFF55BBFF, false);
                g.drawString(font, "§c×", splitX - COL_GAP - 12, ty + 9, 0xFFFF5555, false);
            }
            ty += ROW_H;
        }
        if (tasks.isEmpty())
            g.drawString(font, "§8No tasks yet: add one below.", MARGIN + 6, listTop + 5, C_TEXT_FAINT, false);

        hoveredRewardRow = -1;
        rewardDisplayOrder = computeRewardDisplayOrder();
        int tableSectionStart = rewardTableSectionStart(rewardDisplayOrder);
        int ry = listTop;
        for (int pos = 0; pos < rewardDisplayOrder.size(); pos++) {
            if (pos == tableSectionStart) {
                if (ry + ROW_HEADER_H > listBottom) break;
                g.drawString(font, "§6⊞ §8Reward Tables", splitX + 5, ry + (ROW_HEADER_H / 2) - 4, C_TEXT_FAINT,
                        false);
                g.fill(splitX, ry + ROW_HEADER_H - 1, vw - MARGIN, ry + ROW_HEADER_H, C_BORDER);
                ry += ROW_HEADER_H;
            }
            int i = rewardDisplayOrder.get(pos);
            QuestReward reward = rewards.get(i);
            if (ry + ROW_H > listBottom) break;
            boolean hov = mx >= splitX && mx < vw - MARGIN && my >= ry && my < ry + ROW_H;
            if (hov) {
                g.fill(splitX, ry, vw - MARGIN, ry + ROW_H, C_ROW_HOVER);
                hoveredRewardRow = i;
            }
            if (draggingRewardIndex == i) drawBorder(g, splitX, ry, vw - MARGIN - splitX, ROW_H, 0xFF55DD55);

            g.fill(splitX, ry + 2, splitX + 2, ry + ROW_H - 2, C_ACCENT);
            int rewardTextX = splitX + 5;
            if (reward instanceof QuestReward.ItemReward ir) {
                ItemStack stack = new ItemStack(ir.getItem(), ir.getCount());
                g.renderItem(stack, rewardTextX, ry + 4);
                rewardTextX += 18;
                int rmaxW = (vw - MARGIN - (hov ? 16 : 6)) - rewardTextX;
                String rl = "§f" + stack.getHoverName().getString();
                if (font.width(rl) > rmaxW) rl = font.plainSubstrByWidth(rl, rmaxW - 4) + "…";
                g.drawString(font, rl, rewardTextX, ry + 4, C_TEXT_DIM, false);
                g.drawString(font, "§8×" + ir.getCount(), rewardTextX, ry + 15, C_TEXT_FAINT, false);
            } else {
                String icon = switch (reward.getType()) {
                    case XP -> "§a✦";
                    case COMMAND -> "§b◆";
                    case LOOT_TABLE -> "§d❋";
                    case SCRIPT_EVENT -> "§e⚡";
                    case REWARD_TABLE -> "§6⊞";
                    default -> "§8?";
                };
                String typeLine = switch (reward.getType()) {
                    case XP -> "§8XP";
                    case COMMAND -> "§8command";
                    case LOOT_TABLE -> "§8loot table";
                    case SCRIPT_EVENT -> "§8script event";
                    case REWARD_TABLE -> "§8reward table";
                    default -> "§8reward";
                };
                int rmaxW = (vw - MARGIN - (hov ? 16 : 6)) - rewardTextX - font.width(icon) - 4;
                String rl = reward.getSummary().getString();
                String[] rwrapped = wordWrap(rl, rmaxW);
                g.drawString(font, icon + " §7" + rwrapped[0], rewardTextX, ry + 4, C_TEXT_DIM, false);
                g.drawString(font, rwrapped[1] != null ? "§8" + rwrapped[1] : typeLine,
                        rewardTextX, ry + 15, C_TEXT_FAINT, false);
            }
            if (hov) g.drawString(font, "§c×", vw - MARGIN - 12, ry + 9, 0xFFFF5555, false);
            ry += ROW_H;
        }
        if (rewards.isEmpty())
            g.drawString(font, "§8No rewards yet: add one below.", splitX + 6, listTop + 5, C_TEXT_FAINT, false);

        super.render(g, mx, my, partial);

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.flush();

        if (taskTypeDropOpen) {
            List<PhoenixTaskRegistry.TaskEntry> editorTypes = PhoenixTaskRegistry.getEditorTypes();
            int rowH = FIELD_H;
            int totalH = editorTypes.size() * rowH;
            int maxDropH = Math.max(rowH, (formTop - 2) - listTop);
            int dropH = Math.min(totalH, maxDropH);
            int dy = Math.max(listTop, formTop - dropH - 2);
            int maxScroll = Math.max(0, totalH - dropH);
            taskTypeDropScroll = Math.max(0, Math.min(taskTypeDropScroll, maxScroll));

            g.fill(MARGIN, dy, MARGIN + colW, dy + dropH, C_PANEL);
            drawBorder(g, MARGIN, dy, colW, dropH, C_ACCENT);

            hoveredDropRow = -1;
            for (int i = 0; i < editorTypes.size(); i++) {
                int dropRowY = dy - taskTypeDropScroll + i * rowH;
                if (dropRowY + rowH <= dy || dropRowY >= dy + dropH) continue;
                PhoenixTaskRegistry.TaskEntry m = editorTypes.get(i);
                boolean hov = mx >= MARGIN && mx < MARGIN + colW && my >= dropRowY && my < dropRowY + rowH &&
                        my >= dy && my < dy + dropH;
                if (hov) {
                    g.fill(MARGIN + 1, dropRowY, MARGIN + colW - 1, dropRowY + rowH, 0xFF1E1E2A);
                    hoveredDropRow = i;
                }
                g.drawString(font, m.editorIcon() + " §7" + m.editorLabel(), MARGIN + 5, dropRowY + 3,
                        hov ? C_TEXT : C_TEXT_DIM, false);
            }
            ChroniclesThemeRenderer.drawScrollbar(g, MARGIN + colW, dy, dy + dropH, taskTypeDropScroll, totalH);

            if (hoveredDropRow >= 0 && hoveredDropRow < editorTypes.size()) {
                PhoenixTaskRegistry.TaskEntry hm = editorTypes.get(hoveredDropRow);
                String tooltip = hm.editorTooltip() != null ? hm.editorTooltip() : hm.editorLabel();
                int maxTipTextW = 220;
                String[] rawLines = tooltip.split("\n");
                List<net.minecraft.util.FormattedCharSequence> wrappedLines = new ArrayList<>();
                for (int rli = 0; rli < rawLines.length; rli++) {
                    Component lineComp = Component.literal((rli == 0 ? "§f" : "§8") + rawLines[rli]);
                    wrappedLines.addAll(font.split(lineComp, maxTipTextW));
                }
                int maxLw = 0;
                for (var l : wrappedLines) maxLw = Math.max(maxLw, font.width(l));
                int tipW = maxLw + 10, tipH = wrappedLines.size() * 10 + 6;
                int tipX = MARGIN + colW + 4;
                int dropRowY = dy - taskTypeDropScroll + hoveredDropRow * rowH;
                int tipY = Math.min(Math.max(dropRowY, 2), vh - tipH - 2);
                if (tipX + tipW > vw - 2) tipX = Math.max(2, vw - 2 - tipW);
                g.fill(tipX, tipY, tipX + tipW, tipY + tipH, C_TOOLTIP_BG);
                drawBorder(g, tipX, tipY, tipW, tipH, C_ACCENT);
                for (int li = 0; li < wrappedLines.size(); li++)
                    g.drawString(font, wrappedLines.get(li), tipX + 5, tipY + 3 + li * 10, 0xFFFFFFFF, false);
            }
        }

        if (rewardTypeDropOpen) {
            int rowH = FIELD_H;
            int dropH = REWARD_TYPES.length * rowH;
            int dy = Math.max(listTop, formTop - dropH - 2);
            g.fill(splitX, dy, splitX + colW, dy + dropH, C_PANEL);
            drawBorder(g, splitX, dy, colW, dropH, C_ACCENT);
            for (int i = 0; i < REWARD_TYPES.length; i++) {
                int dropRowY = dy + i * rowH;
                boolean hov = mx >= splitX && mx < splitX + colW && my >= dropRowY && my < dropRowY + rowH;
                if (hov) g.fill(splitX + 1, dropRowY, splitX + colW - 1, dropRowY + rowH, 0xFF1E1E2A);
                g.drawString(font, "§7" + rewardTypeLabel(REWARD_TYPES[i]), splitX + 5, dropRowY + 3,
                        hov ? C_TEXT : C_TEXT_DIM, false);
            }
        }

        g.pose().popPose();
        g.pose().popPose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & 2) != 0;
        boolean shift = (mods & 1) != 0;
        if (ctrl && key == 90 && !shift) {
            undoLastChange();
            return true;
        }
        if (ctrl && (key == 89 || (key == 90 && shift))) {
            redoLastChange();
            return true;
        }
        if (ctrl && key == 86 && copiedTaskNBT != null) {
            QuestTask pasted = deserializeTask(copiedTaskNBT.copy());
            if (pasted != null) {
                pasted = retaskId(pasted, "task_paste_" + java.util.UUID.randomUUID().toString().replace("-", ""));
                tasks.add(pasted);
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double rawMx, double rawMy, double delta) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (taskTypeDropOpen) {
            List<PhoenixTaskRegistry.TaskEntry> edTypes = PhoenixTaskRegistry.getEditorTypes();
            int totalH = edTypes.size() * FIELD_H;
            int maxDropH = Math.max(FIELD_H, (formTop - 2) - listTop);
            int dropH = Math.min(totalH, maxDropH);
            int maxScroll = Math.max(0, totalH - dropH);
            taskTypeDropScroll = Math.max(0, Math.min(maxScroll, (int) (taskTypeDropScroll - delta * FIELD_H)));
            return true;
        }
        if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0 && mx >= boxOptionsListX &&
                mx < boxOptionsListX + boxOptionsListW && my >= boxOptionsListY && my < boxOptionsListBottom) {
            int visibleRows = Math.max(1, (boxOptionsListBottom - boxOptionsListY) / BOX_OPTION_ROW_H);
            int maxScroll = Math.max(0, boxOptions.size() - visibleRows);
            boxOptionsScroll = Math.max(0, Math.min(maxScroll, boxOptionsScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double rawMx, double rawMy, int btn) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (btn == 0) {
            if (taskTypeDropOpen) {
                List<PhoenixTaskRegistry.TaskEntry> edTypes = PhoenixTaskRegistry.getEditorTypes();
                int totalH = edTypes.size() * FIELD_H;
                int maxDropH = Math.max(FIELD_H, (formTop - 2) - listTop);
                int dropH = Math.min(totalH, maxDropH);
                int dy = Math.max(listTop, formTop - dropH - 2);
                if (my >= dy && my < dy + dropH) {
                    for (int i = 0; i < edTypes.size(); i++) {
                        int ry2 = dy - taskTypeDropScroll + i * FIELD_H;
                        if (ry2 + FIELD_H <= dy || ry2 >= dy + dropH) continue;
                        if (mx >= MARGIN && mx < MARGIN + colW && my >= ry2 && my < ry2 + FIELD_H) {
                            taskType = edTypes.get(i).typeId();
                            pendingPickedItemFilter = null;
                            pendingPickedFluidFilter = null;
                            taskTypeDropOpen = false;
                            rebuildWidgets();
                            return true;
                        }
                    }
                }
                taskTypeDropOpen = false;
                return true;
            }
            if (rewardTypeDropOpen) {
                int dropH = REWARD_TYPES.length * FIELD_H;
                int dy = Math.max(listTop, formTop - dropH - 2);
                for (int i = 0; i < REWARD_TYPES.length; i++) {
                    int ry2 = dy + i * FIELD_H;
                    if (mx >= splitX && mx < splitX + colW && my >= ry2 && my < ry2 + FIELD_H) {
                        rewardType = REWARD_TYPES[i];
                        rewardTypeDropOpen = false;
                        rebuildWidgets();
                        return true;
                    }
                }
                rewardTypeDropOpen = false;
                return true;
            }
            if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0) {
                for (int i = 0; i < boxOptionRowRects.size(); i++) {
                    int[] r = boxOptionRowRects.get(i);
                    if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                        boxOptions.remove(boxOptionsScroll + i);
                        rebuildWidgets();
                        return true;
                    }
                }
            }

            if (hoveredTaskRow >= 0 && mx >= splitX - COL_GAP - 28 && mx < splitX - COL_GAP - 14) {
                copiedTaskNBT = tasks.get(hoveredTaskRow).serializeNBT();
                return true;
            }

            if (hoveredTaskRow >= 0 && mx >= splitX - COL_GAP - 14 && mx < splitX - COL_GAP) {
                int removeIdx = hoveredTaskRow;
                pushUndo(() -> tasks.remove(removeIdx));
                hoveredTaskRow = -1;
                if (editingTaskIndex >= 0) cancelTaskEdit();
                return true;
            }

            if (hoveredRewardRow >= 0 && mx >= vw - MARGIN - 14 && mx < vw - MARGIN) {
                int removeIdx = hoveredRewardRow;
                pushUndo(() -> rewards.remove(removeIdx));
                hoveredRewardRow = -1;
                if (editingRewardIndex >= 0) cancelRewardEdit();
                return true;
            }

            if (hoveredTaskRow >= 0) {
                draggingTaskIndex = hoveredTaskRow;
                dragMovedTask = false;
                return true;
            }
            if (hoveredRewardRow >= 0) {
                draggingRewardIndex = hoveredRewardRow;
                dragMovedReward = false;
                return true;
            }
        } else if (btn == 1) {

            if (hoveredTaskRow >= 0) {
                if (editingTaskIndex == hoveredTaskRow) cancelTaskEdit();
                else startEditingTask(hoveredTaskRow);
                return true;
            }
            if (hoveredRewardRow >= 0) {
                if (editingRewardIndex == hoveredRewardRow) cancelRewardEdit();
                else startEditingReward(hoveredRewardRow);
                return true;
            }
            if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0) {
                for (int i = 0; i < boxOptionRowRects.size(); i++) {
                    int[] r = boxOptionRowRects.get(i);
                    if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                        startEditingBoxOption(boxOptionsScroll + i);
                        return true;
                    }
                }
            } else if (rewardType.equals("choice_box")) {
                cancelBoxOptionEdit();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private int rowIndexAtY(double my, int count) {
        if (my < listTop) return -1;
        int idx = (int) ((my - listTop) / ROW_H);
        return idx >= 0 && idx < count ? idx : -1;
    }

    private List<Integer> computeRewardDisplayOrder() {
        List<Integer> order = new ArrayList<>(rewards.size());
        for (int i = 0; i < rewards.size(); i++) {
            if (rewards.get(i).getType() != QuestReward.RewardType.REWARD_TABLE) order.add(i);
        }
        for (int i = 0; i < rewards.size(); i++) {
            if (rewards.get(i).getType() == QuestReward.RewardType.REWARD_TABLE) order.add(i);
        }
        return order;
    }

    private int rewardTableSectionStart(List<Integer> order) {
        for (int pos = 0; pos < order.size(); pos++) {
            if (rewards.get(order.get(pos)).getType() == QuestReward.RewardType.REWARD_TABLE) return pos;
        }
        return order.size();
    }

    private int rewardRealIndexAtY(double my) {
        if (my < listTop || rewardDisplayOrder.isEmpty()) return -1;
        int tableSectionStart = rewardTableSectionStart(rewardDisplayOrder);
        int y = listTop;
        for (int pos = 0; pos < rewardDisplayOrder.size(); pos++) {
            if (pos == tableSectionStart) y += ROW_HEADER_H;
            if (my >= y && my < y + ROW_H) return rewardDisplayOrder.get(pos);
            y += ROW_H;
        }
        return -1;
    }

    @Override
    public boolean mouseDragged(double rawMx, double rawMy, int btn, double rawDx, double rawDy) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        double dx = rawDx / uiScale;
        double dy = rawDy / uiScale;
        if (btn == 0 && draggingTaskIndex >= 0) {
            int target = rowIndexAtY(my, tasks.size());
            if (target >= 0 && target != draggingTaskIndex) {
                if (!dragMovedTask) {
                    beginDragUndo();
                    dragMovedTask = true;
                }
                Collections.swap(tasks, draggingTaskIndex, target);
                if (editingTaskIndex == draggingTaskIndex) editingTaskIndex = target;
                else if (editingTaskIndex == target) editingTaskIndex = draggingTaskIndex;
                draggingTaskIndex = target;
            }
            return true;
        }
        if (btn == 0 && draggingRewardIndex >= 0) {
            int target = rewardRealIndexAtY(my);
            if (target >= 0 && target != draggingRewardIndex) {
                if (!dragMovedReward) {
                    beginDragUndo();
                    dragMovedReward = true;
                }
                Collections.swap(rewards, draggingRewardIndex, target);
                if (editingRewardIndex == draggingRewardIndex) editingRewardIndex = target;
                else if (editingRewardIndex == target) editingRewardIndex = draggingRewardIndex;
                draggingRewardIndex = target;
            }
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double rawMx, double rawMy, int btn) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (btn == 0 && (draggingTaskIndex >= 0 || draggingRewardIndex >= 0)) {
            if (dragMovedTask || dragMovedReward) finishDragUndo();
            draggingTaskIndex = -1;
            draggingRewardIndex = -1;
            dragMovedTask = false;
            dragMovedReward = false;
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public void onClose() {
        flushToQuestNode();
        LangSyncScheduler.flushNow();
        ChronicleOverviewScreen.invalidateNodeCachesUpChain(parent, questNode);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Nullable
    private static QuestTask deserializeTask(CompoundTag nbt) {
        QuestTask t = PhoenixTaskRegistry.deserialize(nbt);
        if (t != null) t.setOptional(nbt.getBoolean("optional"));
        return t;
    }

    private static QuestTask retaskId(QuestTask task, String newId) {
        CompoundTag nbt = task.serializeNBT();
        nbt.putString("task_id", "phoenix_chronicles:" + newId);
        QuestTask copy = deserializeTask(nbt);
        return copy != null ? copy : task;
    }

    private PhoenixTaskRegistry.TaskEntry getTaskMeta(String typeId) {
        PhoenixTaskRegistry.TaskEntry e = PhoenixTaskRegistry.get(typeId);
        List<PhoenixTaskRegistry.TaskEntry> all = PhoenixTaskRegistry.getEditorTypes();
        return e != null ? e : (all.isEmpty() ? null : all.get(0));
    }

    private PhoenixTaskRegistry.TaskEntry getTaskMetaByClass(QuestTask task) {
        try {
            String typeId = task.serializeNBT().getString("type");
            return getTaskMeta(typeId);
        } catch (Exception ignored) {}
        List<PhoenixTaskRegistry.TaskEntry> all = PhoenixTaskRegistry.getEditorTypes();
        return all.isEmpty() ? null : all.get(0);
    }

    private ItemStack getTaskIconStack(QuestTask task) {
        ResourceLocation id = task.getDisplayItemId();
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);

        if (task instanceof ItemRequirementTask t && t.getNbtFilter() != null && !t.getNbtFilter().isEmpty()) {
            stack.setTag(t.getNbtFilter().copy());
        }
        return stack;
    }

    @Nullable
    private String getTaskDetailString(QuestTask task) {
        if (task instanceof ItemRequirementTask t)
            return t.getItem() != null ?
                    t.getItem().getDefaultInstance().getHoverName().getString() + " ×" + t.getRequiredCount() : null;
        if (task instanceof CraftItemTask t) {
            Item item = ForgeRegistries.ITEMS.getValue(t.getItemId());
            return item != null ? item.getDefaultInstance().getHoverName().getString() + " ×" + t.getRequiredCount() :
                    t.getItemId().toString();
        }
        if (task instanceof KillEntityTask t)
            return t.getEntityId().getPath().replace('_', ' ') + " ×" + t.getRequiredCount();
        if (task instanceof FluidRequirementTask t)
            return t.getFluidId().getPath().replace('_', ' ') + "  " + t.getRequiredAmount() + " mB";
        if (task instanceof ExperienceTask t) return "Level " + t.getRequiredLevel();
        if (task instanceof TagItemTask t) return "#" + t.getTag().location().getPath() + " ×" + t.getRequired();
        return null;
    }

    private String[] wordWrap(String text, int maxW) {
        if (font.width(text) <= maxW) return new String[] { text, null };
        String sub = font.plainSubstrByWidth(text, maxW);
        int lastSpace = sub.lastIndexOf(' ');
        String line1 = lastSpace > 0 ? sub.substring(0, lastSpace) : sub;
        String rest = text.substring(line1.length()).trim();
        if (rest.isEmpty()) return new String[] { line1, null };
        if (font.width(rest) > maxW) rest = font.plainSubstrByWidth(rest, maxW - 4) + "…";
        return new String[] { line1, rest };
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        ChroniclesUIKit.drawBorder(g, x, y, w, h, color);
    }

    private void renderBoxOptionsList(GuiGraphics g, int mx, int my) {
        boxOptionRowRects.clear();
        if (boxOptions.isEmpty()) {
            g.drawString(font, "§8No options yet - use §7+ Item", boxOptionsListX, boxOptionsListY, C_TEXT_FAINT,
                    false);
            return;
        }

        int visibleRows = Math.max(1, (boxOptionsListBottom - boxOptionsListY) / BOX_OPTION_ROW_H);

        int ry = boxOptionsListY;
        int end = Math.min(boxOptions.size(), boxOptionsScroll + visibleRows);
        for (int i = boxOptionsScroll; i < end; i++) {
            QuestReward opt = boxOptions.get(i);
            boolean hov = mx >= boxOptionsListX && mx < boxOptionsListX + boxOptionsListW &&
                    my >= ry && my < ry + BOX_OPTION_ROW_H;
            if (hov) g.fill(boxOptionsListX, ry, boxOptionsListX + boxOptionsListW, ry + BOX_OPTION_ROW_H,
                    C_ROW_HOVER);

            String label = opt.getSummary().getString();
            int maxW = boxOptionsListW - 12;
            if (font.width(label) > maxW) label = font.plainSubstrByWidth(label, Math.max(0, maxW - 6)) + "…";
            g.drawString(font, "§7" + label, boxOptionsListX + 1, ry + 3, C_TEXT_DIM, false);
            g.drawString(font, "§c✕", boxOptionsListX + boxOptionsListW - 9, ry + 3, 0xFFFF5555, false);

            boxOptionRowRects.add(new int[] { boxOptionsListX, ry, boxOptionsListW, BOX_OPTION_ROW_H });
            ry += BOX_OPTION_ROW_H;
        }

        int maxScroll = Math.max(0, boxOptions.size() - visibleRows);
        if (maxScroll > 0) {
            if (boxOptionsScroll > 0)
                g.drawString(font, "§8▲", boxOptionsListX + boxOptionsListW - 9, boxOptionsListY - 8, C_TEXT_FAINT,
                        false);
            if (boxOptionsScroll < maxScroll)
                g.drawString(font, "§8▼", boxOptionsListX + boxOptionsListW - 9, boxOptionsListBottom + 1,
                        C_TEXT_FAINT, false);
        }
    }
}
