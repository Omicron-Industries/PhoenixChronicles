package net.phoenixvine.chronicles.common.flag;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.common.registry.ChapterFlagRegistry;
import net.phoenixvine.chronicles.common.tracker.TeamKeyResolver;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

public final class PhoenixQuestFlags {

    private PhoenixQuestFlags() {}

    private static final Map<String, QuestFlagProvider> providers = new ConcurrentHashMap<>();

    public static final ConfigFileFlagProvider CONFIG = new ConfigFileFlagProvider();
    public static final KubeJsFlagProvider KJS = new KubeJsFlagProvider();

    static {
        registerProvider(new ModLoadedFlagProvider());
        registerProvider(new GameRuleFlagProvider());
        registerProvider(CONFIG);
        registerProvider(KJS);
        registerProvider(new net.phoenixvine.chronicles.integration.conflux.ConfluxFlagProvider());
    }

    public static void registerProvider(QuestFlagProvider provider) {
        providers.put(provider.prefix(), provider);
    }

    private static final String GLOBAL = "";
    private static final Map<String, Map<String, Boolean>> staticFlags = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, BooleanSupplier>> conditions = new ConcurrentHashMap<>();
    private static final Set<String> warnedUnknown = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void setFlag(String name, boolean value) {
        setFlag(GLOBAL, name, value);
    }

    public static void setFlag(String teamKey, String name, boolean value) {
        staticFlags.computeIfAbsent(bucket(teamKey), k -> new ConcurrentHashMap<>()).put(name, value);
        Map<String, BooleanSupplier> teamConditions = conditions.get(bucket(teamKey));
        if (teamConditions != null) teamConditions.remove(name);
    }

    public static void registerCondition(String name, BooleanSupplier condition) {
        registerCondition(GLOBAL, name, condition);
    }

    public static void registerCondition(String teamKey, String name, BooleanSupplier condition) {
        Map<String, Boolean> teamFlags = staticFlags.get(bucket(teamKey));
        if (teamFlags != null) teamFlags.remove(name);
        conditions.computeIfAbsent(bucket(teamKey), k -> new ConcurrentHashMap<>()).put(name, condition);
    }

    public static void clearFlag(String name) {
        clearFlag(GLOBAL, name);
    }

    public static void clearFlag(String teamKey, String name) {
        Map<String, Boolean> teamFlags = staticFlags.get(bucket(teamKey));
        if (teamFlags != null) teamFlags.remove(name);
        Map<String, BooleanSupplier> teamConditions = conditions.get(bucket(teamKey));
        if (teamConditions != null) teamConditions.remove(name);
    }

    public static void setFlagForPlayer(Player player, String name, boolean value) {
        setFlag(resolveScopeKey(player), name, value);
    }

    public static void registerConditionForPlayer(Player player, String name, BooleanSupplier condition) {
        registerCondition(resolveScopeKey(player), name, condition);
    }

    public static void clearFlagForPlayer(Player player, String name) {
        clearFlag(resolveScopeKey(player), name);
    }

    private static String bucket(@Nullable String teamKey) {
        return (teamKey == null || teamKey.isEmpty()) ? GLOBAL : teamKey;
    }

    public static String resolveScopeKey(Player player) {
        String teamKey = TeamKeyResolver.resolveAny(player).orElse(null);
        return teamKey != null ? teamKey : "player:" + player.getUUID();
    }

    private static volatile String currentContext = null;

    @Nullable
    public static String currentContext() {
        return currentContext;
    }

    public static boolean evaluate(@Nullable String expression) {
        return evaluate(expression, null);
    }

    public static boolean evaluate(@Nullable String expression, @Nullable MinecraftServer server,
                                   @Nullable String context) {
        return evaluate(expression, server, null, context);
    }

    public static boolean evaluate(@Nullable String expression, @Nullable MinecraftServer server,
                                   @Nullable Player player, @Nullable String context) {
        String prev = currentContext;
        currentContext = context;
        try {
            return evaluate(expression, server, player);
        } finally {
            currentContext = prev;
        }
    }

    public static boolean evaluate(@Nullable String expression, @Nullable MinecraftServer server) {
        return evaluate(expression, server, (Player) null);
    }

