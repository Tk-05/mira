package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractAssignTests {

    protected abstract String runForOutput(String source);

    @Test
    void simpleAssignment() {
        assertEquals("10", runForOutput("var x : 0; x : 10; print(x);"));
    }

    @Test
    void reassignVariable() {
        assertEquals("20", runForOutput("var x : 10; x : 20; print(x);"));
    }

    @Test
    void assignArithmeticExpression() {
        assertEquals("15", runForOutput("var x : 0; x : (5 + 10); print(x);"));
    }

    @Test
    void chainedAssignments() {
        assertEquals("5", runForOutput("var x : 1; var y : 2; x : 3; y : 5; print(y);"));
    }

    @Test
    void assignListIndex() {
        assertEquals("99", runForOutput("var list : {1, 2, 3}; list[1] : 99; print(list[1]);"));
    }
}
