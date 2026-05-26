package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractArrowLambdaTests {

    protected abstract String runForOutput(String source);

    @Test
    void noParams() {
        assertEquals("42", runForOutput("var f : () -> 42; print(f());"));
    }

    @Test
    void singleParam() {
        assertEquals("6", runForOutput("var f : (x) -> eval($x * 2); print(f(3));"));
    }

    @Test
    void multiParams() {
        assertEquals("7", runForOutput("var add : (a, b) -> eval($a + $b); print(add(3, 4));"));
    }

    @Test
    void blockBody() {
        assertEquals("5", runForOutput("var f : (x) -> { return eval($x + 1); }; print(f(4));"));
    }
}
