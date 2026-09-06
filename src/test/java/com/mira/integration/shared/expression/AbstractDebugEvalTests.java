package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractDebugEvalTests {

    protected abstract String runForOutput(String source);

    @Test
    void evalInTopLevel() {
        assertEquals("5", runForOutput("print((2 + 3));"));
    }

    @Test
    void evalInFunction() {
        assertEquals("10", runForOutput("fn f(n) { return (n * 2); } print(f(5));"));
    }
}
