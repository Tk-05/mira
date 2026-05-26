package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractModuloExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void basicModulo() {
        assertEquals("1", runForOutput("print(eval(7 % 3));"));
    }

    @Test
    void exactModuloIsZero() {
        assertEquals("0", runForOutput("print(eval(6 % 3));"));
    }

    @Test
    void moduloWithVariable() {
        assertEquals("2", runForOutput("var x : 8; print(eval($x % 3));"));
    }

    @Test
    void moduloCompoundAssign() {
        assertEquals("2", runForOutput("var x : 8; $x %= 3; print($x);"));
    }
}
