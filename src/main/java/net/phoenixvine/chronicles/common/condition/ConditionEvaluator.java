package net.phoenixvine.chronicles.common.condition;

import org.jetbrains.annotations.NotNull;

/**
 * Walks a {@link ConditionNode} tree, resolving each {@link ConditionNode.Leaf} through a caller-
 * supplied {@link LeafChecker}. Ported from Phoenix Archive's condition engine -- same evaluation
 * semantics, just checked against Chronicles' own quest state instead of Archive's signal set.
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    @FunctionalInterface
    public interface LeafChecker {

        boolean isMet(String type, String value);
    }

    /**
     * An empty AND (no conditions at all) evaluates to {@code true}; an empty OR evaluates to
     * {@code false}, as is standard.
     */
    public static boolean evaluate(ConditionNode node, @NotNull LeafChecker checker) {
        if (node instanceof ConditionNode.Leaf l) {
            if (l.value() == null || l.value().isEmpty()) return true;
            return checker.isMet(l.type(), l.value());
        }
        if (node instanceof ConditionNode.And a) {
            return a.children().stream().allMatch(c -> evaluate(c, checker));
        }
        if (node instanceof ConditionNode.Or o) {
            return o.children().stream().anyMatch(c -> evaluate(c, checker));
        }
        if (node instanceof ConditionNode.Not n) {
            return !evaluate(n.child(), checker);
        }
        throw new IllegalStateException("Unknown ConditionNode subtype: " + node);
    }
}
