package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractReturnTests {

    protected abstract String runForOutput(String source);

    @Test
    void returnInsideFunctionDoesNotPropagate() {
        assertEquals("42", runForOutput("fn f() { return 42; } print(f());"));
    }

    @Test
    void returnInsideFunctionWithString() {
        assertEquals("hello", runForOutput("fn f() { return \"hello\"; } print(f());"));
    }

    @Test
    void returnWithZero() {
        assertEquals("0", runForOutput("fn f() { return 0; } print(f());"));
    }
}
