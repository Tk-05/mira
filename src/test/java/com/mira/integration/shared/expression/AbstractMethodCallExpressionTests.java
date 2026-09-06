package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractMethodCallExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void simpleMethodCall() {
        assertEquals("42", runForOutput("""
                var obj : { fn getVal() { return 42; } };
                print(obj.getVal());
                """));
    }

    @Test
    void methodWithParameters() {
        assertEquals("7", runForOutput("""
                var calc : { fn add(a, b) { return (a + b); } };
                print(calc.add(3, 4));
                """));
    }

    @Test
    void methodMutatesField() {
        assertEquals("99", runForOutput("""
                var obj : { var x : 0; fn setX(v) { x : v; } };
                obj.setX(99);
                print(obj.x);
                """));
    }
}
