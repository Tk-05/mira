package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractLambdaExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void lambdaWithNoParams() {
        assertEquals("42", runForOutput("var f : fn() { return 42; }; print(f());"));
    }

    @Test
    void lambdaWithOneParam() {
        assertEquals("6", runForOutput("var f : fn(n) { return eval($n * 2); }; print(f(3));"));
    }

    @Test
    void lambdaWithTwoParams() {
        assertEquals("7", runForOutput("var add : fn(a, b) { return eval($a + $b); }; print(add(3, 4));"));
    }

    @Test
    void lambdaPassedAsArgument() {
        assertEquals("10", runForOutput("""
                fn apply(f, x) { return $f($x); }
                var double : fn(n) { return eval($n * 2); };
                print(apply($double, 5));
                """));
    }

    @Test
    void lambdaCapturesOuterVariable() {
        assertEquals("15", runForOutput("""
                var base : 10;
                var addBase : fn(n) { return eval($n + $base); };
                print(addBase(5));
                """));
    }

    @Test
    void lambdaCalledMultipleTimes() {
        assertEquals("6", runForOutput("""
                var inc : fn(n) { return eval($n + 1); };
                var x : 3;
                $x : eval(inc($x));
                $x : eval(inc($x));
                $x : eval(inc($x));
                print($x);
                """));
    }
}
