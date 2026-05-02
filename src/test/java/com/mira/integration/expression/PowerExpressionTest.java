package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class PowerExpressionTest extends InterpreterTestBase {

    @Test
    void basicPower() {
        assertEquals(100.0, run("eval(10 ** 2);"));
    }

    @Test
    void powerOfZero() {
        assertEquals(1.0, run("eval(5 ** 0);"));
    }

    @Test
    void powerOfOne() {
        assertEquals(7.0, run("eval(7 ** 1);"));
    }

    @Test
    void squareRoot() {
        assertEquals(3.0, run("eval(9 ** 0.5);"));
    }

    @Test
    void powerPrecedenceHigherThanMultiply() {
        assertEquals(18.0, run("eval(2 * 3 ** 2);"));
    }

    @Test
    void powerPrecedenceHigherThanAdd() {
        assertEquals(9.0, run("eval(1 + 2 ** 3);"));
    }

    @Test
    void powerWithVariable() {
        assertEquals(8.0, run("""
                var x : 2;
                var n : 3;
                eval($x ** $n);
                """));
    }

    @Test
    void powerAssignment() {
        assertEquals(8.0, run("""
                var x : 2;
                $x **= 3;
                eval($x);
                """));
    }

    @Test
    void negativePower() {
        assertEquals(0.25, run("eval(2 ** -2);"));
    }

    @Test
    void powerOfLargeNumber() {
        assertEquals(1000000.0, run("eval(10 ** 6);"));
    }
}
