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

    static void unlockEntry(net.minecraft.server.level.ServerPlayer player, String entryId) {
        net.phoenix_archives.phoenix_archive.ArchiveAPI.unlockEntry(player, entryId);
    }

    /** Hands every {@link ArchiveEntries} spec to Archive each time it collects lore entries. */
    static void installEntryListener() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.phoenix_archives.phoenix_archive.api.ArchiveRegisterEntriesEvent event) -> {
                    for (ArchiveEntries.Spec s : ArchiveEntries.registered()) {
                        java.util.List<net.phoenix_archives.phoenix_archive.api.ConditionNode> leaves = new java.util.ArrayList<>();
                        for (String q : s.questConditions()) {
                            leaves.add(new net.phoenix_archives.phoenix_archive.api.ConditionNode.Leaf(
                                    "chronicles_quest", q));
                        }
                        net.phoenix_archives.phoenix_archive.api.ConditionNode tree;
                        if (leaves.isEmpty()) {
                            tree = net.phoenix_archives.phoenix_archive.api.ConditionNode.EMPTY;
                        } else if (s.anyQuest()) {
                            tree = new net.phoenix_archives.phoenix_archive.api.ConditionNode.Or(leaves);
                        } else {
                            tree = new net.phoenix_archives.phoenix_archive.api.ConditionNode.And(leaves);
                        }
                        event.register(s.id(), new net.phoenix_archives.phoenix_archive.api.LoreEntry(
                                s.id().toString(), s.title(), s.category(), s.content(), s.iconItem(), 0,
                                s.lockedContent(), s.voiceLine(), tree, s.order(), s.shader(), s.hidden(),
                                s.hiddenUntilId()));
                    }
                });
    }

    static boolean hasEntry(String entryId) {
        return ArchiveClient.hasLoreEntry(entryId);
    }

    static void openEntry(Screen returnTo, String entryId) {
        ArchiveClient.openLoreEntryById(returnTo, entryId);
    }
}
