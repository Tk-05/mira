package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.Evaluator;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class BitwiseExpressionTest extends InterpreterTestBase {

    private static double eval(String expr, boolean ignoreSequences) {
        return ((Number) Evaluator.evaluate(expr, ignoreSequences)).doubleValue();
    }

    @Test
    void bitwiseAnd() {
        assertEquals(4.0, eval("12 & 6", false), 0.0001);
    }

    @Test
    void bitwiseAndZero() {
        assertEquals(0.0, eval("5 & 0", false), 0.0001);
    }

    @Test
    void bitwiseAndCompoundAssign() {
        assertEquals(4.0, run("var x : 12; $x &= 6; eval($x);"));
    }

    @Test
    void bitwiseOr() {
        assertEquals(14.0, eval("12 | 6", false), 0.0001);
    }

    @Test
    void bitwiseOrIdentity() {
        assertEquals(7.0, eval("5 | 3", false), 0.0001);
    }

    @Test
    void bitwiseOrCompoundAssign() {
        assertEquals(14.0, run("var x : 12; $x |= 6; eval($x);"));
    }

    @Test
    void bitwiseXor() {
        assertEquals(10.0, eval("12 ^ 6", false), 0.0001);
    }

    @Test
    void bitwiseXorSelf() {
        assertEquals(0.0, eval("7 ^ 7", false), 0.0001);
    }

    @Test
    void bitwiseXorCompoundAssign() {
        assertEquals(10.0, run("var x : 12; $x ^= 6; eval($x);"));
    }

    @Test
    void bitwiseNot() {
        assertEquals((double) ~5L, eval("~5", false), 0.0001);
    }

    @Test
    void bitwiseNotZero() {
        assertEquals((double) ~0L, eval("~0", false), 0.0001);
    }

    @Test
    void shiftLeft() {
        assertEquals(8.0, eval("1 << 3", false), 0.0001);
    }

    @Test
    void shiftRight() {
        assertEquals(2.0, eval("16 >> 3", false), 0.0001);
    }

    @Test
    void shiftLeftWithVariable() {
        assertEquals(16.0, run("var x : 2; eval($x << 3);"));
    }

    @Test
    void shiftRightWithVariable() {
        assertEquals(4.0, run("var x : 32; eval($x >> 3);"));
    }

    @Test
    void andBeforeOr() {
        // 1 | (2 & 3)  =>  1 | 2  =>  3
        assertEquals(3.0, eval("1 | 2 & 3", false), 0.0001);
    }

    @Test
    void addBindsTighterThanShift() {
        // 1 << (2 + 1)  =>  1 << 3  =>  8
        assertEquals(8.0, eval("1 << 2 + 1", false), 0.0001);
    }
}
