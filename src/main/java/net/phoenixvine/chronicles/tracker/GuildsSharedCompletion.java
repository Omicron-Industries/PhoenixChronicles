package net.phoenixvine.chronicles.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.guilds.data.GuildManager;

import java.util.UUID;

final class GuildsSharedCompletion {

    private GuildsSharedCompletion() {}

    static boolean propagate(ServerPlayer source, QuestNode node) {
        GuildManager guildMgr = GuildManager.get(source.getServer().overworld());
        var pTeam = guildMgr.getGuildFor(source.getUUID());
        if (pTeam.isEmpty()) return false;

        for (UUID memberUUID : pTeam.get().getMembers()) {
            if (memberUUID.equals(source.getUUID())) continue;
            ServerPlayer member = source.getServer().getPlayerList().getPlayer(memberUUID);
            if (member == null) continue;
            PlayerQuestData data = QuestProgressTracker.resolveData(member);
            if (data != null && data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) {
                QuestProgressTracker.changeQuestState(member, node, QuestState.COMPLETED);
            }
        }
        return true;
    }
}
