package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.PostUnaryError;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class UnaryExpressionTest extends InterpreterTestBase {

    @Test
    void prefixNegation() {
        assertEquals(-5.0, run("eval(-5);"));
        assertEquals(1.0, run("eval(-1+2);"));
        assertEquals(-3.0, run("eval(-1*3);"));
    }

    @Test
    void referenceAccess() {
        try {
            run("var x : \"hello\"; return $x;");
        } catch (ReturnSignal r) {
            assertEquals("hello", r.getValue());
        }
    }

    @Test
    void doubleReference() {
        try {
            run("""
                    var x : 1;
                    var y : x;
                    var z : y;

                    print($$$z);
                    """);
        } catch (ReturnSignal r) {
            assertEquals("1", r.getValue());
        }
    }

    @Test
    void referenceInArithmetic() {
        assertEquals(15.0, run("var x : 10; var y : 5; eval($x + $y);"));
    }

    @Test
    void postIncrement() {
        assertEquals(11.0, run("var x : 10; $x++; eval($x);"));
    }

    @Test
    void postDecrement() {
        assertEquals(9.0, run("var x : 10; $x--; eval($x);"));
    }

    @Test
    void postIncrementAndDecrement() {
        assertEquals(10.0, run("var test : 10; $test++; $test--;"));
    }

    @Test
    void postUnaryInExpression() {
        assertEquals(12.0, run("""
                var test : 10;
                $test : $test+++1;
                eval($test);
                """));
    }

    @Test
    void multiplePostUnaryInExpression() {
        assertEquals(20.0, run("""
                var test : 5;
                $test : $test+++$test+++$test;
                eval($test);
                """));
    }

    @Test
    void nestedPostUnary() {
        assertEquals(3.0, run("""
                var test : 0;
                $test : (($test++) + 1) + $test;
                eval($test);
                """));
    }

    @Test
    void invalidPostUnaryThrows() {
        assertThrows(PostUnaryError.class, () -> run("eval(1++2);"));
    }

    @Test
    void booleanNegationTrue() {
        try {
            run("return !true;");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.FALSE, r.getValue());
        }
    }

    @Test
    void booleanNegationFalse() {
        try {
            run("return !false;");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void booleanNegationVariable() {
        try {
            run("var x : true; return !$x;");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.FALSE, r.getValue());
        }
    }
}
