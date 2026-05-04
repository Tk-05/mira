package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class ArrowLambdaTest extends InterpreterTestBase {

    @Test
    void noParams() {
        assertEquals(42.0, run("var f : () -> 42; f();"));
    }

    @Test
    void singleParam() {
        assertEquals(6.0, run("var f : (x) -> eval($x * 2); f(3);"));
    }

    @Test
    void multiParams() {
        assertEquals(7.0, run("var add : (a, b) -> eval($a + $b); add(3, 4);"));
    }

    @Test
    void blockBody() {
        assertEquals(5.0, run("var f : (x) -> { return eval($x + 1); }; f(4);"));
    }

    @Test
    void asArgument() {
        assertEquals(10.0, run("fn apply(f, x) { return $f($x); } eval(apply((x) -> eval($x * 2), 5));"));
    }

    @Test
    void defaultParam() {
        assertEquals(5.0, run("var f : (x : 5) -> $x; f();"));
    }

    @Test
    void stringResult() {
        assertEquals("hello", run("var f : (s) -> $s; f(\"hello\");"));
    }

    @Test
    void closure() {
        assertEquals(8.0, run("var base : 3; var f : (x) -> eval($x + $base); f(5);"));
    }
}
