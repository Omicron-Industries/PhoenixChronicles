package net.phoenixvine.chronicles.condition;

/**
 * Thrown by {@link ConditionExprParser#parse} on malformed input, with a human-readable message.
 * Ported from Phoenix Archive's condition engine.
 */
public class ConditionSyntaxException extends RuntimeException {

    public ConditionSyntaxException(String message) {
        super(message);
    }
}
