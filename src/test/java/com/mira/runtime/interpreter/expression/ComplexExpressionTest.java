package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.Evaluator;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class ComplexExpressionTest extends InterpreterTestBase {

    // --- Arithmetic ---

    @Test
    void addition() {
        assertEquals(3.0, run("eval(1+2);"));
    }

    @Test
    void subtraction() {
        assertEquals(2.0, run("eval(5-3);"));
    }

    @Test
    void multiplication() {
        assertEquals(6.0, run("eval(2*3);"));
    }

    @Test
    void division() {
        assertEquals(4.0, run("eval(8/2);"));
    }

    @Test
    void parentheses() {
        assertEquals(9.0, (double) Evaluator.evaluate("(1+2)*3", false), 0.0001);
        assertEquals(-20.0, (double) Evaluator.evaluate("-(2+3)*4", false), 0.0001);
        assertEquals(20.0, (double) Evaluator.evaluate("((1+2)+(3+4))*2", false), 0.0001);
    }

    @Test
    void nestedParentheses() {
        assertEquals(20.0, (double) Evaluator.evaluate("((1+2)+(3+4))*2", false), 0.0001);
        assertEquals(10.0, (double) Evaluator.evaluate("(1+(2+(3+4)))", false), 0.0001);
    }

    @Test
    void decimalNumbers() {
        assertEquals(3.8, (double) Evaluator.evaluate("1.5 + 2.3", false), 0.0001);
        assertEquals(2.5, (double) Evaluator.evaluate("10.0 / 4.0", false), 0.0001);
        assertEquals(-4.0, (double) Evaluator.evaluate("-0.5 * 8", false), 0.0001);
    }

    @Test
    void complexExpressionWithVariables() {
        assertEquals(0.5, run("""
                var a : 5;
                var b : 3;
                eval((-$a + ($b*2)) / 2);
                """));
    }

    // --- Conditions ---

    @Test
    void greaterThan() {
        assertTrue((boolean) Evaluator.evaluate("1 > 0", false));
        assertFalse((boolean) Evaluator.evaluate("0 > 1", false));
    }

    @Test
    void lessThan() {
        assertFalse((boolean) Evaluator.evaluate("1 < 0", false));
        assertTrue((boolean) Evaluator.evaluate("0 < 1", false));
    }

    @Test
    void greaterThanOrEqual() {
        assertTrue((boolean) Evaluator.evaluate("5 >= 5", false));
        assertTrue((boolean) Evaluator.evaluate("6 >= 5", false));
        assertFalse((boolean) Evaluator.evaluate("4 >= 5", false));
    }

    @Test
    void equality() {
        assertTrue((boolean) Evaluator.evaluate("5 == 5", false));
        assertFalse((boolean) Evaluator.evaluate("5 == 6", false));
    }

    @Test
    void inequality() {
        assertTrue((boolean) Evaluator.evaluate("5 != 6", false));
        assertFalse((boolean) Evaluator.evaluate("5 != 5", false));
    }

    @Test
    void logicalAnd() {
        assertTrue((boolean) Evaluator.evaluate("(5 > 3 && 10 > 5)", false));
        assertFalse((boolean) Evaluator.evaluate("5 > 3 && 10 < 5", false));
    }

    @Test
    void logicalOr() {
        assertTrue((boolean) Evaluator.evaluate("5 > 3 || 10 < 5", false));
        assertFalse((boolean) Evaluator.evaluate("5 < 3 || 10 < 5", false));
    }

    @Test
    void logicalNot() {
        assertFalse((boolean) Evaluator.evaluate("!(5 > 3)", false));
        assertTrue((boolean) Evaluator.evaluate("!(5 < 3)", false));
    }

    @Test
    void complexCondition() {
        assertTrue((boolean) Evaluator.evaluate("((5 > 3 && 10 > 5) || (3 == 4)) && !(2 > 10)", false));
    }

    @Test
    void chainedEqualityIsNotTransitive() {
        assertFalse((boolean) Evaluator.evaluate("1 == 1 == 1", false));
    }
}
