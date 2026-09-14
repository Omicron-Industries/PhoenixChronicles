package net.phoenixvine.chronicles.integration.archive;

import net.minecraft.client.gui.screens.Screen;
import net.phoenix_archives.phoenix_archive.client.ArchiveClient;

final class ArchiveLoreCompatImpl {

    private ArchiveLoreCompatImpl() {}

    static boolean hasLoreFor(String questId) {
        return ArchiveClient.hasLoreForChroniclesQuest(questId);
    }

    static void openLoreFor(Screen returnTo, String questId) {
        ArchiveClient.openLoreForChroniclesQuest(returnTo, questId);
    }

    static boolean hasEntry(String entryId) {
        return ArchiveClient.hasLoreEntry(entryId);
    }

    static void openEntry(Screen returnTo, String entryId) {
        ArchiveClient.openLoreEntryById(returnTo, entryId);
    }
}
