package net.phoenixvine.chronicles.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

final class FtbTeamsSharedCompletion {

    private FtbTeamsSharedCompletion() {}

    static boolean propagate(ServerPlayer source, QuestNode node) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return false;
        var opt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(source.getUUID());
        if (opt.isEmpty()) return false;

        var team = opt.get();
        if (!team.isPartyTeam() && !team.isServerTeam()) return false;

        for (ServerPlayer member : team.getOnlineMembers()) {
            if (member.getUUID().equals(source.getUUID())) continue;
            PlayerQuestData data = QuestProgressTracker.resolveData(member);
            if (data != null && data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) {
                QuestProgressTracker.changeQuestState(member, node, QuestState.COMPLETED);
            }
        }
        return true;
    }
}
