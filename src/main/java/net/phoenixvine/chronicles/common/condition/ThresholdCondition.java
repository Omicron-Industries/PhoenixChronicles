package net.phoenixvine.chronicles.common.condition;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ThresholdCondition {

    private ThresholdCondition() {}

    private static final Pattern COMPARISON = Pattern.compile("^(.*?)(>=|<=|==|!=|>|<)(-?\\d+)$");

    public record Parsed(String id, String op, long threshold) {}

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
            default -> value >= threshold; 
        };
    }
}
