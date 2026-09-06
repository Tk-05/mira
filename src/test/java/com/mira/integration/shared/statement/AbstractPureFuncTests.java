package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractPureFuncTests {

    protected abstract String runForOutput(String source);

    @Test
    void pureFunctionReturnsCorrectResult() {
        assertEquals("25", runForOutput("pure fn square(n) { return (n * n); } print(square(5));"));
    }

    @Test
    void pureFunctionWithMultipleParams() {
        assertEquals("7", runForOutput("pure fn add(a, b) { return (a + b); } print(add(3, 4));"));
    }

    @Test
    void pureFunctionSameResultOnRepeatedCalls() {
        assertEquals("25", runForOutput("""
                pure fn square(n) { return (n * n); }
                print(square(5));
                """));
    }

    @Test
    void pureRecursiveFibonacci() {
        assertEquals("55", runForOutput("""
                pure fn fib(n) {
                    if(n <= 1) { return n; }
                    return (fib(eval(n-1)) + fib(eval(n-2)));
                }
                print(fib(10));
                """));
    }
}
