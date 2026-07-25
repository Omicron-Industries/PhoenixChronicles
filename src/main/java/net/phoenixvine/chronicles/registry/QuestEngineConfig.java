package net.phoenixvine.chronicles.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.nio.file.Files;
import java.nio.file.Path;

public final class QuestEngineConfig {

    private QuestEngineConfig() {}

    private static boolean ae2StorageForItemFluidTasks = true;

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
