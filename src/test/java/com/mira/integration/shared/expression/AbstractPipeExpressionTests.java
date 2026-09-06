package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractPipeExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void pipeToFunctionNoArgs() {
        assertEquals("10", runForOutput("fn double(x) { return (x * 2); } print((5 |> double()));"));
    }

    @Test
    void pipeToFunctionWithExtraArg() {
        assertEquals("5", runForOutput("fn add(a, b) { return (a + b); } print((2 |> add(3)));"));
    }

    @Test
    void chainedPipe() {
        assertEquals("8", runForOutput("""
                fn double(x) { return (x * 2); }
                print((2 |> double() |> double()));
                """));
    }
}
