package com.mira.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InterpreterTest {

    Interpreter interpreter;

    @BeforeEach
    void setup() {
        interpreter = new Interpreter();
    }

    @Test
    void testSimpleArithmetic() {
        double result = Evaluator.evaluate("1+2");
        assertEquals(3.0, result);

        result = Evaluator.evaluate("5-3");
        assertEquals(2.0, result);

        result = Evaluator.evaluate("2*3");
        assertEquals(6.0, result);

        result = Evaluator.evaluate("8/2");
        assertEquals(4.0, result);
    }

    @Test
    void testUnaryOperators() {
        double result = Evaluator.evaluate("-5");
        assertEquals(-5.0, result);

        result = Evaluator.evaluate("+7");
        assertEquals(7.0, result);

        result = Evaluator.evaluate("-1+2");
        assertEquals(1.0, result);

        result = Evaluator.evaluate("-1*3");
        assertEquals(-3.0, result);
    }

    @Test
    void testParentheses() {
        double result = Evaluator.evaluate("(1+2)*3");
        assertEquals(9.0, result);

        result = Evaluator.evaluate("-(2+3)*4");
        assertEquals(-20.0, result);

        result = Evaluator.evaluate("((1+2)+(3+4))*2");
        assertEquals(20.0, result);
    }

    @Test
    void testVariableUsage() {
        // Set variables in global environment
        Interpreter.getGlobalEnvironment().define("x", 10);
        Interpreter.getGlobalEnvironment().define("y", 5);
        Interpreter.getGlobalEnvironment().define("$val", 3);

        double result = Evaluator.evaluate("x+y");
        assertEquals(15.0, result);

        result = Evaluator.evaluate("$val+2");
        assertEquals(5.0, result);

        result = Evaluator.evaluate("$val+ x * 2");
        assertEquals(23.0, result);
    }

    @Test
    void testComplexExpressionWithUnaryAndParentheses() {
        Interpreter.getGlobalEnvironment().define("$a", 5);
        Interpreter.getGlobalEnvironment().define("$b", 3);

        double result = Evaluator.evaluate("(-$a + ($b*2)) / 2");
        // (-5 + (3*2))/2 = (-5+6)/2 = 1/2 = 0.5
        assertEquals(0.5, result);
    }

    @Test
    void testDecimalNumbers() {
        double result = Evaluator.evaluate("1.5 + 2.3");
        assertEquals(3.8, result, 0.0001);

        result = Evaluator.evaluate("10.0 / 4.0");
        assertEquals(2.5, result, 0.0001);

        result = Evaluator.evaluate("-0.5 * 8");
        assertEquals(-4.0, result, 0.0001);
    }

    @Test
    void testNestedParentheses() {
        double result = Evaluator.evaluate("((1+2)+(3+4))*2");
        assertEquals(20.0, result);

        result = Evaluator.evaluate("(1+(2+(3+4)))");
        assertEquals(10.0, result);
    }

    @Test
    void testErrors() {
        assertThrows(RuntimeException.class, () -> Evaluator.evaluate("1++2"));
        assertThrows(RuntimeException.class, () -> Evaluator.evaluate("((1+2)"));
        assertThrows(RuntimeException.class, () -> Evaluator.evaluate("unknownVar+1"));
    }
}
