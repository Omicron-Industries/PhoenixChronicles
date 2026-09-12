package net.phoenixvine.chronicles.common.condition;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Boolean condition tree for {@code :::if} markdown blocks (see RichBlock.ConditionalSection) --
 * ported from Phoenix Archive's condition system (same engine, same AND/OR/NOT/parens grammar via
 * {@link ConditionExprParser}), with a leaf vocabulary grounded in Chronicles' own quest state instead
 * of Archive's item/dimension/biome signals:
 * <ul>
 * <li>{@code quest:<id>} -- that quest is COMPLETED</li>
 * <li>{@code quest_unlocked:<id>} -- that quest is at least UNLOCKED or ACTIVE (visible/started,
 * not necessarily done)</li>
 * <li>{@code quest_progress:<id><op><percent>} -- that quest's task-completion percentage (0-100)
 * compares against {@code <percent>}, e.g. {@code quest_progress:main/forge>=50}. See
 * {@link ThresholdCondition} for the operator grammar.</li>
 * </ul>
 * See {@link ConditionEvaluator} for how a tree gets checked against live quest state.
 */
public sealed interface ConditionNode permits ConditionNode.Leaf, ConditionNode.And, ConditionNode.Or,
                                      ConditionNode.Not {

    record Leaf(String type, String value) implements ConditionNode {}

    record And(List<ConditionNode> children) implements ConditionNode {

        public And {
            children = List.copyOf(children);
        }
    }

    record Or(List<ConditionNode> children) implements ConditionNode {

        public Or {
            children = List.copyOf(children);
        }
    }

    record Not(ConditionNode child) implements ConditionNode {}

    /** An empty AND ("no conditions") -- always satisfied. */
    ConditionNode EMPTY = new And(List.of());

    default boolean isEmpty() {
        return this instanceof And a && a.children().isEmpty();
    }

    /**
     * Depth-first search for the first {@link Leaf} of the given type, ignoring boolean structure and
     * skipping anything under a {@link Not}.
     */
    default @NotNull Optional<String> findLeafValue(@NotNull String type) {
        if (this instanceof Leaf l) return type.equals(l.type()) ? Optional.of(l.value()) : Optional.empty();
        if (this instanceof And a) {
            return a.children().stream().map(c -> c.findLeafValue(type))
                    .filter(Optional::isPresent).findFirst().orElseGet(Optional::empty);
        }
        if (this instanceof Or o) {
            return o.children().stream().map(c -> c.findLeafValue(type))
                    .filter(Optional::isPresent).findFirst().orElseGet(Optional::empty);
        }
        return Optional.empty(); // Not -- a negated leaf isn't "the" value of that type
    }

    /** A leaf paired with whether it sits under an odd number of enclosing {@link Not}s. */
    record NegatableLeaf(Leaf leaf, boolean negated) {}

    /**
     * All leaves in this tree, depth-first, each tagged with whether it's effectively negated. Loses
     * AND/OR grouping -- built for simple "list every condition and mark whether it's currently met"
     * displays, not for anything that needs to reconstruct the boolean structure.
     */
    default @NotNull List<NegatableLeaf> collectLeaves() {
        List<NegatableLeaf> out = new ArrayList<>();
        collectLeavesInto(this, false, out);
        return out;
    }

    private static void collectLeavesInto(ConditionNode node, boolean negated, @NotNull List<NegatableLeaf> out) {
        if (node instanceof Leaf l) {
            out.add(new NegatableLeaf(l, negated));
        } else if (node instanceof And a) {
            a.children().forEach(c -> collectLeavesInto(c, negated, out));
        } else if (node instanceof Or o) {
            o.children().forEach(c -> collectLeavesInto(c, negated, out));
        } else if (node instanceof Not n) {
            collectLeavesInto(n.child(), !negated, out);
        }
    }
}
