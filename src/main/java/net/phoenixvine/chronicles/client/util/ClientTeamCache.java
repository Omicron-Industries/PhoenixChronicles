package net.phoenixvine.chronicles.client.util;

import javax.annotation.Nullable;

public final class ClientTeamCache {

    private ClientTeamCache() {}

    @Nullable
    private static volatile String teamKey = null;

    public static void set(@Nullable String teamKey) {
        ClientTeamCache.teamKey = (teamKey == null || teamKey.isEmpty()) ? null : teamKey;
    }

    @Nullable
    public static String get() {
        return teamKey;
    }
}
