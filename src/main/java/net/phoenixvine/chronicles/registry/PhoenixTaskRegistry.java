package net.phoenixvine.chronicles.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.filter.FluidFilters;
import net.phoenixvine.chronicles.filter.ItemFilters;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.tasks.*;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class PhoenixTaskRegistry {

    public record FieldDef(String id, String label, FieldType type, @Nullable String hint) {

        public enum FieldType {
            TEXT,
            INTEGER,
            BOOLEAN,
            ITEM_ID,
            ENTITY_ID,
            FLUID_ID,
            ITEM_FILTER,
            FLUID_FILTER
        }

        public static FieldDef text(String id, String label) {
            return new FieldDef(id, label, FieldType.TEXT, null);
        }

        public static FieldDef text(String id, String label, String hint) {
            return new FieldDef(id, label, FieldType.TEXT, hint);
        }

        public static FieldDef integer(String id, String label) {
            return new FieldDef(id, label, FieldType.INTEGER, null);
        }

        public static FieldDef itemId(String id, String label) {
            return new FieldDef(id, label, FieldType.ITEM_ID, null);
        }

        public static FieldDef entityId(String id, String label) {
            return new FieldDef(id, label, FieldType.ENTITY_ID, null);
        }

        public static FieldDef fluidId(String id, String label) {
            return new FieldDef(id, label, FieldType.FLUID_ID, null);
        }

        public static FieldDef bool(String id, String label) {
            return new FieldDef(id, label, FieldType.BOOLEAN, null);
        }

        public static FieldDef itemFilter(String id, String label) {
            return new FieldDef(id, label, FieldType.ITEM_FILTER, null);
        }

        public static FieldDef fluidFilter(String id, String label) {
            return new FieldDef(id, label, FieldType.FLUID_FILTER, null);
        }
    }

    public static final class ScriptTaskHandler {

        public BiPredicate<ScriptTask, Player> isCompletedFor = (t, p) -> false;
        public BiConsumer<ScriptTask, Player> tryConsume = (t, p) -> {};
        public BiFunction<ScriptTask, Player, String> progressString = (t, p) -> null;
        public boolean dependsOnInventory = true;
    }

    public record TaskEntry(
                            String typeId,
                            Function<CompoundTag, Object> deserializer,
                            @Nullable String editorIcon,
                            @Nullable String editorLabel,
                            @Nullable String editorTooltip,
                            List<FieldDef> fields) {

        public boolean hasEditorMeta() {
            return editorLabel != null;
        }
    }

    private static final Map<String, TaskEntry> REGISTRY = new LinkedHashMap<>();
    
    private static final List<TaskEntry> EDITOR_ORDER = new ArrayList<>();
    private static final Map<String, ScriptTaskHandler> SCRIPT_HANDLERS = new HashMap<>();

    public static Builder register(String typeId, Function<CompoundTag, Object> deserializer) {
        return new Builder(typeId, deserializer);
    }

    public static Builder registerScripted(String typeId) {
        ScriptTaskHandler handler = new ScriptTaskHandler();
        SCRIPT_HANDLERS.put(typeId, handler);
        Builder b = register(typeId, tag -> {
            ScriptTask t = new ScriptTask(taskId(tag), desc(tag), typeId);
            t.deserializeNBT(tag);
            return t;
        });
        b.scriptHandler = handler;
        return b;
    }

    @Nullable
    public static ScriptTaskHandler getScriptHandler(String typeId) {
        return SCRIPT_HANDLERS.get(typeId);
    }

    public static final class Builder {

        private final String typeId;
        private final Function<CompoundTag, Object> deserializer;
        private String icon = null;
        private String label = null;
        private String tooltip = null;
        private final List<FieldDef> fields = new ArrayList<>();
        private ScriptTaskHandler scriptHandler = null;

        private Builder(String typeId, Function<CompoundTag, Object> deserializer) {
            this.typeId = typeId;
            this.deserializer = deserializer;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder tooltip(String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder field(FieldDef f) {
            this.fields.add(f);
            return this;
        }

        public Builder onCompleted(BiPredicate<ScriptTask, Player> fn) {
            requireScriptHandler().isCompletedFor = fn;
            return this;
        }

        public Builder onConsume(BiConsumer<ScriptTask, Player> fn) {
            requireScriptHandler().tryConsume = fn;
            return this;
        }

        public Builder progressString(BiFunction<ScriptTask, Player, String> fn) {
            requireScriptHandler().progressString = fn;
            return this;
        }

        public Builder dependsOnInventory(boolean v) {
            requireScriptHandler().dependsOnInventory = v;
            return this;
        }

        private ScriptTaskHandler requireScriptHandler() {
            if (scriptHandler == null) {
                throw new IllegalStateException(
                        "onCompleted/onConsume/progressString/dependsOnInventory can only be used on a " +
                                "Builder from registerScripted(...), not register(...)");
            }
            return scriptHandler;
        }

        public void register() {
            TaskEntry entry = new TaskEntry(typeId, deserializer, icon, label, tooltip,
                    Collections.unmodifiableList(new ArrayList<>(fields)));
            REGISTRY.put(typeId, entry);
            if (entry.hasEditorMeta()) EDITOR_ORDER.add(entry);
        }
    }

    @Nullable
    public static TaskEntry get(String typeId) {
        return REGISTRY.get(typeId);
    }

    public static List<TaskEntry> getEditorTypes() {
        return Collections.unmodifiableList(EDITOR_ORDER);
    }

    @Nullable
    public static QuestTask deserialize(CompoundTag tag) {
        String typeId = tag.getString("type");
        TaskEntry entry = REGISTRY.get(typeId);
        if (entry == null) return null;
        try {
            
            return (QuestTask) entry.deserializer().apply(tag);
        } catch (Exception e) {
            System.err.println(
                    "[PhoenixTaskRegistry] Failed to deserialize task type '" + typeId + "': " + e.getMessage());
            return null;
        }
    }

    private static boolean builtinsRegistered = false;

    public static void registerBuiltins() {
        if (builtinsRegistered) return;
        builtinsRegistered = true;

        register("kill_entity", tag -> {
            KillEntityTask t = new KillEntityTask(taskId(tag), desc(tag),
                    new net.minecraft.resources.ResourceLocation("minecraft", "pig"), 1, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§câ˜ ").label("Kill Entity")
                .tooltip("Kill a number of a specific mob type.\nTarget: entity registry id (e.g. minecraft:zombie)")
                .field(FieldDef.entityId("entity_id", "Entity ID"))
                .field(FieldDef.integer("required", "Count"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("item_check", tag -> {
            ItemRequirementTask t = new ItemRequirementTask(taskId(tag), desc(tag),
                    net.minecraft.world.item.Items.DIRT, 1, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§eâ– ").label("Collect Item")
                .tooltip(
                        "Have a specific item in your inventory.\nTarget: item registry id. Consume: remove items on complete.")
                .field(FieldDef.itemId("item_id", "Item ID"))
                .field(FieldDef.integer("count", "Count"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("craft_item", tag -> {
            CraftItemTask t = new CraftItemTask(taskId(tag), desc(tag),
                    new net.minecraft.resources.ResourceLocation("minecraft", "dirt"), 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§6âš’").label("Craft Item")
                .tooltip("Craft a specific item the required number of times.\nTarget: item registry id.")
                .field(FieldDef.itemId("item_id", "Item ID"))
                .field(FieldDef.integer("count", "Count"))
                .register();

        register("experience", tag -> {
            ExperienceTask t = new ExperienceTask(taskId(tag), desc(tag), 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§aâœ¦").label("XP Level")
                .tooltip("Reach a minimum XP level.\nNo target needed â€” just set the required level.")
                .field(FieldDef.integer("required_level", "Level"))
                .register();

        register("location_terminal", tag -> {
            LocationOrTerminalTask t = new LocationOrTerminalTask(taskId(tag), desc(tag),
                    new net.minecraft.resources.ResourceLocation("minecraft", "air"), false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§bâ—Ž").label("Terminal / Location")
                .tooltip("Interact with a specific terminal block or location.\nTarget: terminal registry id.")
                .field(FieldDef.text("terminal_id", "Terminal ID"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("advancement", tag -> {
            AdvancementTask t = new AdvancementTask(taskId(tag), desc(tag),
                    new net.minecraft.resources.ResourceLocation("minecraft", "story/root"));
            t.deserializeNBT(tag);
            return t;
        }).icon("§dâ˜…").label("Advancement")
                .tooltip(
                        "Earn a specific Minecraft advancement.\nTarget: advancement id (e.g. minecraft:story/mine_diamond)")
                .field(FieldDef.text("advancement_id", "Advancement ID"))
                .register();

        register("block_interact", tag -> {
            BlockInteractTask t = new BlockInteractTask(taskId(tag), desc(tag),
                    net.minecraft.world.level.block.Blocks.STONE, "PLACE");
            t.deserializeNBT(tag);
            return t;
        }).icon("§7â–¡").label("Block Interact")
                .tooltip("Place or right-click a specific block.\nTarget: block id. Secondary: PLACE or RIGHT_CLICK.")
                .field(FieldDef.text("block_id", "Block ID"))
                .field(FieldDef.text("mode", "Mode", "PLACE or RIGHT_CLICK"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("fluid_check", tag -> {
            FluidRequirementTask t = new FluidRequirementTask(taskId(tag), desc(tag),
                    new net.minecraft.resources.ResourceLocation("minecraft", "water"), 1000, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§3â‰‹").label("Fluid Check")
                .tooltip("Have a fluid amount in a tank.\nTarget: fluid id. Count: amount in mB.")
                .field(FieldDef.fluidId("fluid_id", "Fluid ID"))
                .field(FieldDef.integer("amount", "Amount (mB)"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("stat", tag -> {
            StatTrackerTask t = new StatTrackerTask(taskId(tag), desc(tag),
                    new net.minecraft.resources.ResourceLocation("minecraft", "jump"), 1, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§9â‰¡").label("Stat Tracker")
                .tooltip(
                        "Reach a value on a Minecraft statistic.\nTarget: stat id (e.g. minecraft:jump). Count: target value.")
                .field(FieldDef.text("stat_id", "Stat ID"))
                .field(FieldDef.integer("required", "Target Value"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("dimension", tag -> {
            DimensionTask t = new DimensionTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            new net.minecraft.resources.ResourceLocation("minecraft", "overworld")));
            t.deserializeNBT(tag);
            return t;
        }).icon("§5âŠ•").label("Visit Dimension")
                .tooltip("Travel to a specific dimension.\nSecondary: dimension id (e.g. minecraft:the_nether)")
                .field(FieldDef.text("dimension_id", "Dimension ID"))
                .register();

        register("biome", tag -> {
            BiomeTask t = new BiomeTask(taskId(tag), desc(tag),
                    new net.minecraft.resources.ResourceLocation("minecraft", "plains"));
            t.deserializeNBT(tag);
            return t;
        }).icon("§2â›°").label("Visit Biome")
                .tooltip("Stand inside a specific biome.\nTarget: biome registry id (e.g. minecraft:jungle)")
                .field(FieldDef.text("biome_id", "Biome ID"))
                .register();

        register("structure", tag -> {
            StructureTask t = new StructureTask(taskId(tag), desc(tag),
                    new net.minecraft.resources.ResourceLocation("minecraft", "village"));
            t.deserializeNBT(tag);
            return t;
        }).icon("§6âŒ‚").label("Visit Structure")
                .tooltip("Enter a specific structure.\nTarget: structure registry id (e.g. minecraft:village)")
                .field(FieldDef.text("structure_id", "Structure ID"))
                .register();

        register("checkmark", tag -> {
            CheckmarkTask t = new CheckmarkTask(taskId(tag), desc(tag));
            t.deserializeNBT(tag);
            return t;
        }).icon("§aâœ“").label("Checkmark")
                .tooltip("Admin-completable manual task.\nNo target needed â€” complete with /chronicle task complete.")
                .register();

        register("tag_item", tag -> {
            TagItemTask t = new TagItemTask(taskId(tag), desc(tag),
                    net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("c", "gems")), 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§eâ—ˆ").label("Tag Item")
                .tooltip(
                        "Have items matching an item tag in inventory.\nTarget: item tag (e.g. c:ores/iron). Count: required amount.")
                .field(FieldDef.text("tag", "Item Tag"))
                .field(FieldDef.integer("count", "Count"))
                .register();

        register("info", tag -> {
            InfoTask t = new InfoTask(taskId(tag), desc(tag), "");
            t.deserializeNBT(tag);
            return t;
        }).icon("§7§l!").label("Info / Text")
                .tooltip(
                        "A read-only information panel the player must acknowledge.\nNo target â€” body text is shown to the player.")
                .field(FieldDef.text("body", "Body Text"))
                .register();

        register("energy_check", tag -> {
            EnergyStorageTask t = new EnergyStorageTask(taskId(tag), desc(tag),
                    10000L, EnergyStorageTask.EnergyType.FE, EnergyStorageTask.Source.INVENTORY);
            t.deserializeNBT(tag);
            return t;
        }).icon("§6âš¡").label("Energy Storage")
                .tooltip(
                        "Check stored energy (FE or GTM EU) in inventory items OR a right-clicked block.\nCount: amount required.\nTarget: FE / EU / ANY  (energy type).\nSecondary: INVENTORY / HELD / BLOCK  (source).")
                .field(FieldDef.text("energy_type", "Energy Type", "FE / EU / ANY"))
                .field(FieldDef.text("source", "Source", "INVENTORY / HELD / BLOCK"))
                .field(FieldDef.integer("required", "Amount (FE)"))
                .register();

        register("ae2_item_storage", tag -> {
            net.phoenixvine.chronicles.tasks.AE2ItemStorageTask t = new net.phoenixvine.chronicles.tasks.AE2ItemStorageTask(
                    taskId(tag), desc(tag),
                    net.minecraft.world.item.Items.DIRT, 1, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§bâ—†").label("AE2 Item Storage")
                .tooltip("Have an item amount stored in your Applied Energistics 2 network.\n" +
                        "Read through whichever wireless terminal you're currently holding.\n" +
                        "Target: item registry id. Consume: withdraw items on complete.")
                .field(FieldDef.itemId("item_id", "Item ID"))
                .field(FieldDef.integer("count", "Count"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("ae2_fluid_storage", tag -> {
            net.phoenixvine.chronicles.tasks.AE2FluidStorageTask t = new net.phoenixvine.chronicles.tasks.AE2FluidStorageTask(
                    taskId(tag), desc(tag),
                    new net.minecraft.resources.ResourceLocation("minecraft", "water"), 1000, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§bâ‰‹").label("AE2 Fluid Storage")
                .tooltip("Have a fluid amount stored in your Applied Energistics 2 network.\n" +
                        "Read through whichever wireless terminal you're currently holding.\n" +
                        "Target: fluid id. Count: amount in mB.")
                .field(FieldDef.fluidId("fluid_id", "Fluid ID"))
                .field(FieldDef.integer("amount", "Amount (mB)"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("filter_item", tag -> {
            FilterItemTask t = new FilterItemTask(taskId(tag), desc(tag),
                    ItemFilters.tag("c:gems"), 1, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§eâ—‡").label("Collect Items (Filter)")
                .tooltip("Have items matching a composable filter in inventory.\n" +
                        "Filter types: exact, tag, mod, any_of, all_of, not.\n" +
                        "Consume: remove matching items on completion.\n" +
                        "Example: any_of [ {tag: forge:ingots/copper}, {tag: forge:ingots/gold} ]")
                .field(FieldDef.itemFilter("filter", "Item Filter"))
                .field(FieldDef.integer("count", "Count"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("filter_fluid", tag -> {
            FilterFluidTask t = new FilterFluidTask(taskId(tag), desc(tag),
                    FluidFilters.exact(new net.minecraft.resources.ResourceLocation("minecraft", "water")), 1000,
                    false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§3â‰‹").label("Fluid Check (Filter)")
                .tooltip("Have a fluid amount in inventory matching a composable filter.\n" +
                        "Filter types: exact, tag, mod, any_of, all_of, not.\n" +
                        "Supports fluid tags (e.g. forge:milk, minecraft:water).\n" +
                        "Amount in mB. Consume: drain fluid on completion.")
                .field(FieldDef.fluidFilter("filter", "Fluid Filter"))
                .field(FieldDef.integer("amount", "Amount (mB)"))
                .field(FieldDef.bool("consume", "Consume"))
                .register();

        register("block_break", tag -> {
            BlockBreakTask t = new BlockBreakTask(taskId(tag), desc(tag),
                    net.minecraft.world.level.block.Blocks.STONE, 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§7âœ•").label("Break Block")
                .tooltip(
                        "Break a specific block a required number of times.\nTarget: block registry id (e.g. minecraft:stone). Count: how many times.")
                .field(FieldDef.text("block_id", "Block ID"))
                .field(FieldDef.integer("required", "Count"))
                .register();

        register("enchantment", tag -> {
            net.phoenixvine.chronicles.tasks.EnchantmentTask t = new net.phoenixvine.chronicles.tasks.EnchantmentTask(
                    taskId(tag), desc(tag), new net.minecraft.resources.ResourceLocation("minecraft:sharpness"), 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§bâœ¦").label("Enchantment")
                .tooltip(
                        "Complete when the player has a specific enchantment at >= the required level on any equipped item.\nenchantment_id: registry id (e.g. minecraft:sharpness). required_level: minimum enchantment level.")
                .field(FieldDef.text("enchantment_id", "Enchantment ID", "e.g. minecraft:sharpness"))
                .field(FieldDef.integer("required_level", "Min Level"))
                .register();

        register("view_machine", tag -> {
            ViewMachineTask t = new ViewMachineTask(taskId(tag), desc(tag), "", 3.0f);
            t.deserializeNBT(tag);
            return t;
        }).icon("§bâ¬¡").label("View Machine (Phantasia)")
                .tooltip(
                        "Completes when the player views a Phantasia build guide for a specific machine for at least the required time.\nTarget: machine id (Phantasia multiblock definition id).")
                .field(FieldDef.text("machine_id", "Machine ID"))
                .field(FieldDef.integer("min_seconds", "Min Seconds"))
                .register();

        register("view_scene", tag -> {
            ViewSceneTask t = new ViewSceneTask(taskId(tag), desc(tag), "", 5.0f);
            t.deserializeNBT(tag);
            return t;
        }).icon("§bâ¬¢").label("View Scene (Phantasia)")
                .tooltip(
                        "Completes when the player views a multi-machine Phantasia scene for at least the required time.\nTarget: scene id (Phantasia scene definition id).")
                .field(FieldDef.text("scene_id", "Scene ID"))
                .field(FieldDef.integer("min_seconds", "Min Seconds"))
                .register();

        register("external_trigger", tag -> {
            ExternalTriggerTask t = new ExternalTriggerTask(taskId(tag), desc(tag), "", 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§dâš¡").label("External Trigger")
                .tooltip(
                        "Completes when an external mod/script fires QuestAPI.fireExternalEvent() with the matching trigger ID.\nTarget: trigger id (e.g. mymod:sun_eaten).\nCount: how many times the event must fire.")
                .field(FieldDef.text("trigger_id", "Trigger ID", "e.g. mymod:sun_eaten"))
                .field(FieldDef.integer("required", "Count (times fired)"))
                .register();
    }

    private static net.minecraft.resources.ResourceLocation taskId(CompoundTag tag) {
        try {
            return new net.minecraft.resources.ResourceLocation(tag.getString("task_id"));
        } catch (Exception e) {
            return new net.minecraft.resources.ResourceLocation(PhoenixChronicles.MOD_ID,
                    "task_" + UUID.randomUUID().toString().substring(0, 8));
        }
    }

    private static net.minecraft.network.chat.Component desc(CompoundTag tag) {
        try {
            if (tag.contains("description")) {
                net.minecraft.network.chat.Component c = net.minecraft.network.chat.Component.Serializer
                        .fromJson(tag.getString("description"));
                if (c != null) return c;
            }
        } catch (Exception ignored) {}
        return net.minecraft.network.chat.Component.literal(tag.getString("type"));
    }

    private PhoenixTaskRegistry() {}
}

