package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractCallExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void callFunctionWithReturn() {
        assertEquals("42", runForOutput("fn f() { return 42; } print(f());"));
    }

    @Test
    void callFunctionWithArguments() {
        assertEquals("5", runForOutput("fn add(a, b) { return eval($a + $b); } print(add(2, 3));"));
    }

    @Test
    void callBuiltinPrint() {
        assertEquals("hello", runForOutput("print(\"hello\");"));
    }

    @Test
    void callFunctionResultUsedInExpression() {
        assertEquals("14", runForOutput("fn double(n) { return eval($n * 2); } print(eval(double(5) + 4));"));
    }
}
