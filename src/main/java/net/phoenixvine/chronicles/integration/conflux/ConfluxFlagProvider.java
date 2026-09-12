package net.phoenixvine.chronicles.integration.conflux;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.flag.QuestFlagProvider;

import javax.annotation.Nullable;

/**
 * {@code conflux:} flag prefix -- lets a pack gate quest content on the *viewing player's* Conflux
 * (PhoenixCore) research team state, the same team-scoped state {@link ConfluxResearchTask} already
 * checks for task completion, now usable as a general condition too:
 * <ul>
 * <li>{@code conflux:<node_id>} -- research node {@code node_id} (e.g. {@code
 *   phoenixcore:reactor_theory}) is unlocked for the player's team.</li>
 * <li>{@code conflux:flag:<name>} -- research flag {@code name} is set for the player's team.</li>
 * </ul>
 * Research state is per-team, not global, so this only works where a player is actually in hand --
 * currently quest description {@code :::if} text (see QuestTasksScreen#isConditionMet). enableIf,
 * variant conditions, and the other evaluation sites have no player to offer and fall back to the
 * player-less {@link #evaluate(String, MinecraftServer)}, which can't resolve a team and always
 * returns false (with a one-time warning) rather than silently guessing.
 */
public class ConfluxFlagProvider implements QuestFlagProvider {

    @Override
    public String prefix() {
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
