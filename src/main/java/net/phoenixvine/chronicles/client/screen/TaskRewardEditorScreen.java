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
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.filter.IItemFilter;
import net.phoenixvine.chronicles.filter.ItemFilters;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;
import net.phoenixvine.chronicles.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.tasks.*;
import net.phoenixvine.chronicles.tasks.BlockBreakTask;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen task & reward editor — left column tasks, right column rewards.
 */
public class TaskRewardEditorScreen extends Screen {

    // ── Colours ───────────────────────────────────────────────────────────────
    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_OK;
    private static final int C_ROW_HOVER = 0x22FFFFFF;
    private static final int C_FORM_BG = 0x33000000;
    private static final int C_SPLIT = 0xFF2A2A3A;
    private static final int C_TOOLTIP_BG = 0xFF0E0E16;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 28;
    private static final int MARGIN = 10;
    private static final int COL_GAP = 6;    // gap between the two columns
    private static final int ROW_H = 26;   // task/reward list row height (2 lines)
    private static final int FIELD_H = 15;   // form field height
    private static final int FIELD_GAP = 5;    // gap between fields
    // Max stacked form field rows (Type/Description/Target/NBT-or-Secondary) PLUS one extra row's
    // worth of space for the count/consume/optional/Add row below them, which is positioned
    // independently from formBottom rather than continuing the same stacking cursor - without
    // that extra row of budget, task types needing all 4 stacked rows (e.g. item_check: Type +
    // Description + Target + NBT filter) had their last row overlap that fixed-position bottom row.
    private static final int FORM_ROWS = 5;

    // Derived — set in init()
    private int splitX;        // x where right column begins
    private int colW;          // width of each column (they're equal)
    private int listTop;       // y where list area begins
    private int listBottom;    // y where list area ends (form starts)
    private int formTop;       // y where add-form begins
    private int formBottom;    // y where footer begins (== height - FOOTER_H)

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final QuestNode questNode;
    /** Non-null when editing a variant's task/reward override instead of the quest's own base lists. */
    @Nullable
    private final QuestNode.QuestVariant variantTarget;

    private final List<QuestTask> tasks = new ArrayList<>();
    private final List<QuestReward> rewards = new ArrayList<>();

    // Task form
    private String taskType = "kill_entity";
    private boolean taskConsume = true;
    private boolean taskOptional = false;
    // "Hold X" style tasks (item/tag/fluid checks) default to sticky - see
    // ItemRequirementTask#sticky. Energy checks default false since a reading is inherently
    // transient; showSticky() below only exposes the button for the task types that support it.
    private boolean taskSticky = true;
    private boolean taskTypeDropOpen = false;
    private EditBox taskDescBox, taskTargetBox, taskCountBox, taskSecondaryBox, taskNbtBox;

    // Right-click-to-edit — when >= 0, the form is editing that existing task/reward in place
    // (same id preserved) instead of building a new one; -1 means the form is in "add new" mode.
    private int editingTaskIndex = -1;
    private int editingRewardIndex = -1;
    // Seed values applied to the freshly-rebuilt form boxes right after entering edit mode (the
    // boxes themselves get recreated blank by rebuildWidgets(), so there's nothing to read a
    // value back out of yet) or after a commit clears the form back to blank.
    private boolean forcePendingTaskValues = false;
    private String pendingTaskDesc = "", pendingTaskTarget = "", pendingTaskSecondary = "", pendingTaskCount = "",
            pendingTaskNbt = "";
    private boolean forcePendingRewardValues = false;
    private String pendingRewardCount = "", pendingRewardCommand = "", pendingRewardEventData = "";

    // Reward form
    private String rewardType = "item";
    private boolean rewardTypeDropOpen = false;
    private ItemStack rewardPickedItem = null;
    private EditBox rewardCountBox, rewardCommandBox;

    // Hover tracking
    private int hoveredTaskRow = -1;
    private int hoveredRewardRow = -1;
    private int hoveredDropRow = -1;

    // Task clipboard
    private static CompoundTag copiedTaskNBT = null;

    // Undo history — each entry is a snapshot of [tasks, rewards] before a mutation
    private final java.util.Deque<Object[]> undoHistory = new java.util.ArrayDeque<>();
    private static final int MAX_UNDO = 30;

    private static final String[] REWARD_TYPES = { "item", "xp", "command", "loot_table", "script_event",
            "reward_table" };

    /** Friendly display label for a reward type id - the ids themselves stay snake_case for NBT compatibility. */
    private static String rewardTypeLabel(String type) {
        return switch (type) {
            case "item" -> "Item";
            case "xp" -> "XP";
            case "command" -> "Command";
            case "loot_table" -> "Loot Table";
            case "script_event" -> "Script Event";
            case "reward_table" -> "Reward Table";
            default -> type;
        };
    }

    private EditBox rewardEventDataBox;

