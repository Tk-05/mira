package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractAsyncAwaitTests;
import com.mira.runtime.functions.Promise;

public class AsyncAwaitTest extends AbstractAsyncAwaitTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void asyncFunctionReturnsPromise() {
        Object result = backend.runAndGetValue("async fn task() { return 1; } task();");
        assertInstanceOf(Promise.class, result);
    }

    @Test
    void awaitResolvesReturnValue() {
        assertEquals(42.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "async fn task() { return 42; } (await task());")));
    }

    @Test
    void awaitWithParameter() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "async fn double(n) { return (n * 2); } (await double(5));")));
    }

    @Test
    void awaitVoidAsyncReturnsNull() {
        assertNull(backend.runAndGetValue("async fn task() {} await task();"));
    }

    @Test
    void awaitOnNonPromisePassesThrough() {
        assertEquals(7.0, InterpreterRunner.normNum(backend.runAndGetValue("(await 7);")));
    }

    @Test
    void awaitOnSyncFunctionPassesThrough() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "fn add(a, b) { return (a + b); } (await add(1, 2));")));
    }

    @Test
    void asyncLambdaReturnsPromise() {
        Object result = backend.runAndGetValue("var task : async fn() { return 99; }; task();");
        assertInstanceOf(Promise.class, result);
    }

    @Test
    void awaitAsyncLambda() {
        assertEquals(99.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var task : async fn() { return 99; }; (await task());")));
    }

    @Test
    void parallelAsyncCallsAllResolve() {
        assertEquals(6.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                async fn inc(n) { return (n + 1); }
                var p1 : inc(0);
                var p2 : inc(1);
                var p3 : inc(2);
                (eval(await p1 + await p2) + await p3);
                """)));
    }

    @Test
    void asyncCapturesClosureVariable() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var factor : 2;
                async fn scale(n) { return (n * factor); }
                (await scale(5));
                """)));
    }

    @Test
    void awaitErrorPropagatesToCatch() {
        assertEquals("boom", backend.runAndGetValue("""
                async fn failing() { throw error("boom"); }
                var result : "ok";
                try {
                    await failing();
                } catch(error) {
                    result : error;
                }
                result;
                """));
    }

    @Test
    void multipleAwaitsSequential() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                async fn one() { return 1; }
                async fn two() { return 2; }
                (eval(await one() + await two()));
                """)));
    }
}
