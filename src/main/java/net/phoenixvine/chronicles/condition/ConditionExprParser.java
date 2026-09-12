package net.phoenixvine.chronicles.condition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses (and renders back) the small boolean-expression language used for {@code :::if} markdown
 * blocks: {@code AND} / {@code OR} / {@code NOT} (case-insensitive keywords) and parens over
 * {@code type:value} leaves, e.g.
 * 
 * <pre>
 *   quest:main/forge AND (quest_unlocked:main/reactor OR quest_progress:main/reactor>=50)
 *   AND NOT quest:main/reactor_meltdown
 * </pre>
 * 
 * OR binds loosest, then AND, then NOT, then parens/leaves. A leaf's value may itself contain colons
 * (namespaced quest ids); only the first colon in a token splits its type from its value.
 *
 * <p>
 * Ported verbatim from Phoenix Archive's condition engine -- this class has no dependency on
 * anything Chronicles- or Archive-specific, it just operates on {@link ConditionNode}.
 */
public final class ConditionExprParser {

    private ConditionExprParser() {}

    private static final Pattern TOKEN = Pattern.compile("\\(|\\)|\\S+");

    /**
     * Parses {@code expr} into a tree; a blank string parses to {@link ConditionNode#EMPTY}. Throws
     * {@link ConditionSyntaxException} with a human-readable message on malformed input.
     */
    public static ConditionNode parse(@Nullable String expr) {
        if (expr == null || expr.isBlank()) return ConditionNode.EMPTY;
        Cursor c = new Cursor(tokenize(expr));
        ConditionNode node = parseOr(c);
        if (c.hasNext()) throw new ConditionSyntaxException("Unexpected trailing text near '" + c.peek() + "'");
        return node;
    }

    /** Renders a tree back into this same syntax. */
    public static @NotNull String render(ConditionNode node) {
        return render(node, false);
    }

    private static @NotNull String render(ConditionNode node, boolean parenthesizeIfCompound) {
        if (node instanceof ConditionNode.Leaf l) return l.type() + ":" + l.value();
        if (node instanceof ConditionNode.Not n) return "NOT " + render(n.child(), true);
        if (node instanceof ConditionNode.And a) {
            if (a.children().isEmpty()) return "";
            String joined = a.children().stream().map(c -> render(c, true))
                    .reduce((x, y) -> x + " AND " + y).orElse("");
            return parenthesizeIfCompound && a.children().size() > 1 ? "(" + joined + ")" : joined;
        }
        if (node instanceof ConditionNode.Or o) {
            if (o.children().isEmpty()) return "";
            String joined = o.children().stream().map(c -> render(c, true))
                    .reduce((x, y) -> x + " OR " + y).orElse("");
            return parenthesizeIfCompound && o.children().size() > 1 ? "(" + joined + ")" : joined;
        }
        throw new IllegalStateException("Unknown ConditionNode subtype: " + node);
    }

    private static @NotNull List<String> tokenize(@NotNull String expr) {
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(expr);
        while (m.find()) out.add(m.group());
        return out;
    }

    private static ConditionNode parseOr(@NotNull Cursor c) {
        ConditionNode left = parseAnd(c);
        List<ConditionNode> children = null;
        while (c.hasNext() && c.peek().equalsIgnoreCase("OR")) {
            c.next();
            if (children == null) {
                children = new ArrayList<>();
                children.add(left);
            }
            children.add(parseAnd(c));
        }
        return children == null ? left : new ConditionNode.Or(children);
    }

    private static ConditionNode parseAnd(@NotNull Cursor c) {
        ConditionNode left = parseUnary(c);
        List<ConditionNode> children = null;
        while (c.hasNext() && c.peek().equalsIgnoreCase("AND")) {
            c.next();
            if (children == null) {
                children = new ArrayList<>();
                children.add(left);
            }
            children.add(parseUnary(c));
        }
        return children == null ? left : new ConditionNode.And(children);
    }

    private static ConditionNode parseUnary(@NotNull Cursor c) {
        if (c.hasNext() && c.peek().equalsIgnoreCase("NOT")) {
            c.next();
            return new ConditionNode.Not(parseUnary(c));
        }
        return parsePrimary(c);
    }

    private static ConditionNode parsePrimary(@NotNull Cursor c) {
        if (!c.hasNext()) throw new ConditionSyntaxException("Expected a condition, found end of input");
        String tok = c.next();

        if (tok.equals("(")) {
            ConditionNode inner = parseOr(c);
            if (!c.hasNext() || !c.peek().equals(")")) throw new ConditionSyntaxException("Missing closing ')'");
            c.next();
            return inner;
        }
        if (tok.equals(")")) throw new ConditionSyntaxException("Unexpected ')' with no matching '('");
        if (tok.equalsIgnoreCase("AND") || tok.equalsIgnoreCase("OR")) {
            throw new ConditionSyntaxException("Unexpected '" + tok + "' -- expected a condition before it");
        }

        int sep = tok.indexOf(':');
        if (sep <= 0) {
            throw new ConditionSyntaxException(
                    "'" + tok + "' isn't a valid condition -- expected type:value (e.g. quest:main/forge)");
        }
        return new ConditionNode.Leaf(tok.substring(0, sep), tok.substring(sep + 1));
    }

    private static final class Cursor {

        private final List<String> tokens;
        private int pos = 0;

        Cursor(List<String> tokens) {
            this.tokens = tokens;
        }

        boolean hasNext() {
            return pos < tokens.size();
        }

        String peek() {
            return tokens.get(pos);
        }

        String next() {
            return tokens.get(pos++);
        }
    }
}