    // ── Constructor ───────────────────────────────────────────────────────────

    public TaskRewardEditorScreen(Screen parent, QuestNode questNode) {
        this(parent, questNode, null);
    }

    /**
     * @param variantTarget when non-null, this screen edits an override list for one quest
     *                      variant instead of the quest's own base tasks/rewards - starts from
     *                      the variant's current override (or the quest's base list, if the
     *                      variant hasn't overridden it yet) and flushes back into the variant.
     */
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

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        ChroniclesTheme th = ChroniclesTheme.current();
        C_BG = th.bg.getColor();
        C_PANEL = th.panel.getColor();
        C_HEADER = th.header.getColor();
        C_BORDER = th.border.getColor();
        C_ACCENT = th.accent.getColor();
        C_TEXT = th.text.getColor();
        C_TEXT_DIM = th.textDim.getColor();
        C_TEXT_FAINT = th.textFaint.getColor();
        C_OK = th.done.getColor();

        // Geometry
        colW = (width - MARGIN * 2 - COL_GAP) / 2;
        splitX = MARGIN + colW + COL_GAP;
        formBottom = height - FOOTER_H;
        formTop = formBottom - MARGIN - FORM_ROWS * (FIELD_H + FIELD_GAP) - 8;
        listTop = HEADER_H + 22; // 22px for column sub-header
        listBottom = formTop - 22; // leave room for the form panel header

