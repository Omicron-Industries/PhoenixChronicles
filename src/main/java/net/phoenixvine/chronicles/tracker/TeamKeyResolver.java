package net.phoenixvine.chronicles.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.guilds.data.GuildManager;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

import java.util.Optional;

/**
 * Resolves a stable, opaque "team key" for a player, trying (in order) Phoenix Guilds,
 * FTB Teams (party/server teams only, not the per-player default team), then vanilla
 * scoreboard teams. Empty if the player belongs to none of these — pooled-progress
 * quests then behave exactly like ordinary per-player ones for that player.
 *
 * Shared by {@link QuestProgressTracker}'s completion cascade and pooled task-progress
 * storage ({@link net.phoenixvine.chronicles.capability.PooledTaskProgress}), so both
 * features always agree on what "the same team" means.
 */
public final class TeamKeyResolver {

    private TeamKeyResolver() {}

    public static Optional<String> resolve(ServerPlayer player) {
        // Phoenix Guilds is supposed to be an optional compat, not a hard dependency - this used
        // to call GuildManager unconditionally, which is exactly backwards from "optional": this
        // method is called for EVERY pooled-progress task check (see TaskProgressAccess), so any
        // pack running pooled-progress quests without Phoenix Guilds installed would immediately
        // hit NoClassDefFoundError the first time any player made progress on one.
        if (net.minecraftforge.fml.ModList.get().isLoaded("phoenix_guilds")) {
            GuildManager guildMgr = GuildManager.get(player.getServer().overworld());
            var guild = guildMgr.getGuildFor(player.getUUID());
            if (guild.isPresent()) return Optional.of("guild:" + guild.get().getId());
        }

        if (FTBTeamsAPI.api().isManagerLoaded()) {
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
