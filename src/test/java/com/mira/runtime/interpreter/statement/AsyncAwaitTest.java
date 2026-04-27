package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.Promise;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class AsyncAwaitTest extends InterpreterTestBase {

    @Test
    void asyncFunctionReturnsPromise() {
        Object result = run("async fn task() { return 1; } task();");
        assertInstanceOf(Promise.class, result);
    }

    @Test
    void awaitResolvesReturnValue() {
        assertEquals(42.0, run("async fn task() { return 42; } eval(await task());"));
    }

    @Test
    void awaitWithParameter() {
        assertEquals(10.0, run("async fn double(n) { return eval($n * 2); } eval(await double(5));"));
    }

    @Test
    void awaitVoidAsyncReturnsNull() {
        assertNull(run("async fn task() {} await task();"));
    }

    @Test
    void awaitOnNonPromisePassesThrough() {
        assertEquals(7.0, run("eval(await 7);"));
    }

    @Test
    void awaitOnSyncFunctionPassesThrough() {
        assertEquals(3.0, run("fn add(a, b) { return eval($a + $b); } eval(await add(1, 2));"));
    }

    @Test
    void asyncLambdaReturnsPromise() {
        Object result = run("var task : async fn() { return 99; }; task();");
        assertInstanceOf(Promise.class, result);
    }

    @Test
    void awaitAsyncLambda() {
        assertEquals(99.0, run("var task : async fn() { return 99; }; eval(await task());"));
    }

    @Test
    void awaitAsyncLambdaWithParam() {
        assertEquals(15.0, run("var mul : async fn(a, b) { return eval($a * $b); }; eval(await mul(3, 5));"));
    }

    @Test
    void parallelAsyncCallsAllResolve() {
        assertEquals(6.0, run("""
                async fn inc(n) { return eval($n + 1); }
                var p1 : inc(0);
                var p2 : inc(1);
                var p3 : inc(2);
                eval(eval(await $p1 + await $p2) + await $p3);
                """));
    }

    @Test
    void asyncCapturesClosureVariable() {
        assertEquals(10.0, run("""
                var factor : 2;
                async fn scale(n) { return eval($n * $factor); }
                eval(await scale(5));
                """));
    }

    @Test
    void awaitErrorPropagatesToCatch() {
        assertEquals("boom", run("""
                async fn failing() { throw error("boom"); }
                var result : "ok";
                try {
                    await failing();
                } catch(error) {
                    $result : $error;
                }
                $result;
                """));
    }

    @Test
    void awaitInsideTryCatchReceivesValue() {
        assertEquals(5.0, run("""
                async fn safe() { return 5; }
                var result : 0;
                try {
                    $result : await safe();
                } catch(e) {}
                eval($result);
                """));
    }

    @Test
    void multipleAwaitsSequential() {
        assertEquals(3.0, run("""
                async fn one() { return 1; }
                async fn two() { return 2; }
                eval(eval(await one() + await two()));
                """));
    }
}
