package net.phoenixvine.chronicles.common.condition;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a {@code quest_progress} leaf's value into a quest id plus an optional comparison against its
 * completion percentage (0-100) -- {@code quest_progress:main/forge} (no operator) means "progress is
 * at least 1%", i.e. "has any task been started"; {@code quest_progress:main/forge>=50},
 * {@code quest_progress:main/forge==100}, etc. compare against a specific percentage instead. Supports
 * {@code >= <= == != > <}. No spaces are allowed around the operator -- the whole thing must be one
 * token, since the condition-expression tokenizer (ConditionExprParser) splits on whitespace and has no
 * notion of "continues the previous token".
 *
 * <p>
 * Ported from Phoenix Archive's ExternalCondition (same grammar, renamed here since "external"
 * doesn't fit Chronicles' own vocabulary -- this is about *quest* progress specifically).
 */
public final class ThresholdCondition {

    private ThresholdCondition() {}

    private static final Pattern COMPARISON = Pattern.compile("^(.*?)(>=|<=|==|!=|>|<)(-?\\d+)$");

    public record Parsed(String id, String op, long threshold) {}

    /** {@code value} is the raw leaf value, e.g. {@code "main/forge"} or {@code "main/forge>=50"}. */
    public static @NotNull Parsed parse(@NotNull String value) {
        Matcher m = COMPARISON.matcher(value);
        if (m.matches()) {
            return new Parsed(m.group(1), m.group(2), Long.parseLong(m.group(3)));
        }
        return new Parsed(value, ">=", 1);
    }

    public static boolean test(long value, @NotNull String op, long threshold) {
        return switch (op) {
            case "<=" -> value <= threshold;
            case "==" -> value == threshold;
            case "!=" -> value != threshold;
            case ">" -> value > threshold;
            case "<" -> value < threshold;
            default -> value >= threshold; // ">=" and the no-operator default both land here
        };
    }
}
