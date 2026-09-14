package net.phoenixvine.chronicles.common.flag;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public interface QuestFlagProvider {

    String prefix();

    boolean evaluate(String expression, @Nullable MinecraftServer server);

    default boolean evaluate(String expression, @Nullable MinecraftServer server, @Nullable Player player) {
        return evaluate(expression, server);
    }
}
