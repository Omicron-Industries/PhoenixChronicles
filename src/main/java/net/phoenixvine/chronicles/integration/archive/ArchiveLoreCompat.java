package net.phoenixvine.chronicles.integration.archive;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;

public final class ArchiveLoreCompat {

    private ArchiveLoreCompat() {}

    public static final String ARCHIVE_MOD_ID = "phoenix_archive";

    public static boolean isAvailable() {
        return ModList.get() != null && ModList.get().isLoaded(ARCHIVE_MOD_ID);
    }

    public static boolean hasLoreFor(String questId) {
        return isAvailable() && ArchiveLoreCompatImpl.hasLoreFor(questId);
    }

    public static void openLoreFor(Screen returnTo, String questId) {
        if (isAvailable()) ArchiveLoreCompatImpl.openLoreFor(returnTo, questId);
    }

    public static void registerRewards() {
        if (!isAvailable()) return;
        net.phoenixvine.chronicles.common.registry.PhoenixRewardRegistry.register("archive_unlock")
                .label("Unlock Archive entry")
                .tooltip("Unlocks a Phoenix Archive lore entry for the player.")
                .requiresMod(ARCHIVE_MOD_ID)
                .field(net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry.FieldDef.text("entry_id",
                        "Entry id", "e.g. phoenix_archive:my_entry"))
                .summary(data -> "Unlock Archive entry: " + data.getString("entry_id"))
                .onGrant((player, data) -> {
                    String id = data.getString("entry_id").trim();
                    if (!id.isEmpty()) ArchiveLoreCompatImpl.unlockEntry(player, id);
                })
                .register();
    }

    public static boolean hasEntry(String entryId) {
        return isAvailable() && ArchiveLoreCompatImpl.hasEntry(entryId);
    }

    public static void openEntry(Screen returnTo, String entryId) {
        if (isAvailable()) ArchiveLoreCompatImpl.openEntry(returnTo, entryId);
    }
}
