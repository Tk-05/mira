package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractPowerExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void basicPower() {
        assertEquals("8.0", runForOutput("print(eval(2 ** 3));"));
    }

    @Test
    void powerOfZero() {
        assertEquals("1.0", runForOutput("print(eval(5 ** 0));"));
    }

    @Test
    void powerOfOne() {
        assertEquals("7.0", runForOutput("print(eval(7 ** 1));"));
    }

    @Test
    void powerWithVariable() {
        assertEquals("9.0", runForOutput("var x : 3; print(eval($x ** 2));"));
    }

    @Test
    void powerAssignment() {
        assertEquals("8.0", runForOutput("var x : 2; $x **: 3; print($x);"));
    }
}
