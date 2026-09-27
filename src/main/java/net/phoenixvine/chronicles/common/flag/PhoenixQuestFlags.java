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

    /**
     * Evaluates a flag/condition expression. Supports full boolean logic, not just OR-of-ANDs:
     *
     * <ul>
     * <li>{@code !a} - NOT</li>
     * <li>{@code a & b} (or the legacy alias {@code a, b}) - AND</li>
     * <li>{@code a ^ b} - XOR</li>
     * <li>{@code a | b} - OR</li>
     * <li>{@code (...)} - grouping, for arbitrary nesting</li>
     * </ul>
     *
     * <p>
     * Precedence from loosest to tightest binding is OR, XOR, AND, NOT - the same as most
     * languages (e.g. {@code a | b & c} means {@code a | (b & c)}) - and parentheses override it.
     * NAND/NOR/XNOR aren't separate operators since they're just a negated AND/OR/XOR, e.g.
     * {@code !(a & b)}.
     *
     * <p>
     * The old flat "{@code a,b|c,!d}" syntax (comma-AND inside pipe-OR, no grouping) still
     * evaluates identically to before - {@code ,} is kept as an AND alias specifically so every
     * expression written before this parser existed keeps working unchanged.
     *
     * <p>
     * A malformed expression (unbalanced parens, an operator with nothing on one side, etc.) is
     * logged once and treated as true, matching this class's existing "unknown -&gt; default true"
     * philosophy elsewhere, so a typo in one quest's condition can't hard-fail quest loading.
     */
    public static boolean evaluate(@Nullable String expression, @Nullable MinecraftServer server,
                                   @Nullable Player player) {
        if (expression == null || expression.isBlank()) return true;

        String teamKey = player != null ? resolveScopeKey(player) : null;
        try {
            ExpressionParser parser = new ExpressionParser(expression, server, player, teamKey);
            boolean result = parser.parseOr();
            parser.expectEnd();
            return result;
        } catch (ExpressionParser.ParseException e) {
            String ctxSuffix = currentContext != null ? " [" + currentContext + "]" : "";
            if (warnedUnknown.add("parse:" + expression + ctxSuffix)) {
                System.err.println("[Phoenix Chronicles] Failed to parse flag expression '" + expression + "': " +
                        e.getMessage() + " - defaulting to true." + ctxSuffix);
            }
            return true;
        }
    }

    private static final class ExpressionParser {

        private static final String OPERATOR_CHARS = "()!&|^,";

        private final String src;
        private final MinecraftServer server;
        private final Player player;
        private final String teamKey;
        private int pos = 0;

        ExpressionParser(String src, @Nullable MinecraftServer server, @Nullable Player player,
                         @Nullable String teamKey) {
            this.src = src;
            this.server = server;
            this.player = player;
            this.teamKey = teamKey;
        }

        boolean parseOr() {
            boolean result = parseXor();
            skipWs();
            while (peek() == '|') {
                pos++;
                boolean rhs = parseXor();
                result = result || rhs;
                skipWs();
            }
            return result;
        }

        private boolean parseXor() {
            boolean result = parseAnd();
            skipWs();
            while (peek() == '^') {
                pos++;
                boolean rhs = parseAnd();
                result = result ^ rhs;
                skipWs();
            }
            return result;
        }

        private boolean parseAnd() {
            boolean result = parseNot();
            skipWs();
            while (peek() == '&' || peek() == ',') {
                pos++;
                boolean rhs = parseNot();
                result = result && rhs;
                skipWs();
            }
            return result;
        }

        private boolean parseNot() {
            skipWs();
            if (peek() == '!') {
                pos++;
                return !parseNot();
            }
            return parseAtom();
        }

        private boolean parseAtom() {
            skipWs();
            if (peek() == '(') {
                pos++;
                boolean result = parseOr();
                skipWs();
                if (peek() != ')') throw new ParseException("expected ')' at position " + pos);
                pos++;
                return result;
            }
            String term = readTerm();
            if (term.isEmpty()) throw new ParseException("expected a term at position " + pos);
            return evaluateTerm(term, server, player, teamKey);
        }

        private String readTerm() {
            int start = pos;
            while (pos < src.length() && OPERATOR_CHARS.indexOf(src.charAt(pos)) < 0 &&
                    !Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
            return src.substring(start, pos);
        }

        private char peek() {
            return pos < src.length() ? src.charAt(pos) : '\0';
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        void expectEnd() {
            skipWs();
            if (pos < src.length()) throw new ParseException("unexpected '" + src.charAt(pos) + "' at position " + pos);
        }

        private static final class ParseException extends RuntimeException {

            ParseException(String message) {
                super(message);
            }
        }
    }

    private static boolean evaluateTerm(String term, @Nullable MinecraftServer server, @Nullable Player player,
                                        @Nullable String teamKey) {
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
