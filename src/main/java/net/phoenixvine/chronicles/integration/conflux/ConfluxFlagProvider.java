package net.phoenixvine.chronicles.integration.conflux;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.common.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.common.flag.QuestFlagProvider;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class ConfluxFlagProvider implements QuestFlagProvider {

    @Override
    public @NotNull String prefix() {
        return "conflux";
    }

    @Override
    public boolean evaluate(String expression, @Nullable MinecraftServer server) {
        String context = PhoenixQuestFlags.currentContext();
        String warnKey = "conflux-no-player|" + context;
        if (WARNED_NO_PLAYER.add(warnKey)) {
            System.err.println("[Phoenix Chronicles] 'conflux:" + expression +
                    "' can't be evaluated without a player (Conflux research is per-team) -- only usable in " +
                    "quest description text, not enableIf/variants." +
                    (context != null ? " [" + context + "]" : "") + ".");
        }
        return false;
    }

    @Override
    public boolean evaluate(String expression, @Nullable MinecraftServer server, @Nullable Player player) {
        if (player == null) return evaluate(expression, server);
        if (!ConfluxCompat.isAvailable()) return false;

        if (expression.startsWith("flag:")) {
            return ConfluxCompat.hasFlag(player, expression.substring(5).trim());
        }
        try {
            return ConfluxCompat.isUnlocked(player, ResourceLocation.parse(expression.trim()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static final java.util.Set<String> WARNED_NO_PLAYER = java.util.Collections
            .newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
}
