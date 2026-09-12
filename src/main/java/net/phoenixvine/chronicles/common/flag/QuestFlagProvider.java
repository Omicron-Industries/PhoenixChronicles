package net.phoenixvine.chronicles.common.flag;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public interface QuestFlagProvider {

    String prefix();

    boolean evaluate(String expression, @Nullable MinecraftServer server);

    /**
     * Player-aware variant for providers whose state is per-player/team rather than global (e.g.
     * Conflux research, scoped to the player's research team) -- defaults to the player-less
     * {@link #evaluate(String, MinecraftServer)} for every other provider, so implementing this is
     * opt-in and doesn't change behavior for mod:/rule:/config:/kjs:. Only reached where a caller
     * actually has a player in hand (currently: quest description {@code :::if} text); every other
     * evaluation site (enableIf, variants, chapter/shader overrides) has no player to offer and always
     * falls through to the player-less overload.
     */
    default boolean evaluate(String expression, @Nullable MinecraftServer server, @Nullable Player player) {
        return evaluate(expression, server);
    }
}
