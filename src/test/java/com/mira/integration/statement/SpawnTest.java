package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;
import com.mira.runtime.functions.Promise;

public class SpawnTest extends InterpreterTestBase {

    @Test
    void spawnReturnsPromise() {
        skipCompilerTest = true;
        Object result = run("spawn(fn() { return 1; });");
        assertInstanceOf(Promise.class, result);
    }

    @Test
    void awaitSpawnedNumber() {
        assertEquals(42.0, run("eval(await spawn(fn() { return 42; }));"));
    }

    @Test
    void awaitSpawnedString() {
        assertEquals("hello", run("await spawn(fn() { return \"hello\"; });"));
    }

    @Test
    void awaitSpawnedNull() {
        assertEquals(null, run("await spawn(fn() {});"));
    }

    @Test
    void parallelSpawnBothResolve() {
        assertEquals(3.0, run("""
                var h1 : spawn(fn() { return 1; });
                var h2 : spawn(fn() { return 2; });
                eval(await($h1) + await($h2));
                """));
    }

    @Test
    void spawnWithCapture() {
        assertEquals(10.0, run("""
                var x : 10;
                eval(await spawn(fn() { return $x; }));
                """));
    }

    @Test
    void spawnErrorPropagatesViaAwait() {
        Object result = run("""
                var caught : "none";
                try {
                    await spawn(fn() { throw oob("fail"); });
                } catch(oob) {
                    $caught : "caught";
                }
                $caught;
                """);
        assertEquals("caught", result);
    }

    @Test
    void awaitOnNonPromisePassesThrough() {
        assertEquals(7.0, run("eval(await 7);"));
    }
}
