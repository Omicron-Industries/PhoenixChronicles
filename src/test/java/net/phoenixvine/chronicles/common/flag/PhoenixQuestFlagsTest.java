package net.phoenixvine.chronicles.common.flag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoenixQuestFlagsTest {

    @AfterEach
    void clearFlags() {
        for (String name : new String[] { "a", "b", "c", "d" }) PhoenixQuestFlags.clearFlag(name);
    }

    private static boolean eval(String expr) {
        return PhoenixQuestFlags.evaluate(expr);
    }

    @Test
    void blankExpressionIsVacuouslyTrue() {
        assertTrue(eval(null));
        assertTrue(eval(""));
        assertTrue(eval("   "));
    }

    @Test
    void legacyCommaAndPipeSyntaxStillWorks() {
        PhoenixQuestFlags.setFlag("a", true);
        PhoenixQuestFlags.setFlag("b", true);
        PhoenixQuestFlags.setFlag("c", false);
        PhoenixQuestFlags.setFlag("d", false);

        assertTrue(eval("a,b|c,!d"));
        assertFalse(eval("a,c"));
        assertTrue(eval("a|c"));
    }

    @Test
    void newAndOperatorMatchesComma() {
        PhoenixQuestFlags.setFlag("a", true);
        PhoenixQuestFlags.setFlag("b", false);

        assertFalse(eval("a & b"));
        PhoenixQuestFlags.setFlag("b", true);
        assertTrue(eval("a & b"));
    }

    @Test
    void xorIsTrueOnlyWhenExactlyOneSideIsTrue() {
        PhoenixQuestFlags.setFlag("a", true);
        PhoenixQuestFlags.setFlag("b", false);
        assertTrue(eval("a ^ b"));

        PhoenixQuestFlags.setFlag("b", true);
        assertFalse(eval("a ^ b"));

        PhoenixQuestFlags.setFlag("a", false);
        assertTrue(eval("a ^ b"));

        PhoenixQuestFlags.setFlag("b", false);
        assertFalse(eval("a ^ b"));
    }

    @Test
    void parenthesesOverridePrecedence() {
        PhoenixQuestFlags.setFlag("a", true);
        PhoenixQuestFlags.setFlag("b", false);
        PhoenixQuestFlags.setFlag("c", false);
        PhoenixQuestFlags.setFlag("d", false);

        assertTrue(eval("a | b & c & d"));

        assertFalse(eval("(a | b) & c & d"));
    }

    @Test
    void notDistributesOverGroupsAsNandNorXnor() {
        PhoenixQuestFlags.setFlag("a", true);
        PhoenixQuestFlags.setFlag("b", true);

        assertFalse(eval("!(a & b)"));

        assertFalse(eval("!(a | b)"));

        assertTrue(eval("!(a ^ b)"));

        PhoenixQuestFlags.setFlag("b", false);
        assertTrue(eval("!(a & b)"));
        assertFalse(eval("!(a | b)"));
        assertFalse(eval("!(a ^ b)"));
    }

    @Test
    void arbitraryNestingIsSupported() {
        PhoenixQuestFlags.setFlag("a", true);
        PhoenixQuestFlags.setFlag("b", false);
        PhoenixQuestFlags.setFlag("c", true);
        PhoenixQuestFlags.setFlag("d", false);

        assertFalse(eval("!((a ^ b) & (c | d))"));
    }

    @Test
    void malformedExpressionDefaultsToTrue() {
        assertTrue(eval("(a"));
        assertTrue(eval("a &"));
        assertTrue(eval("a | | b"));
    }
}
