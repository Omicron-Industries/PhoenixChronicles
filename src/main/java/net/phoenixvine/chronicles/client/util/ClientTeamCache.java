package net.phoenixvine.chronicles.client.util;

import javax.annotation.Nullable;

/**
 * Client-side mirror of this player's own resolved team key (see TeamKeyResolver), sent down by
 * S2CSyncTeamKeyPacket at login. Needed because client-side flag evaluation (quest description
 * {@code :::if} text) has no server access, and both team backends behind TeamKeyResolver -- Phoenix
 * Guilds' GuildManager and FTB Teams' manager -- are server-side SavedData with no client mirror.
 */
public final class ClientTeamCache {

    private ClientTeamCache() {}

    @Nullable
    private static volatile String teamKey = null;

    public static void set(@Nullable String teamKey) {
        ClientTeamCache.teamKey = (teamKey == null || teamKey.isEmpty()) ? null : teamKey;
    }

    /** This client's own team key, or {@code null} if it's not on a team (or hasn't synced yet). */
    @Nullable
    public static String get() {
        return teamKey;
    }
}
