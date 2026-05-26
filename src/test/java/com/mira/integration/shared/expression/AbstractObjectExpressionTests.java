package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractObjectExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void objectWithSingleField() {
        assertEquals("42", runForOutput("var obj : { var x : 42; }; print($obj.x);"));
    }

    @Test
    void objectWithMultipleFields() {
        assertEquals("3", runForOutput("var obj : { var a : 1; var b : 2; }; print(eval($obj.a + $obj.b));"));
    }

    @Test
    void objectFieldArithmetic() {
        assertEquals("10", runForOutput("var obj : { var x : 5; }; print(eval($obj.x * 2));"));
    }

    @Test
    void objectFieldCanBeReassigned() {
        assertEquals("99", runForOutput("var obj : { var x : 1; }; $obj.x : 99; print($obj.x);"));
    }
}
