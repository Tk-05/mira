package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.Evaluator;
import com.mira.integration.InterpreterTestBase;

public class ModuloExpressionTest extends InterpreterTestBase {

    private static double eval(String expr, boolean ignoreSequences) {
        return ((Number) Evaluator.evaluate(expr, ignoreSequences)).doubleValue();
    }

    @Test
    void modulo() {
        assertEquals(1.0, eval("10 % 3", false), 0.0001);
    }

    @Test
    void moduloEven() {
        assertEquals(0.0, eval("8 % 4", false), 0.0001);
    }

    @Test
    void moduloWithVariable() {
        assertEquals(2.0, run("var x : 17; eval($x % 5);"));
    }

    @Test
    void moduloCompoundAssign() {
        assertEquals(1.0, run("var x : 10; $x %= 3; eval($x);"));
    }
}
