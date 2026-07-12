package net.phoenixvine.chronicles.flag;

import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

/**
 * A named provider that evaluates flag expressions for PhoenixQuestFlags.
 *
 * Register custom providers with
 * {@link PhoenixQuestFlags#registerProvider}.
 *
 * Expression format in quest SNBT:
 * enable_if: "[prefix:]expression"
 *
 * Example (custom provider with prefix "myplugin"):
 * enable_if: "myplugin:some_condition>=3"
 *
 * The expression string passed to {@link #evaluate} is everything AFTER the colon.
 *
 * Providers that require server context (game rules, world data) should return
 * {@code true} when {@code server} is null — the client uses flags for display
 * only; the server enforces completion. Returning {@code false} client-side would
 * hide quests from players even when the server would consider them active.
 *
 * <p>
 * <b>If your mod treats Phoenix Chronicles as an optional/soft dependency</b>
 * (declared non-mandatory in {@code mods.toml}), do not put your {@code isLoaded}
 * check and your {@link QuestFlagProvider} implementation (or the call to
 * {@link PhoenixQuestFlags#registerProvider}) in the same class. Calling any
 * static method on a class forces the JVM to load and verify that class's
 * <i>entire</i> bytecode, not just the method invoked — so if the risky
 * reference lives in the same class as the guard, evaluating the guard itself
 * already tries to resolve Chronicles' types and throws
 * {@code ClassNotFoundException} on installs that don't have this mod, before
 * the check ever gets a chance to prevent it. Split into two classes instead:
 * one containing only the {@code ModList.get().isLoaded("phoenix_chronicles")}
 * check, and a second (referenced only after that check passes) containing the
 * actual provider class and the {@code registerProvider} call.
 */
public interface QuestFlagProvider {

    /** The prefix that routes to this provider, e.g. {@code "mod"}, {@code "rule"}. */
    String prefix();

    /**
     * Evaluates an expression within this provider's domain.
     *
     * @param expression Everything after the prefix colon, e.g. {@code "refinedstorage"}
     *                   or {@code "randomTickSpeed>=3"}.
     * @param server     Nullable. Null on client-side display checks and during early
     *                   world load. Providers that need server state should return
     *                   {@code true} when this is null.
     */
    boolean evaluate(String expression, @Nullable MinecraftServer server);
}
