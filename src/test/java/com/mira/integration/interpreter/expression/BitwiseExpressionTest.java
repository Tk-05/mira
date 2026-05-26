package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.Evaluator;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractBitwiseExpressionTests;

public class BitwiseExpressionTest extends AbstractBitwiseExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    private static double eval(String expr) {
        return ((Number) Evaluator.evaluate(expr, false)).doubleValue();
    }

    @Test
    void bitwiseAndZero() {
        assertEquals(0.0, eval("5 & 0"), 0.0001);
    }

    @Test
    void bitwiseOrIdentity() {
        assertEquals(7.0, eval("5 | 3"), 0.0001);
    }

    @Test
    void bitwiseXorSelf() {
        assertEquals(0.0, eval("7 ^ 7"), 0.0001);
    }

    @Test
    void bitwiseNot() {
        assertEquals((double) ~5L, eval("~5"), 0.0001);
    }

    @Test
    void bitwiseNotZero() {
        assertEquals((double) ~0L, eval("~0"), 0.0001);
    }

    @Test
    void shiftLeftWithVariable() {
        assertEquals(16.0, InterpreterRunner.normNum(backend.runAndGetValue("var x : 2; eval($x << 3);")));
    }

    @Test
    void shiftRightWithVariable() {
        assertEquals(4.0, InterpreterRunner.normNum(backend.runAndGetValue("var x : 32; eval($x >> 3);")));
    }

    @Test
    void bitwiseXorCompoundAssign() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("var x : 12; $x ^= 6; eval($x);")));
    }

    @Test
    void andBeforeOr() {
        assertEquals(3.0, eval("1 | 2 & 3"), 0.0001);
    }

    @Test
    void addBindsTighterThanShift() {
        assertEquals(8.0, eval("1 << 2 + 1"), 0.0001);
    }
}
