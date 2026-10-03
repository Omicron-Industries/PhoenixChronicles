package net.phoenixvine.chronicles.common.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.nio.file.Files;
import java.nio.file.Path;

public final class QuestEngineConfig {

    private QuestEngineConfig() {}

    private static boolean ae2StorageForItemFluidTasks = true;
    private static boolean ae2PushRewards = true;
    private static boolean emergencyRepeatable = false;
    private static int emergencyCooldownSeconds = 0;

    public static void load(Path configDir) {
        ae2StorageForItemFluidTasks = true;
        ae2PushRewards = true;
        emergencyRepeatable = false;
        emergencyCooldownSeconds = 0;
        Path file = configDir.resolve("engine_settings.snbt");
        if (!Files.exists(file)) {
            writeDefaults(file);
            return;
        }

        try {
            String content = Files.readString(file);
            CompoundTag root = TagParser.parseTag(content);
            if (root.contains("ae2_storage_for_item_fluid_tasks")) {
                ae2StorageForItemFluidTasks = root.getBoolean("ae2_storage_for_item_fluid_tasks");
            }
            if (root.contains("ae2_push_rewards")) {
                ae2PushRewards = root.getBoolean("ae2_push_rewards");
            }
            if (root.contains("emergency_repeatable")) {
                emergencyRepeatable = root.getBoolean("emergency_repeatable");
            }
            if (root.contains("emergency_cooldown_seconds")) {
                emergencyCooldownSeconds = Math.max(0, root.getInt("emergency_cooldown_seconds"));
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load engine_settings.snbt: " + e.getMessage());
        }
    }

    public static boolean isAe2StorageForItemFluidTasksEnabled() {
        return ae2StorageForItemFluidTasks;
    }

    public static boolean isAe2PushRewardsEnabled() {
        return ae2PushRewards;
    }

    public static boolean isEmergencyRepeatable() {
        return emergencyRepeatable;
    }

    public static int getEmergencyCooldownSeconds() {
        return emergencyCooldownSeconds;
    }

    private static void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                    {
                        ae2_storage_for_item_fluid_tasks: 1b,
                        ae2_push_rewards: 1b,
                        emergency_repeatable: 0b,
                        emergency_cooldown_seconds: 0
                    }
                    """);
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to write default engine_settings.snbt: " + e.getMessage());
        }
    }
}
