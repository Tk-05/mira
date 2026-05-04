package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class CompoundAssignTest extends InterpreterTestBase {

    @Test
    void addAssign() {
        assertEquals(15.0, run("var x : 10; $x += 5; eval($x);"));
    }

    @Test
    void subtractAssign() {
        assertEquals(7.0, run("var x : 10; $x -= 3; eval($x);"));
    }

    @Test
    void multiplyAssign() {
        assertEquals(30.0, run("var x : 6; $x *= 5; eval($x);"));
    }

    @Test
    void divideAssign() {
        assertEquals(4.0, run("var x : 20; $x /= 5; eval($x);"));
    }

    @Test
    void addAssignWithVariable() {
        assertEquals(25.0, run("var x : 10; var y : 15; $x += $y; eval($x);"));
    }

    @Test
    void compoundAssignInLoop() {
        assertEquals(10.0, run("""
                var sum : 0;
                for (var i : 1; $i <= 4; $i += 1) {
                    $sum += $i;
                }
                eval($sum);
                """));
    }

    @Test
    void chainedCompoundAssigns() {
        assertEquals(24.0, run("var x : 10; $x += 5; $x -= 3; $x *= 2; eval($x);"));
    }

    @Test
    void divideAssignResultIsDecimal() {
        assertEquals(3.5, run("var x : 7; $x /= 2; eval($x);"));
    }

    @Test
    void addAssignOnFieldAccess() {
        assertEquals(15.0, run("""
                var obj : { var n : 10; };
                $obj.n += 5;
                eval($obj.n);
                """));
    }

    @Test
    void multiplyAssignOnFieldAccess() {
        assertEquals(6.0, run("""
                var obj : { var n : 2; };
                $obj.n *= 3;
                eval($obj.n);
                """));
    }
}
