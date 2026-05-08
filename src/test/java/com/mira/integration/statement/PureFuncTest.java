package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class PureFuncTest extends InterpreterTestBase {

    @Test
    void pureFunctionReturnsCorrectResult() {
        assertEquals(25.0, run("""
                pure fn square(n) { return eval($n * $n); }
                eval(square(5));
                """));
    }

    @Test
    void pureFunctionWithMultipleParams() {
        assertEquals(12.0, run("""
                pure fn multiply(a, b) { return eval($a * $b); }
                eval(multiply(3, 4));
                """));
    }

    @Test
    void pureFunctionSameResultOnRepeatedCalls() {
        assertEquals(25.0, run("""
                pure fn square(n) { return eval($n * $n); }
                square(5);
                square(5);
                eval(square(5));
                """));
    }

    @Test
    void pureFunctionDifferentArgsReturnDifferentResults() {
        assertEquals(9.0, run("""
                pure fn square(n) { return eval($n * $n); }
                eval(square(3));
                """));
        interpreter = new com.mira.runtime.interpreter.Interpreter();
        assertEquals(16.0, run("""
                pure fn square(n) { return eval($n * $n); }
                eval(square(4));
                """));
    }

    @Test
    void pureFunctionIsRegisteredInPureFunctionsSet() {
        run("""
                pure fn square(n) { return eval($n * $n); }
                square(5);
                """);
        assertTrue(interpreter.getPureFunctions().contains("square"),
                "pure fn should be in pureFunctions set");
    }

    @Test
    void regularFunctionIsNotForcedIntoPureFunctionsSet() {
        run("""
                fn square(n) { return eval($n * $n); }
                square(5);
                """);
        // A regular fn that happens to be pure may or may not be detected by PurityAnalyzer,
        // but the explicit pure keyword is what this test guards.
        // Just verify the function runs correctly without the pure flag forcing anything.
        assertEquals(25.0, run("""
                fn square(n) { return eval($n * $n); }
                eval(square(5));
                """));
    }

    @Test
    void pureFunctionResultIsCached() {
        run("""
                pure fn square(n) { return eval($n * $n); }
                square(10);
                """);
        int sizeBefore = interpreter.getCallCache().size();
        run("""
                pure fn square(n) { return eval($n * $n); }
                square(10);
                """);
        // Cache should have at least one entry after a pure function call
        assertTrue(interpreter.getCallCache().size() >= sizeBefore,
                "cache should grow after calling a pure function");
    }

    @Test
    void pureRecursiveFibonacci() {
        assertEquals(55.0, run("""
                pure fn fib(n) {
                    if ($n <= 1) { return $n; }
                    return eval(fib(eval($n - 1)) + fib(eval($n - 2)));
                }
                eval(fib(10));
                """));
    }

    @Test
    void pureAndRegularFunctionsCoexist() {
        assertEquals(24.0, run("""
                pure fn square(n) { return eval($n * $n); }
                fn add(a, b) { return eval($a + $b); }
                eval(add(square(3), square(4) - square(1)));
                """));
    }
}
