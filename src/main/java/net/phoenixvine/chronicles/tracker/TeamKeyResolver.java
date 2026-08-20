package net.phoenixvine.chronicles.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.guilds.data.GuildManager;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

import java.util.Optional;

public final class TeamKeyResolver {

    private TeamKeyResolver() {}

    public static boolean anyTeamModLoaded() {
        var modList = net.minecraftforge.fml.ModList.get();
        if (modList == null) return false;
        return modList.isLoaded("phoenix_guilds") || modList.isLoaded("ftbteams");
    }

    public static Optional<String> resolve(ServerPlayer player) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("phoenix_guilds")) {
            GuildManager guildMgr = GuildManager.get(player.getServer().overworld());
            var guild = guildMgr.getGuildFor(player.getUUID());
            if (guild.isPresent()) return Optional.of("guild:" + guild.get().getId());
        }

        if (net.minecraftforge.fml.ModList.get().isLoaded("ftbteams") && FTBTeamsAPI.api().isManagerLoaded()) {
            var opt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(player.getUUID());
            if (opt.isPresent()) {
                var team = opt.get();
                if (team.isPartyTeam() || team.isServerTeam()) {
                    return Optional.of("ftbteam:" + team.getId());
                }
            }
        }

        net.minecraft.world.scores.Team team = player.getTeam();
        if (team != null) return Optional.of("sb:" + team.getName());

        return Optional.empty();
    }
}
