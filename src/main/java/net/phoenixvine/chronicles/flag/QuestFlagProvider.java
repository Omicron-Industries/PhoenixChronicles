package net.phoenixvine.chronicles.flag;

import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

public interface QuestFlagProvider {

    String prefix();

    boolean evaluate(String expression, @Nullable MinecraftServer server);
}
