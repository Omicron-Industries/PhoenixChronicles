package net.phoenixvine.chronicles.common.condition;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    ConditionNode EMPTY = new And(List.of());

    default boolean isEmpty() {
        return this instanceof And a && a.children().isEmpty();
    }

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
        return Optional.empty(); 
    }

    record NegatableLeaf(Leaf leaf, boolean negated) {}

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
