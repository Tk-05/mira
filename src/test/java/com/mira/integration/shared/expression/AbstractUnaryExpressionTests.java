package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractUnaryExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void referenceInArithmetic() {
        assertEquals("15", runForOutput("var x : 10; print(eval($x + 5));"));
    }

    @Test
    void postIncrement() {
        assertEquals("6", runForOutput("var x : 5; $x++; print($x);"));
    }

    @Test
    void postDecrement() {
        assertEquals("4", runForOutput("var x : 5; $x--; print($x);"));
    }

    @Test
    void booleanNegationTrue() {
        assertEquals("false", runForOutput("print(eval(!true));"));
    }

    @Test
    void booleanNegationFalse() {
        assertEquals("true", runForOutput("print(eval(!false));"));
    }
}
