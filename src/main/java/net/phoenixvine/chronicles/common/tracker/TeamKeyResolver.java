package net.phoenixvine.chronicles.common.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.guilds.data.GuildManager;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

import java.util.Optional;

public final class TeamKeyResolver {

    private TeamKeyResolver() {}

    /**
     * Works from either side: on the server, resolves the team key live (see {@link #resolve}); on the
     * client, {@code player} can't be a {@link ServerPlayer} (Guilds' GuildManager and FTB Teams' own
     * manager are both server-side SavedData with no client mirror), so this reads the value the server
     * already synced down via S2CSyncTeamKeyPacket instead. Used by flag/condition evaluation, which
     * runs on both sides (quest description {@code :::if} text is client-only; enableIf/variants can run
     * on either).
     */
    public static Optional<String> resolveAny(Player player) {
        if (player instanceof ServerPlayer sp) return resolve(sp);
        String clientKey = net.phoenixvine.chronicles.client.util.ClientTeamCache.get();
        return Optional.ofNullable(clientKey);
    }

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
