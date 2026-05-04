package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.Function;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterTestBase;

public class LambdaExpressionTest extends InterpreterTestBase {

    @Test
    void lambdaAssignedToVariable() {
        try {
            run("var f : fn(x) { return $x; }; return $f;");
        } catch (ReturnSignal r) {
            assertInstanceOf(Function.class, r.getValue());
        }
    }

    @Test
    void lambdaWithNoParams() {
        assertEquals(42.0, run("var answer : fn() { return 42; }; eval(answer());"));
    }

    @Test
    void lambdaWithOneParam() {
        assertEquals(10.0, run("var double : fn(x) { return eval($x * 2); }; eval(double(5));"));
    }

    @Test
    void lambdaWithTwoParams() {
        assertEquals(7.0, run("var add : fn(a, b) { return eval($a + $b); }; eval(add(3, 4));"));
    }

    @Test
    void lambdaReturnsString() {
        try {
            run("var greet : fn(name) { return \"Hello \" $name; }; return greet(\"World\");");
        } catch (ReturnSignal r) {
            assertEquals("Hello World", r.getValue());
        }
    }

    @Test
    void lambdaVoidReturn() {
        assertNull(run("var f : fn() {}; f();"));
    }

    @Test
    void lambdaPassedAsArgument() {
        assertEquals(24.0, run("""
                fn apply(f, x) { return $f($x); }
                var triple : fn(n) { return eval($n * 3); };
                eval(apply($triple, 8));
                """));
    }

    @Test
    void lambdaInlineAsArgument() {
        assertEquals(9.0, run("""
                fn apply(f, x) { return $f($x); }
                eval(apply(fn(n) { return eval($n * $n); }, 3));
                """));
    }

    @Test
    void higherOrderFunctionWithMultipleParams() {
        assertEquals(12.0, run("""
                fn applyTwo(f, a, b) { return $f($a, $b); }
                var multiply : fn(x, y) { return eval($x * $y); };
                eval(applyTwo($multiply, 3, 4));
                """));
    }

    @Test
    void lambdaCapturesOuterVariable() {
        assertEquals(15.0, run("""
                var factor : 5;
                var scale : fn(x) { return eval($x * $factor); };
                eval(scale(3));
                """));
    }

    @Test
    void constLambda() {
        assertEquals(4.0, run("const square : fn(x) { return eval($x * $x); }; eval(square(2));"));
    }

    @Test
    void lambdaCalledMultipleTimes() {
        assertEquals(9.0, run("""
                var inc : fn(x) { return eval($x + 1); };
                var a : eval(inc(1));
                var b : eval(inc(2));
                var c : eval(inc(3));
                eval($a + $b + $c);
                """));
    }
}
