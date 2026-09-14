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

    public static boolean hasEntry(String entryId) {
        return isAvailable() && ArchiveLoreCompatImpl.hasEntry(entryId);
    }

    public static void openEntry(Screen returnTo, String entryId) {
        if (isAvailable()) ArchiveLoreCompatImpl.openEntry(returnTo, entryId);
    }
}
