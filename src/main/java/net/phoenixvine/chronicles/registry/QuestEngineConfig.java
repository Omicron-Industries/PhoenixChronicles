package net.phoenixvine.chronicles.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small pack-wide engine behavior toggles that aren't per-quest/per-category (see
 * {@link CategoryPrereqDefaults} for those) and aren't a per-player client preference (see
 * {@link net.phoenixvine.chronicles.codec.QuestChroniclesSettings} for those) - this is
 * server-authoritative, since it affects quest COMPLETION logic and must be the same answer for
 * every player, not something each client could independently toggle.
 *
 * ── Config file ───────────────────────────────────────────────────────────────
 *
 * config/phoenix_chronicles/engine_settings.snbt
 *
 * {
 * ae2_storage_for_item_fluid_tasks: 0b
 * }
 *
 * Any field not present keeps its hardcoded default below.
 */
public final class QuestEngineConfig {

    private QuestEngineConfig() {}

    /**
     * Whether the built-in item_check/fluid_check tasks (ItemRequirementTask/FluidRequirementTask)
     * also count matching items/fluids stored in the player's Applied Energistics 2 ME network
     * (read through their currently-held wireless terminal, same as the dedicated
     * ae2_item_storage/ae2_fluid_storage task types) IN ADDITION TO physical inventory, when AE2
     * is installed. On by default - a pack dev who wants these tasks to require PHYSICALLY
     * carrying the item (not just having it stored away) can turn this off without needing to
     * avoid AE2 entirely or hunt down every item_check/fluid_check task individually.
     */
    private static boolean ae2StorageForItemFluidTasks = true;

    /** Safe to call multiple times - resets to defaults each time, same convention as CategoryPrereqDefaults. */
    public static void load(Path configDir) {
        ae2StorageForItemFluidTasks = true;
        Path file = configDir.resolve("engine_settings.snbt");
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file);
            CompoundTag root = TagParser.parseTag(content);
            if (root.contains("ae2_storage_for_item_fluid_tasks")) {
                ae2StorageForItemFluidTasks = root.getBoolean("ae2_storage_for_item_fluid_tasks");
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load engine_settings.snbt: " + e.getMessage());
        }
    }

    public static boolean isAe2StorageForItemFluidTasksEnabled() {
        return ae2StorageForItemFluidTasks;
    }
}