    public static boolean evaluate(@Nullable String expression, @Nullable MinecraftServer server,
                                   @Nullable Player player) {
        if (expression == null || expression.isBlank()) return true;

        String teamKey = player != null ? resolveScopeKey(player) : null;

        for (String orClause : expression.split("\\|")) {
            boolean andResult = true;
            for (String part : orClause.split(",")) {
                String term = part.trim();
                if (!term.isEmpty() && !evaluateTerm(term, server, player, teamKey)) {
                    andResult = false;
                    break;
                }
            }
            if (andResult) return true;
        }
        return false;
    }

    private static boolean evaluateTerm(String term, @Nullable MinecraftServer server, @Nullable Player player,
                                        @Nullable String teamKey) {
        if (term.startsWith("!")) return !evaluateTerm(term.substring(1), server, player, teamKey);
        int colon = term.indexOf(':');
        if (colon > 0) {
            String prefix = term.substring(0, colon);
            String rest = term.substring(colon + 1);

            if ("flag".equals(prefix)) return evaluateStaticFlag(rest, teamKey);

            QuestFlagProvider provider = providers.get(prefix);
            if (provider != null) return provider.evaluate(rest, server, player);

            String ctxSuffix1 = currentContext != null ? " [" + currentContext + "]" : "";
            if (warnedUnknown.add("prefix:" + prefix + ctxSuffix1)) {
                System.err.println("[Phoenix Chronicles] Unknown flag prefix '" + prefix + "' in expression '" + term +
                        "'. Register a provider with PhoenixQuestFlags.registerProvider()." + ctxSuffix1);
            }
            return false;
        }

        return evaluateStaticFlag(term, teamKey);
    }

    private static boolean evaluateStaticFlag(String name, @Nullable String teamKey) {
        if (teamKey != null) {
            Map<String, Boolean> teamFlags = staticFlags.get(teamKey);
            if (teamFlags != null && teamFlags.containsKey(name)) return teamFlags.get(name);

            Map<String, BooleanSupplier> teamConditions = conditions.get(teamKey);
            if (teamConditions != null && teamConditions.containsKey(name)) {
                return teamConditions.get(name).getAsBoolean();
            }
        }

        Map<String, Boolean> globalFlags = staticFlags.get(GLOBAL);
        Boolean staticVal = globalFlags != null ? globalFlags.get(name) : null;
        if (staticVal != null) return staticVal;

        Map<String, BooleanSupplier> globalConditions = conditions.get(GLOBAL);
        BooleanSupplier dyn = globalConditions != null ? globalConditions.get(name) : null;
        if (dyn != null) return dyn.getAsBoolean();

        String ctxSuffix = currentContext != null ? " [" + currentContext + "]" : "";
        if (warnedUnknown.add(name + ctxSuffix)) {
            System.err.println("[Phoenix Chronicles] Unknown quest flag '" + name +
                    "' : defaulting to true. Use PhoenixQuestFlags.setFlag() or" +
                    " registerCondition() to register it, or use a provider prefix" + " (mod:, config:, rule:, kjs:)." +
                    ctxSuffix);
        }
        return true;
    }

    public static String describeFlag(Player player, String name) {
        String scopeKey = resolveScopeKey(player);
        Map<String, Boolean> scopedFlags = staticFlags.get(scopeKey);
        Map<String, BooleanSupplier> scopedConditions = conditions.get(scopeKey);
        Map<String, Boolean> globalFlags = staticFlags.get(GLOBAL);
        Map<String, BooleanSupplier> globalConditions = conditions.get(GLOBAL);

        String sb = "scope=" + scopeKey + "  " +
                "scoped=" + describeTier(scopedFlags, scopedConditions, name) + "  " +
                "global=" + describeTier(globalFlags, globalConditions, name) + "  " +
                "resolved=" + evaluateStaticFlag(name, scopeKey);
        return sb;
    }

    private static String describeTier(@Nullable Map<String, Boolean> flags,
                                       @Nullable Map<String, BooleanSupplier> conds, String name) {
        if (flags != null && flags.containsKey(name)) return String.valueOf(flags.get(name));
        if (conds != null && conds.containsKey(name)) return conds.get(name).getAsBoolean() + " (dynamic)";
        return "unset";
    }

    public static void invalidateCaches() {
        CONFIG.invalidateCache();
        KJS.invalidate();
        ChapterFlagRegistry.clear();
    }
}
