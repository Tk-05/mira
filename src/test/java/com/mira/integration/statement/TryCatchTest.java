package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.functions.ThrowSignal;
import com.mira.integration.InterpreterTestBase;

public class TryCatchTest extends InterpreterTestBase {

    @Test
    void throwAtTopLevelPropagates() {
        ThrowSignal signal = assertThrows(ThrowSignal.class, () -> run("throw error(\"error\");"));
        assertEquals("error", signal.getValue());
    }

    @Test
    void catchReceivesStringValue() {
        try {
            run("""
                    try {
                        throw error("something went wrong");
                    } catch (error) {
                        return $error;
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals("something went wrong", r.getValue());
        }
    }

    @Test
    void catchReceivesNumberValue() {
        ThrowSignal signal = assertThrows(ThrowSignal.class, () -> run("throw error(42);"));
        assertEquals(42.0, normNum(signal.getValue()));
    }

    @Test
    void catchReceivesBooleanValue() {
        ThrowSignal signal = assertThrows(ThrowSignal.class, () -> run("throw error(true);"));
        assertEquals(Boolean.TRUE, signal.getValue());
    }

    @Test
    void noThrowSkipsCatchBlock() {
        run("""
                var x : 1;
                try {
                    $x : eval($x + 1);
                } catch (e) {
                    $x : eval($x + 100);
                }
                """);
        assertEquals(2.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }

    @Test
    void executionContinuesAfterTryCatch() {
        run("""
                var x : 0;
                try {
                    throw error("err");
                } catch (error) {
                    $x : 1;
                }
                $x : eval($x + 10);
                """);
        assertEquals(11.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }

    @Test
    void catchBindsVariableCorrectly() {
        try {
            run("""
                    try {
                        throw error("hello");
                    } catch (error) {
                        return $error;
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals("hello", r.getValue());
        }
    }

    @Test
    void nestedTryCatchInnerCatches() {
        run("""
                var x : 0;
                try {
                    try {
                        throw error("inner");
                    } catch (error) {
                        $x : 1;
                    }
                } catch (e) {
                    $x : 2;
                }
                """);
        assertEquals(1.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }

    @Test
    void nestedTryCatchOuterCatchesWhenInnerRethrows() {
        run("""
                var x : 0;
                try {
                    try {
                        throw error("rethrow");
                    } catch (error) {
                        throw error($error);
                    }
                } catch (error) {
                    $x : 99;
                }
                """);
        assertEquals(99.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }

    @Test
    void throwInsideFunction() {
        try {
            run("""
                    fn risky() {
                        throw risky("from function");
                    }
                    try {
                        risky();
                    } catch (risky) {
                        return $risky;
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals("from function", r.getValue());
        }
    }

    @Test
    void throwPropagatesThroughLoop() {
        run("""
                var x : 0;
                try {
                    while (true) {
                        $x : eval($x + 1);
                        throw error("stop");
                    }
                } catch (error) {
                    $x : eval($x + 10);
                }
                """);
        assertEquals(11.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }

    @Test
    void tryBodyScopeIsIsolated() {
        assertNull(run("""
                try {
                    var inner : 5;
                } catch (e) {
                }
                """));
    }

    @Test
    void catchVariableIsScopedToBlock() {
        assertThrows(RuntimeException.class, () -> run("""
                try {
                    throw "scoped";
                } catch (e) {
                }
                var result : $e;
                """));
    }

    @Test
    void finallyRunsWhenNoThrow() {
        run("""
                var x : 0;
                try {
                    $x : 1;
                } catch (e) {
                    $x : 99;
                } finally {
                    $x : eval($x + 10);
                }
                """);
        assertEquals(11.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }

    @Test
    void finallyRunsAfterCatch() {
        run("""
                var x : 0;
                try {
                    throw error("err");
                } catch (error) {
                    $x : 1;
                } finally {
                    $x : eval($x + 10);
                }
                """);
        assertEquals(11.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }

    @Test
    void finallyRunsEvenWhenThrowNotCaught() {
        run("""
                var x : 0;
                try {
                    try {
                        throw error("err");
                    } catch (error) {
                    } finally {
                        $x : 42;
                    }
                } catch (error) {
                }
                """);
        assertEquals(42.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }

    @Test
    void finallyWithoutThrowDoesNotRunCatch() {
        run("""
                var x : 0;
                try {
                    $x : 1;
                } catch (e) {
                    $x : 99;
                } finally {
                    $x : eval($x + 5);
                }
                """);
        assertEquals(6.0, normNum(interpreter.getGlobalEnvironment().get("x")));
    }
}
