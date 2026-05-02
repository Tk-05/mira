package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class NullCoalescingTest extends InterpreterTestBase {

    @Test
    void returnsRightWhenLeftIsNull() {
        assertEquals("default", run("var x : null; $x ?? \"default\";"));
    }

    @Test
    void returnsLeftWhenLeftIsNotNull() {
        assertEquals("value", run("var x : \"value\"; $x ?? \"default\";"));
    }

    @Test
    void returnsLeftWhenLeftIsZero() {
        assertEquals(0.0, run("var x : 0; $x ?? 99;"));
    }

    @Test
    void returnsLeftWhenLeftIsFalse() {
        assertEquals(Boolean.FALSE, run("var x : false; $x ?? true;"));
    }

    @Test
    void returnsLeftWhenLeftIsEmptyString() {
        assertEquals("", run("var x : \"\"; $x ?? \"fallback\";"));
    }

    @Test
    void rightSideIsNumber() {
        assertEquals(42.0, run("var x : null; $x ?? 42;"));
    }

    @Test
    void rightSideIsBoolean() {
        assertEquals(Boolean.TRUE, run("var x : null; $x ?? true;"));
    }

    @Test
    void chainsLeftToRight() {
        run("""
                var a : null;
                var b : null;
                var c : "found";
                var result : $a ?? $b ?? $c;
                """);
        assertEquals("found", interpreter.getGlobalEnvironment().get("result"));
    }

    @Test
    void chainStopsAtFirstNonNull() {
        run("""
                var a : null;
                var b : "b";
                var c : "c";
                var result : $a ?? $b ?? $c;
                """);
        assertEquals("b", interpreter.getGlobalEnvironment().get("result"));
    }

    @Test
    void worksWithUninitializedVariable() {
        run("""
                var x;
                var result : $x ?? "fallback";
                """);
        assertEquals("fallback", interpreter.getGlobalEnvironment().get("result"));
    }

    @Test
    void rightSideNotEvaluatedWhenLeftIsNonNull() {
        run("""
                var count : 0;
                fn sideEffect() {
                    $count : eval($count + 1);
                    return "side";
                }
                var x : "left";
                var result : $x ?? sideEffect();
                """);
        assertEquals("left", interpreter.getGlobalEnvironment().get("result"));
        assertEquals(0.0, normNum(interpreter.getGlobalEnvironment().get("count")));
    }

    @Test
    void rightSideEvaluatedWhenLeftIsNull() {
        run("""
                var count : 0;
                fn sideEffect() {
                    $count : eval($count + 1);
                    return "side";
                }
                var x : null;
                var result : $x ?? sideEffect();
                """);
        assertEquals("side", interpreter.getGlobalEnvironment().get("result"));
        assertEquals(1.0, normNum(interpreter.getGlobalEnvironment().get("count")));
    }
}
