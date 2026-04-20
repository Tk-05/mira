package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.InterpreterTestBase;

public class FloorDivExpressionTest extends InterpreterTestBase {

    @Test
    void basicFloorDiv() {
        assertEquals(3.0, run("eval(7 \\% 2);"));
    }

    @Test
    void exactDivision() {
        assertEquals(4.0, run("eval(8 \\% 2);"));
    }

    @Test
    void floorRoundsDown() {
        assertEquals(2.0, run("eval(9 \\% 4);"));
    }

    @Test
    void floatFloorDiv() {
        assertEquals(2.0, run("eval(7.5 \\% 3.0);"));
    }

    @Test
    void floorDivPrecedenceSameAsMul() {
        // 10 \% 3 + 1 = 3 + 1 = 4  (not 10 \% 4)
        assertEquals(4.0, run("eval(10 \\% 3 + 1);"));
    }

    @Test
    void floorDivWithVariable() {
        assertEquals(3.0, run("""
                var a : 10;
                var b : 3;
                eval($a \\% $b);
                """));
    }

    @Test
    void floorDivAssignment() {
        assertEquals(3.0, run("""
                var x : 10;
                $x \\%= 3;
                eval($x);
                """));
    }

    @Test
    void largeFloorDiv() {
        assertEquals(100.0, run("eval(1000 \\% 10);"));
    }
}
