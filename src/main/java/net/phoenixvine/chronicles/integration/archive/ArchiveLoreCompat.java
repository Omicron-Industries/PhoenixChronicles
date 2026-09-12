package net.phoenixvine.chronicles.integration.archive;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;

/**
 * Optional bridge to Phoenix Archive's lore codex -- the other half of the deep link started by
 * Archive's own ArchiveClient.openChroniclesQuest. A quest node's "View Lore" context-menu item uses
 * this to jump to whichever Archive entry (if any) links back to that quest via its own
 * "chronicles_quest" condition.
 */
public final class ArchiveLoreCompat {

    private ArchiveLoreCompat() {}

    public static final String ARCHIVE_MOD_ID = "phoenix_archive";

    public static boolean isAvailable() {
        return ModList.get() != null && ModList.get().isLoaded(ARCHIVE_MOD_ID);
    }

    /** Whether any Archive lore entry links to this quest -- used to decide if the menu item shows. */
    public static boolean hasLoreFor(String questId) {
        return isAvailable() && ArchiveLoreCompatImpl.hasLoreFor(questId);
    }

    /** Opens the Archive on the entry linking this quest, if one exists, returning to {@code returnTo} on close. */
    public static void openLoreFor(Screen returnTo, String questId) {
        if (isAvailable()) ArchiveLoreCompatImpl.openLoreFor(returnTo, questId);
    }

    /**
     * Direct-by-id counterpart to {@link #hasLoreFor}/{@link #openLoreFor}, for the "archive_entry"
     * quest task type (ArchiveEntryTask) -- points straight at one entry's own id instead of searching
     * for a "chronicles_quest" condition.
     */
    public static boolean hasEntry(String entryId) {
        return isAvailable() && ArchiveLoreCompatImpl.hasEntry(entryId);
    }

    /** Opens the Archive on entry {@code entryId} directly, if it exists, returning to {@code returnTo} on close. */
    public static void openEntry(Screen returnTo, String entryId) {
        if (isAvailable()) ArchiveLoreCompatImpl.openEntry(returnTo, entryId);
    }
}
