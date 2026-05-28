package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractFloorDivExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void basicFloorDiv() {
        assertEquals("3", runForOutput("print(eval(7 \\% 2));"));
    }

    @Test
    void exactDivision() {
        assertEquals("4", runForOutput("print(eval(8 \\% 2));"));
    }

    @Test
    void floorRoundsDown() {
        assertEquals("2", runForOutput("print(eval(5 \\% 2));"));
    }

    @Test
    void floorDivWithVariable() {
        assertEquals("3", runForOutput("var x : 10; print(eval($x \\% 3));"));
    }

    @Test
    void floorDivAssignment() {
        assertEquals("3", runForOutput("var x : 10; $x \\%: 3; print($x);"));
    }
}
