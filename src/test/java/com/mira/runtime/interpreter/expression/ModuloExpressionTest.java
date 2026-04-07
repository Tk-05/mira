package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.Evaluator;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class ModuloExpressionTest extends InterpreterTestBase {

    @Test
    void modulo() {
        assertEquals(1.0, (double) Evaluator.evaluate("10 % 3", false), 0.0001);
    }

    @Test
    void moduloEven() {
        assertEquals(0.0, (double) Evaluator.evaluate("8 % 4", false), 0.0001);
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