        rebuildWidgets();
    }

    protected void rebuildWidgets() {
        // Preserve whatever's already typed across a rebuild (changing the type dropdown or
        // toggling Consume/Optional just changes which fields are shown, not what was already
        // entered) - unless startEditingTask/Reward or a commit just asked for fresh seed values,
        // in which case those win instead.
        String descVal = forcePendingTaskValues ? pendingTaskDesc : (taskDescBox != null ? taskDescBox.getValue() : "");
        String targetVal = forcePendingTaskValues ? pendingTaskTarget :
                (taskTargetBox != null ? taskTargetBox.getValue() : "");
        String secondVal = forcePendingTaskValues ? pendingTaskSecondary :
                (taskSecondaryBox != null ? taskSecondaryBox.getValue() : "");
        String countVal = forcePendingTaskValues ? pendingTaskCount :
                (taskCountBox != null ? taskCountBox.getValue() : "");
        String nbtVal = forcePendingTaskValues ? pendingTaskNbt : (taskNbtBox != null ? taskNbtBox.getValue() : "");
        forcePendingTaskValues = false;

        String rCountVal = forcePendingRewardValues ? pendingRewardCount :
                (rewardCountBox != null ? rewardCountBox.getValue() : "");
        String rCommandVal = forcePendingRewardValues ? pendingRewardCommand :
                (rewardCommandBox != null ? rewardCommandBox.getValue() : "");
        String rEventDataVal = forcePendingRewardValues ? pendingRewardEventData :
                (rewardEventDataBox != null ? rewardEventDataBox.getValue() : "");
        forcePendingRewardValues = false;

        clearWidgets();

        // ── Done button ───────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("§7‹ Done"), b -> {
            flushToQuestNode();
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(width / 2 - 40, height - FOOTER_H + (FOOTER_H - 14) / 2, 80, 14)
                .tooltip(Tooltip.create(Component.literal("Save changes and return to quest editor"))).build());

        // ── Task form fields ──────────────────────────────────────────────────
        int tx = MARGIN;
        int fy = formTop + 8;

        // Type selector
        PhoenixTaskRegistry.TaskEntry curMeta = getTaskMeta(taskType);
        String typeTooltip = curMeta != null && curMeta.editorTooltip() != null ?
                curMeta.editorTooltip().split("\n")[0] : "Choose the type of task to add";
        addRenderableWidget(Button.builder(
                Component.literal("§8Type: §7" + (curMeta != null ? curMeta.editorLabel() : taskType) + " §8▾"),
                b -> {
                    taskTypeDropOpen = !taskTypeDropOpen;
                    rewardTypeDropOpen = false;
                })
                .bounds(tx, fy, colW, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(typeTooltip))).build());
        fy += FIELD_H + FIELD_GAP;

        // Field visibility logic (unchanged from original)
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
            case "kill_entity", "item_check", "craft_item", "fluid_check", "location_terminal", "stat", "block_interact", "filter_item" -> true;
            default -> false;
        };
        // "Hold X" tasks where placing/using the item before some other requirement is met
        // shouldn't force re-gathering it - see the sticky field on each of these task classes.
        boolean showSticky = switch (taskType) {
            case "item_check", "tag_item", "fluid_check", "energy_check", "filter_item" -> true;
            default -> false;
        };

        // Description
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
                case "filter_item" -> "§8Item id(s), semicolon-separated — ANY match  (e.g. wire;cable)";
                case "external_trigger" -> "§8Trigger id";
                case "view_machine" -> "§8Machine id  (Phantasia multiblock definition id)";
                case "view_scene" -> "§8Scene id  (Phantasia scene definition id)";
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
            // "Any of" list — the picker APPENDS (semicolon-joined) instead of replacing the box,
            // since the whole point is building up a list of alternatives (e.g. wire OR cable).
            boolean hasItemListPicker = taskType.equals("filter_item");
            boolean hasFluidPicker = taskType.equals("fluid_check");
            int tw = (hasItemPicker || hasItemListPicker || hasFluidPicker) ? colW - 18 : colW;
            int tmaxLen = isInfo ? 512 : 160;
            taskTargetBox = new EditBox(font, tx, fy, tw, FIELD_H, Component.empty());
            taskTargetBox.setHint(Component.literal(hint));
            taskTargetBox.setMaxLength(tmaxLen);
            taskTargetBox.setValue(targetVal);
            addRenderableWidget(taskTargetBox);
            if (hasItemPicker) {
                addRenderableWidget(Button.builder(Component.literal("§7⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        if (id != null && taskTargetBox != null) taskTargetBox.setValue(id.toString());
                    }));
                }).bounds(tx + tw, fy, 16, FIELD_H).build());
            } else if (hasItemListPicker) {
                addRenderableWidget(Button.builder(Component.literal("§7⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
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
            }
            fy += FIELD_H + FIELD_GAP;
        }

        // NBT filter — only for item_check
        taskNbtBox = null;
        if (taskType.equals("item_check")) {
            taskNbtBox = new EditBox(font, tx, fy, colW, FIELD_H, Component.empty());
            taskNbtBox.setHint(Component
                    .literal("§8NBT filter  {Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}  (optional)"));
            taskNbtBox.setMaxLength(512);
            taskNbtBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal(
                            "Subset NBT match — item must contain ALL keys listed here.\nLeave blank to match any stack of the item.")));
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

        // Bottom row: count | consume | optional | add
        int rowY = formBottom - FIELD_H - 4;
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
        }
        if (showConsume) {
            int cx2 = needsCount ? tx + 56 : tx;
            addRenderableWidget(Button.builder(
                    Component.literal(taskConsume ? "§aConsume" : "§8Consume"),
                    b -> {
                        taskConsume = !taskConsume;
                        rebuildWidgets();
                    })
                    .bounds(cx2, rowY, 54, FIELD_H)
                    .tooltip(Tooltip.create(
                            Component.literal("Remove the item/fluid from the player's inventory on completion")))
                    .build());
        }
        if (showSticky) {
            addRenderableWidget(Button.builder(
                    Component.literal(taskSticky ? "§bSticky" : "§8Sticky"),
                    b -> {
                        taskSticky = !taskSticky;
                        rebuildWidgets();
                    })
                    .bounds(tx + colW - 162, rowY, 56, FIELD_H)
                    .tooltip(Tooltip.create(Component.literal(
                            "ON (default): once satisfied, stays satisfied - placing/using the item\n" +
                                    "later won't un-complete this task.\n" +
                                    "OFF: re-checked live - task un-completes if you stop holding enough.")))
                    .build());
        }
        addRenderableWidget(Button.builder(
                Component.literal(taskOptional ? "§eOptional" : "§8Optional"),
                b -> {
                    taskOptional = !taskOptional;
                    rebuildWidgets();
                })
                .bounds(tx + colW - 100, rowY, 50, FIELD_H)
                .tooltip(Tooltip.create(Component.literal("Task is optional — won't block quest completion"))).build());
        addRenderableWidget(Button.builder(
                Component.literal(editingTaskIndex >= 0 ? "§b✎ Update" : "§a✔ Add"),
                b -> commitTaskFromForm())
                .bounds(tx + colW - 46, rowY, 46, FIELD_H)
                .tooltip(Tooltip.create(Component.literal(editingTaskIndex >= 0 ?
                        "Save changes to this task (right-click it again to cancel)" :
                        "Add this task to the quest (Ctrl+Z to undo)")))
                .build());

        // ── Reward form fields ────────────────────────────────────────────────
        int rx = splitX;
        int rfy = formTop + 8;

        String rewardTypeTooltip = switch (rewardType) {
            case "item" -> "Give the player one or more items";
            case "xp" -> "Award experience levels";
            case "command" -> "Run a server command (%player% = player name)";
            case "loot_table" -> "Roll a loot table and give all resulting items";
            case "script_event" -> "Fire a Forge event for KubeJS or Java handlers";
            case "reward_table" -> "Reference a named reward table (config/phoenix_chronicles/reward_tables/)";
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
                    "§8Table ID  — known: " + knownTables;
            rewardCommandBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            rewardCommandBox.setHint(Component.literal(hint));
            rewardCommandBox.setMaxLength(128);
            rewardCommandBox.setValue(rCommandVal);
            addRenderableWidget(rewardCommandBox);
        } else {
            // command / loot_table
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

    // ── Undo ─────────────────────────────────────────────────────────────────

    private void pushUndo() {
        undoHistory.push(new Object[] { new ArrayList<>(tasks), new ArrayList<>(rewards) });
        if (undoHistory.size() > MAX_UNDO) undoHistory.pollLast();
    }

    @SuppressWarnings("unchecked")
    private void undoLastChange() {
        if (undoHistory.isEmpty()) return;
        Object[] snap = undoHistory.pop();
        tasks.clear();
        tasks.addAll((List<QuestTask>) snap[0]);
        rewards.clear();
        rewards.addAll((List<QuestReward>) snap[1]);
        editingTaskIndex = -1;
        editingRewardIndex = -1;
        rebuildWidgets();
    }

    // ── Commit ────────────────────────────────────────────────────────────────

    private void commitTaskFromForm() {
        String desc = taskDescBox != null ? taskDescBox.getValue().trim() : "";
        String target = taskTargetBox != null ? taskTargetBox.getValue().trim() : "";
        String second = taskSecondaryBox != null ? taskSecondaryBox.getValue().trim() : "";
        String countS = taskCountBox != null ? taskCountBox.getValue().trim() : "1";
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countS));
        } catch (NumberFormatException ignored) {}

        boolean needsTarget = !taskType.equals("experience") && !taskType.equals("dimension") &&
                !taskType.equals("checkmark");
        if (desc.isEmpty() || (needsTarget && !taskType.equals("info") && target.isEmpty())) return;

        // Editing an existing task must keep its original id - player progress/completion state
        // is keyed by task id, so minting a fresh one here would silently reset it.
        ResourceLocation taskId = (editingTaskIndex >= 0 && editingTaskIndex < tasks.size()) ?
                tasks.get(editingTaskIndex).getTaskId() :
                new ResourceLocation("phoenixcore", "task_" + taskType + "_" + System.currentTimeMillis());
        Component descComp = Component.literal(desc);
        QuestTask task = null;
        try {
            task = switch (taskType) {
                case "kill_entity" -> new KillEntityTask(taskId, descComp, new ResourceLocation(target), count,
                        taskConsume);
                case "item_check" -> {
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(target));
                    if (item == null) yield null;
                    ItemRequirementTask irt = new ItemRequirementTask(taskId, descComp, item, count, taskConsume);
                    String nbtStr = taskNbtBox != null ? taskNbtBox.getValue().trim() : "";
                    if (!nbtStr.isEmpty()) {
                        try {
                            irt.setNbtFilter(net.minecraft.nbt.TagParser.parseTag(nbtStr));
                        } catch (Exception e) { /* invalid NBT — ignore silently */ }
                    }
                    yield irt;
                }
                case "craft_item" -> new CraftItemTask(taskId, descComp, new ResourceLocation(target), count);
                case "experience" -> new ExperienceTask(taskId, descComp, count);
                case "location_terminal" -> new LocationOrTerminalTask(taskId, descComp, new ResourceLocation(target),
                        taskConsume);
                case "advancement" -> new AdvancementTask(taskId, descComp, new ResourceLocation(target));
                case "filter_item" -> {
                    List<IItemFilter> alts = new ArrayList<>();
                    for (String part : target.split(";")) {
                        String id = part.trim();
                        if (id.isEmpty()) continue;
                        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
                        if (item != null && item != Items.AIR) alts.add(ItemFilters.exact(item));
                    }
                    if (alts.isEmpty()) yield null;
                    IItemFilter filter = alts.size() == 1 ? alts.get(0) :
                            ItemFilters.anyOf(alts.toArray(new IItemFilter[0]));
                    yield new FilterItemTask(taskId, descComp, filter, count, taskConsume);
                }
                case "block_interact" -> {
                    var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(target));
                    String mode = second.isEmpty() ? "PLACE" : second.toUpperCase();
                    yield block != null ? new BlockInteractTask(taskId, descComp, block, mode) : null;
                }
                case "block_break" -> {
                    var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(target));
                    yield block != null ? new BlockBreakTask(taskId, descComp, block, count) : null;
                }
                case "enchantment" -> new EnchantmentTask(taskId, descComp, new ResourceLocation(target), count);
                case "fluid_check" -> new FluidRequirementTask(taskId, descComp, new ResourceLocation(target), count,
                        taskConsume);
                case "stat" -> new StatTrackerTask(taskId, descComp, new ResourceLocation(target), count, taskConsume);
                case "dimension" -> {
                    String dim = second.isEmpty() ? "minecraft:overworld" : second;
                    yield new DimensionTask(taskId, descComp,
                            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));
                }
                case "biome" -> new BiomeTask(taskId, descComp, new ResourceLocation(target));
                case "structure" -> new StructureTask(taskId, descComp, new ResourceLocation(target));
                case "checkmark" -> new CheckmarkTask(taskId, descComp);
                case "tag_item" -> new TagItemTask(taskId, descComp, ItemTags.create(new ResourceLocation(target)),
                        count);
                case "info" -> new InfoTask(taskId, descComp, target);
                case "external_trigger" -> new ExternalTriggerTask(taskId, descComp, target, count);
                case "view_machine" -> new net.phoenixvine.chronicles.tasks.ViewMachineTask(taskId, descComp, target,
                        (float) count);
                case "view_scene" -> new net.phoenixvine.chronicles.tasks.ViewSceneTask(taskId, descComp, target,
                        (float) count);
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
            pushUndo();
            if (editingTaskIndex >= 0 && editingTaskIndex < tasks.size()) {
                tasks.set(editingTaskIndex, task);
            } else {
                tasks.add(task);
            }
            editingTaskIndex = -1;
            taskTypeDropOpen = false;
            taskOptional = false;
            taskSticky = true;
            pendingTaskDesc = pendingTaskTarget = pendingTaskSecondary = pendingTaskCount = pendingTaskNbt = "";
            forcePendingTaskValues = true;
            rebuildWidgets();
        }
    }

    /** Populates the task form from an existing task and switches it into "edit in place" mode. */
    private void startEditingTask(int idx) {
        if (idx < 0 || idx >= tasks.size()) return;
        QuestTask t = tasks.get(idx);
        editingTaskIndex = idx;
        taskType = taskTypeIdFor(t);
        taskOptional = t.isOptional();
        taskConsume = true;
        taskSticky = true;
        if (t instanceof ItemRequirementTask x) taskSticky = x.isSticky();
        else if (t instanceof TagItemTask x) taskSticky = x.isSticky();
        else if (t instanceof FilterItemTask x) taskSticky = x.isSticky();
        else if (t instanceof FluidRequirementTask x) taskSticky = x.isSticky();
        else if (t instanceof FilterFluidTask x) taskSticky = x.isSticky();
        else if (t instanceof EnergyStorageTask x) taskSticky = x.isSticky();
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
        } else if (t instanceof net.phoenixvine.chronicles.tasks.ViewMachineTask vmt) {
            pendingTaskTarget = vmt.getMachineId();
            pendingTaskCount = String.valueOf((int) vmt.getMinSeconds());
        } else if (t instanceof net.phoenixvine.chronicles.tasks.ViewSceneTask vst) {
            pendingTaskTarget = vst.getSceneId();
            pendingTaskCount = String.valueOf((int) vst.getMinSeconds());
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
        }

        forcePendingTaskValues = true;
        taskTypeDropOpen = false;
        rewardTypeDropOpen = false;
        rebuildWidgets();
    }

    /**
     * Flattens an item filter back into the semicolon-separated id list the "filter_item" form
     * field expects, so re-opening an existing task for editing shows its actual item list
     * instead of a blank box. Only ExactItem/AnyOf-of-ExactItem round-trip cleanly (that's all
     * this editor ever builds) - anything else (a hand-authored tag/mod/allOf/not filter from
     * NBT or KubeJS) falls back to its human-readable describe() text, which won't re-parse into
     * the same filter if saved again, but at least isn't blank.
     */
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

    /** Applies the Sticky toggle to whichever task types actually support it (see each class's own sticky field). */
    private static void applyStickyIfSupported(QuestTask t, boolean sticky) {
        if (t instanceof ItemRequirementTask x) x.setSticky(sticky);
        else if (t instanceof TagItemTask x) x.setSticky(sticky);
        else if (t instanceof FilterItemTask x) x.setSticky(sticky);
        else if (t instanceof FluidRequirementTask x) x.setSticky(sticky);
        else if (t instanceof FilterFluidTask x) x.setSticky(sticky);
        else if (t instanceof EnergyStorageTask x) x.setSticky(sticky);
    }

    /** Reverses the construction switch in {@link #commitTaskFromForm()} to find a task's editor type id. */
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
        if (t instanceof TagItemTask) return "tag_item";
        if (t instanceof InfoTask) return "info";
        if (t instanceof net.phoenixvine.chronicles.tasks.ViewMachineTask) return "view_machine";
        if (t instanceof net.phoenixvine.chronicles.tasks.ViewSceneTask) return "view_scene";
        if (t instanceof EnergyStorageTask) return "energy_check";
        if (t instanceof FilterItemTask) return "filter_item";
        return "checkmark";
    }

    private void cancelTaskEdit() {
        editingTaskIndex = -1;
        taskOptional = false;
        pendingTaskDesc = pendingTaskTarget = pendingTaskSecondary = pendingTaskCount = pendingTaskNbt = "";
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
            case "item" -> rewardPickedItem != null ? new QuestReward.ItemReward(rewardPickedItem.getItem(), count) :
                    null;
            case "xp" -> new QuestReward.XPReward(count);
            case "command" -> {
                String cmd = rewardCommandBox != null ? rewardCommandBox.getValue().trim() : "";
                yield cmd.isEmpty() ? null : new QuestReward.CommandReward(cmd);
            }
            case "loot_table" -> {
                String lt = rewardCommandBox != null ? rewardCommandBox.getValue().trim() : "";
                yield lt.isEmpty() ? null : new QuestReward.LootTableReward(new ResourceLocation(lt));
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
            default -> null;
        };

        if (reward != null) {
            pushUndo();
            if (editingRewardIndex >= 0 && editingRewardIndex < rewards.size()) {
                rewards.set(editingRewardIndex, reward);
            } else {
                rewards.add(reward);
            }
            editingRewardIndex = -1;
            rewardPickedItem = null;
            rewardTypeDropOpen = false;
            pendingRewardCount = pendingRewardCommand = pendingRewardEventData = "";
            forcePendingRewardValues = true;
            rebuildWidgets();
        }
    }

    /** Populates the reward form from an existing reward and switches it into "edit in place" mode. */
    private void startEditingReward(int idx) {
        if (idx < 0 || idx >= rewards.size()) return;
        QuestReward r = rewards.get(idx);
        rewardPickedItem = null;
        pendingRewardCount = "1";
        pendingRewardCommand = "";
        pendingRewardEventData = "";

        if (r instanceof QuestReward.ItemReward ir) {
            rewardType = "item";
            rewardPickedItem = new ItemStack(ir.getItem(), ir.getCount());
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
        } else {
            return; // unsupported reward type (e.g. loot crate) - no form fields exist to edit it with
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
        forcePendingRewardValues = true;
        rebuildWidgets();
    }

    // ── Flush ─────────────────────────────────────────────────────────────────

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
        // This used to only mutate the in-memory QuestNode, relying on some OTHER trigger
        // (logout/shutdown's saveAllQuestsToDisk, or an unrelated edit elsewhere) to actually
        // persist it - meaning a task/reward edit here survived only until the next abnormal
        // exit (crash, force-quit), exactly the same "works this session, reverts on restart"
        // bug this session already found and fixed for descriptions. variantTarget edits are
        // covered too since a variant is serialized as part of its owning quest's own SNBT.
        //
        // Only write to disk if questNode is the actual live registered instance - QuestCreatorScreen
        // opens this screen against a throwaway QuestNode while a brand-new quest is still being
        // filled out (it doesn't exist in the registry or on disk yet, and might never be saved at
        // all), and writing straight to disk here would leave a stray "_preview_.snbt"-style ghost
        // file behind regardless of whether the user ever clicks that screen's own Save button.
        if (net.phoenixvine.chronicles.registry.QuestTreeRegistry.getQuest(questNode.getId()) == questNode) {
            net.phoenixvine.chronicles.codec.QuestFileSaver.saveOneQuestToDisk(questNode);
            net.phoenixvine.chronicles.client.LangSyncScheduler.markDirty();
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        // This is a full editor screen (its own header/columns spanning the whole window), not a
        // small floating card - rendering the quest graph behind it at 67% opacity just let it
        // visibly bleed through behind every panel. Use a solid opaque background like the rest
        // of this screen's UI, and defensively clear any scissor left active from a previous frame
        // (the overview screen and its sub-panels push several) so this fill can't get clipped.
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        g.fill(0, 0, width, height, C_BG);

        // Header - accent stripe across the top ties this into the same visual language as the
        // rest of the Chronicles UI (e.g. QuestTextInputScreen's accent stripe, the group/toast
        // editors' accent borders), instead of a flat header with no signature touch at all.
        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, 0, width, 2, C_ACCENT);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        String repeatBadge = switch (questNode.getRepeatMode()) {
            case DAILY -> "  §b[Daily]";
            case COOLDOWN -> "  §e[Cooldown " + questNode.getRepeatCooldownHours() + "h]";
            case INFINITE -> "  §a[∞]";
            default -> "";
        };
        String variantBadge = variantTarget != null ? "  §d[variant: " + variantTarget.condition + "]" : "";
        g.drawCenteredString(font,
                "§fTasks & Rewards  §8— §7" + questNode.getId().getPath() + repeatBadge + variantBadge,
                width / 2, (HEADER_H - 8) / 2, C_TEXT);

        // Column sub-headers
        g.fill(0, HEADER_H, width, listTop - 1, C_PANEL);
        g.fill(0, listTop - 1, width, listTop, C_BORDER);
        String taskSubHeader;
        if (tasks.isEmpty()) {
            taskSubHeader = "§c⚠ No tasks — quest auto-completes on unlock";
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

        // Centre column divider
        g.fill(splitX - COL_GAP / 2, HEADER_H, splitX - COL_GAP / 2 + 1, height - FOOTER_H, C_SPLIT);

        // Form zone background + separator
        int formPanelTop = formTop - 20;
        g.fill(0, formPanelTop, width, formBottom, C_PANEL);
        g.fill(0, formPanelTop, width, formPanelTop + 1, C_BORDER);
        // Column form panels
        g.fill(MARGIN, formPanelTop + 2, MARGIN + colW, formBottom - 2, C_FORM_BG);
        drawBorder(g, MARGIN, formPanelTop + 2, colW, formBottom - 2 - (formPanelTop + 2), C_BORDER);
        g.fill(splitX, formPanelTop + 2, splitX + colW, formBottom - 2, C_FORM_BG);
        drawBorder(g, splitX, formPanelTop + 2, colW, formBottom - 2 - (formPanelTop + 2), C_BORDER);
        g.drawString(font, editingTaskIndex >= 0 ? "§b✎ Editing Task (right-click to cancel)" : "§8Add Task",
                MARGIN + 6, formPanelTop + 6, C_TEXT_FAINT, false);
        g.drawString(font,
                editingRewardIndex >= 0 ? "§b✎ Editing Reward (right-click to cancel)" : "§8Add Reward",
                splitX + 6, formPanelTop + 6, C_TEXT_FAINT, false);

        // Footer
        g.fill(0, height - FOOTER_H, width, height, C_HEADER);
        g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, C_BORDER);

        // ── Task list ─────────────────────────────────────────────────────────
        g.enableScissor(0, listTop, splitX - COL_GAP / 2, listBottom);
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
            // Accent stripe: green = optional, accent = required
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
                // description overflowed — second line continues it; no room for detail
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
            g.drawString(font, "§8No tasks yet — add one below.", MARGIN + 6, listTop + 5, C_TEXT_FAINT, false);
        g.disableScissor();

        // ── Reward list ───────────────────────────────────────────────────────
        g.enableScissor(splitX, listTop, width, listBottom);
        hoveredRewardRow = -1;
        int ry = listTop;
        for (int i = 0; i < rewards.size(); i++) {
            QuestReward reward = rewards.get(i);
            if (ry + ROW_H > listBottom) break;
            boolean hov = mx >= splitX && mx < width - MARGIN && my >= ry && my < ry + ROW_H;
            if (hov) {
                g.fill(splitX, ry, width - MARGIN, ry + ROW_H, C_ROW_HOVER);
                hoveredRewardRow = i;
            }
            // Accent stripe - matches the task list's left-edge accent bar (was only on the task
            // side, which made the reward list read as a plainer, less finished sibling of it).
            g.fill(splitX, ry + 2, splitX + 2, ry + ROW_H - 2, C_ACCENT);
            int rewardTextX = splitX + 5;
            if (reward instanceof QuestReward.ItemReward ir) {
                ItemStack stack = new ItemStack(ir.getItem(), ir.getCount());
                g.renderItem(stack, rewardTextX, ry + 4);
                rewardTextX += 18;
                int rmaxW = (width - MARGIN - (hov ? 16 : 6)) - rewardTextX;
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
                int rmaxW = (width - MARGIN - (hov ? 16 : 6)) - rewardTextX - font.width(icon) - 4;
                String rl = reward.getSummary().getString();
                String[] rwrapped = wordWrap(rl, rmaxW);
                g.drawString(font, icon + " §7" + rwrapped[0], rewardTextX, ry + 4, C_TEXT_DIM, false);
                g.drawString(font, rwrapped[1] != null ? "§8" + rwrapped[1] : typeLine,
                        rewardTextX, ry + 15, C_TEXT_FAINT, false);
            }
            if (hov) g.drawString(font, "§c×", width - MARGIN - 12, ry + 9, 0xFFFF5555, false);
            ry += ROW_H;
        }
        if (rewards.isEmpty())
            g.drawString(font, "§8No rewards yet — add one below.", splitX + 6, listTop + 5, C_TEXT_FAINT, false);
        g.disableScissor();

        super.render(g, mx, my, partial);

        // ── Dropdowns ─────────────────────────────────────────────────────────
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.flush(); // same missing-flush bleed-through bug fixed elsewhere this session

        if (taskTypeDropOpen) {
            List<PhoenixTaskRegistry.TaskEntry> editorTypes = PhoenixTaskRegistry.getEditorTypes();
            int rowH = FIELD_H;
            int dropH = editorTypes.size() * rowH;
            int dy = Math.max(listTop, formTop - dropH - 2);
            g.fill(MARGIN, dy, MARGIN + colW, dy + dropH, C_PANEL);
            drawBorder(g, MARGIN, dy, colW, dropH, C_ACCENT);
            hoveredDropRow = -1;
            for (int i = 0; i < editorTypes.size(); i++) {
                PhoenixTaskRegistry.TaskEntry m = editorTypes.get(i);
                int dropRowY = dy + i * rowH;
                boolean hov = mx >= MARGIN && mx < MARGIN + colW && my >= dropRowY && my < dropRowY + rowH;
                if (hov) {
                    g.fill(MARGIN + 1, dropRowY, MARGIN + colW - 1, dropRowY + rowH, 0xFF1E1E2A);
                    hoveredDropRow = i;
                }
                g.drawString(font, m.editorIcon() + " §7" + m.editorLabel(), MARGIN + 5, dropRowY + 3,
                        hov ? C_TEXT : C_TEXT_DIM, false);
            }
            if (hoveredDropRow >= 0 && hoveredDropRow < editorTypes.size()) {
                PhoenixTaskRegistry.TaskEntry hm = editorTypes.get(hoveredDropRow);
                String tooltip = hm.editorTooltip() != null ? hm.editorTooltip() : hm.editorLabel();
                String[] lines = tooltip.split("\n");
                int maxLw = 0;
                for (String l : lines) maxLw = Math.max(maxLw, font.width(l));
                int tipW = maxLw + 10, tipH = lines.length * 10 + 6;
                int tipX = MARGIN + colW + 4;
                int tipY = Math.min(Math.max(dy + hoveredDropRow * rowH, 2), height - tipH - 2);
                if (tipX + tipW > width - 2) tipX = MARGIN - tipW - 4;
                g.fill(tipX, tipY, tipX + tipW, tipY + tipH, C_TOOLTIP_BG);
                drawBorder(g, tipX, tipY, tipW, tipH, C_ACCENT);
                for (int li = 0; li < lines.length; li++)
                    g.drawString(font, (li == 0 ? "§f" : "§8") + lines[li], tipX + 5, tipY + 3 + li * 10, 0xFFFFFFFF,
                            false);
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
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & 2) != 0;
        if (ctrl && key == 90) { // Ctrl+Z — undo
            undoLastChange();
            return true;
        }
        if (ctrl && key == 86 && copiedTaskNBT != null) {
            QuestTask pasted = deserializeTask(copiedTaskNBT.copy());
            if (pasted != null) {
                pasted = retaskId(pasted, "task_paste_" + System.currentTimeMillis());
                tasks.add(pasted);
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            if (taskTypeDropOpen) {
                List<PhoenixTaskRegistry.TaskEntry> edTypes = PhoenixTaskRegistry.getEditorTypes();
                int dropH = edTypes.size() * FIELD_H;
                int dy = Math.max(listTop, formTop - dropH - 2);
                for (int i = 0; i < edTypes.size(); i++) {
                    int ry2 = dy + i * FIELD_H;
                    if (mx >= MARGIN && mx < MARGIN + colW && my >= ry2 && my < ry2 + FIELD_H) {
                        taskType = edTypes.get(i).typeId();
                        taskTypeDropOpen = false;
                        rebuildWidgets();
                        return true;
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
            // Copy task
            if (hoveredTaskRow >= 0 && mx >= splitX - COL_GAP - 28 && mx < splitX - COL_GAP - 14) {
                copiedTaskNBT = tasks.get(hoveredTaskRow).serializeNBT();
                return true;
            }
            // Delete task
            if (hoveredTaskRow >= 0 && mx >= splitX - COL_GAP - 14 && mx < splitX - COL_GAP) {
                pushUndo();
                tasks.remove(hoveredTaskRow);
                hoveredTaskRow = -1;
                if (editingTaskIndex >= 0) cancelTaskEdit(); // stale index — bail out of editing
                return true;
            }
            // Delete reward
            if (hoveredRewardRow >= 0 && mx >= width - MARGIN - 14 && mx < width - MARGIN) {
                pushUndo();
                rewards.remove(hoveredRewardRow);
                hoveredRewardRow = -1;
                if (editingRewardIndex >= 0) cancelRewardEdit(); // stale index — bail out of editing
                return true;
            }
        } else if (btn == 1) {
            // Right-click an existing task/reward row to load it into the form for editing in
            // place - right-clicking the same row again cancels back out to "add new" mode.
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
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void onClose() {
        flushToQuestNode();
        net.phoenixvine.chronicles.client.LangSyncScheduler.flushNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Nullable
    private static QuestTask deserializeTask(CompoundTag nbt) {
        QuestTask t = PhoenixTaskRegistry.deserialize(nbt);
        if (t != null) t.setOptional(nbt.getBoolean("optional"));
        return t;
    }

    private static QuestTask retaskId(QuestTask task, String newId) {
        CompoundTag nbt = task.serializeNBT();
        nbt.putString("task_id", "phoenixcore:" + newId);
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
        return (item != null && item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
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

    /** Splits text at the last word boundary that fits within maxW pixels. Returns [line1, line2_or_null]. */
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
}
