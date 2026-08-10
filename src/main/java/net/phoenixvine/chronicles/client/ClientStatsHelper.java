package net.phoenixvine.chronicles.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Player;

public final class ClientStatsHelper {

    private ClientStatsHelper() {}

    public static StatsCounter getStats(Player player) {
        return player instanceof LocalPlayer lp ? lp.getStats() : null;
    }
}
