package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractSpawnTests;
import com.mira.runtime.functions.Promise;

public class SpawnTest extends AbstractSpawnTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void spawnReturnsPromise() {
        Object result = backend.runAndGetValue("spawn(fn() { return 1; });");
        assertInstanceOf(Promise.class, result);
    }

    @Test
    void awaitSpawnedNumber() {
        assertEquals(42.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "(await spawn(fn() { return 42; }));")));
    }

    @Test
    void awaitSpawnedString() {
        assertEquals("hello", backend.runAndGetValue(
                "await spawn(fn() { return \"hello\"; });"));
    }

    @Test
    void awaitSpawnedNull() {
        assertEquals(null, backend.runAndGetValue("await spawn(fn() {});"));
    }

    @Test
    void parallelSpawnBothResolve() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var h1 : spawn(fn() { return 1; });
                var h2 : spawn(fn() { return 2; });
                (await(h1) + await(h2));
                """)));
    }

    @Test
    void spawnWithCapture() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var x : 10;
                (await spawn(fn() { return x; }));
                """)));
    }

    @Test
    void spawnErrorPropagatesViaAwait() {
        Object result = backend.runAndGetValue("""
                var caught : "none";
                try {
                    await spawn(fn() { throw oob("fail"); });
                } catch(oob) {
                    caught : "caught";
                }
                caught;
                """);
        assertEquals("caught", result);
    }

    @Test
    void awaitOnNonPromisePassesThrough() {
        assertEquals(7.0, InterpreterRunner.normNum(backend.runAndGetValue("(await 7);")));
    }
}
