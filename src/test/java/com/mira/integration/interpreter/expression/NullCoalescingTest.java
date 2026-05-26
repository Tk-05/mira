package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractNullCoalescingTests;

public class NullCoalescingTest extends AbstractNullCoalescingTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void rightSideIsNumber() {
        assertEquals(42.0, InterpreterRunner.normNum(backend.runAndGetValue("var x : null; $x ?? 42;")));
    }

    @Test
    void chainsLeftToRight() {
        backend.runAndGetValue("""
                var a : null;
                var b : null;
                var c : "found";
                var result : $a ?? $b ?? $c;
                """);
        assertEquals("found", backend.getInterpreter().getGlobalEnvironment().get("result"));
    }

    @Test
    void chainStopsAtFirstNonNull() {
        backend.runAndGetValue("""
                var a : null;
                var b : "b";
                var c : "c";
                var result : $a ?? $b ?? $c;
                """);
        assertEquals("b", backend.getInterpreter().getGlobalEnvironment().get("result"));
    }

    @Test
    void rightSideNotEvaluatedWhenLeftIsNonNull() {
        backend.runAndGetValue("""
                var count : 0;
                fn sideEffect() {
                    $count : eval($count + 1);
                    return "side";
                }
                var x : "left";
                var result : $x ?? sideEffect();
                """);
        assertEquals("left", backend.getInterpreter().getGlobalEnvironment().get("result"));
        assertEquals(0.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("count")));
    }

    @Test
    void rightSideEvaluatedWhenLeftIsNull() {
        backend.runAndGetValue("""
                var count : 0;
                fn sideEffect() {
                    $count : eval($count + 1);
                    return "side";
                }
                var x : null;
                var result : $x ?? sideEffect();
                """);
        assertEquals("side", backend.getInterpreter().getGlobalEnvironment().get("result"));
        assertEquals(1.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("count")));
    }
}
