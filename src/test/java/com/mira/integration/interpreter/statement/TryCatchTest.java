package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.functions.ThrowSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractTryCatchTests;

public class TryCatchTest extends AbstractTryCatchTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void throwAtTopLevelPropagates() {
        ThrowSignal signal = assertThrows(ThrowSignal.class, () -> backend.runAndGetValue("throw error(\"error\");"));
        assertEquals("error", signal.getValue());
    }

    @Test
    void catchReceivesStringValue() {
        try {
            backend.runAndGetValue("""
                    try {
                        throw error("something went wrong");
                    } catch (error) {
                        return error;
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals("something went wrong", r.getValue());
        }
    }

    @Test
    void catchReceivesNumberValue() {
        ThrowSignal signal = assertThrows(ThrowSignal.class, () -> backend.runAndGetValue("throw error(42);"));
        assertEquals(42.0, InterpreterRunner.normNum(signal.getValue()));
    }

    @Test
    void catchReceivesBooleanValue() {
        ThrowSignal signal = assertThrows(ThrowSignal.class, () -> backend.runAndGetValue("throw error(true);"));
        assertEquals(Boolean.TRUE, signal.getValue());
    }

    @Test
    void noThrowSkipsCatchBlockValue() {
        backend.runAndGetValue("""
                var x : 1;
                try {
                    x : (x + 1);
                } catch (e) {
                    x : (x + 100);
                }
                """);
        assertEquals(2.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("x")));
    }

    @Test
    void nestedTryCatchInnerCatches() {
        backend.runAndGetValue("""
                var x : 0;
                try {
                    try {
                        throw error("inner");
                    } catch (error) {
                        x : 1;
                    }
                } catch (e) {
                    x : 2;
                }
                """);
        assertEquals(1.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("x")));
    }

    @Test
    void nestedTryCatchOuterCatchesWhenInnerRethrows() {
        backend.runAndGetValue("""
                var x : 0;
                try {
                    try {
                        throw error("rethrow");
                    } catch (error) {
                        throw error(error);
                    }
                } catch (error) {
                    x : 99;
                }
                """);
        assertEquals(99.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("x")));
    }

    @Test
    void throwPropagatesThroughLoop() {
        backend.runAndGetValue("""
                var x : 0;
                try {
                    while (true) {
                        x : (x + 1);
                        throw error("stop");
                    }
                } catch (error) {
                    x : (x + 10);
                }
                """);
        assertEquals(11.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("x")));
    }

    @Test
    void tryBodyScopeIsIsolated() {
        assertNull(backend.runAndGetValue("""
                try {
                    var inner : 5;
                } catch (e) {
                }
                """));
    }

    @Test
    void catchVariableIsScopedToBlock() {
        assertThrows(RuntimeException.class, () -> backend.runAndGetValue("""
                try {
                    throw "scoped";
                } catch (e) {
                }
                var result : e;
                """));
    }

    @Test
    void finallyRunsEvenWhenThrowNotCaught() {
        backend.runAndGetValue("""
                var x : 0;
                try {
                    try {
                        throw error("err");
                    } catch (error) {
                    } finally {
                        x : 42;
                    }
                } catch (error) {
                }
                """);
        assertEquals(42.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("x")));
    }

    @Test
    void finallyWithoutThrowDoesNotRunCatch() {
        backend.runAndGetValue("""
                var x : 0;
                try {
                    x : 1;
                } catch (e) {
                    x : 99;
                } finally {
                    x : (x + 5);
                }
                """);
        assertEquals(6.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("x")));
    }
}
