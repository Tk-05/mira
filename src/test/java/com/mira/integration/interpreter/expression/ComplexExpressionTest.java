package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.Evaluator;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractComplexExpressionTests;

public class ComplexExpressionTest extends AbstractComplexExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    private static double evalD(String expr) {
        return ((Number) Evaluator.evaluate(expr, false)).doubleValue();
    }

    @Test
    void parentheses() {
        assertEquals(9.0, evalD("(1+2)*3"), 0.0001);
        assertEquals(-20.0, evalD("-(2+3)*4"), 0.0001);
        assertEquals(20.0, evalD("((1+2)+(3+4))*2"), 0.0001);
    }

    @Test
    void decimalNumbers() {
        assertEquals(3.8, evalD("1.5 + 2.3"), 0.0001);
        assertEquals(2.5, evalD("10.0 / 4.0"), 0.0001);
    }

    @Test
    void greaterThan() {
        assertTrue((boolean) Evaluator.evaluate("1 > 0", false));
        assertFalse((boolean) Evaluator.evaluate("0 > 1", false));
    }

    @Test
    void equality() {
        assertTrue((boolean) Evaluator.evaluate("5 == 5", false));
        assertFalse((boolean) Evaluator.evaluate("5 == 6", false));
    }

    @Test
    void logicalAnd() {
        assertTrue((boolean) Evaluator.evaluate("(5 > 3 && 10 > 5)", false));
        assertFalse((boolean) Evaluator.evaluate("5 > 3 && 10 < 5", false));
    }

    @Test
    void logicalOr() {
        assertTrue((boolean) Evaluator.evaluate("5 > 3 || 10 < 5", false));
    }

    @Test
    void logicalNot() {
        assertFalse((boolean) Evaluator.evaluate("!(5 > 3)", false));
        assertTrue((boolean) Evaluator.evaluate("!(5 < 3)", false));
    }

    @Test
    void chainedEqualityIsNotTransitive() {
        assertFalse((boolean) Evaluator.evaluate("1 == 1 == 1", false));
    }
}
