package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractCompoundAssignTests {

    protected abstract String runForOutput(String source);

    @Test
    void addAssign() {
        assertEquals("15", runForOutput("var x : 10; $x +: 5; print($x);"));
    }

    @Test
    void subtractAssign() {
        assertEquals("5", runForOutput("var x : 10; $x -: 5; print($x);"));
    }

    @Test
    void multiplyAssign() {
        assertEquals("20", runForOutput("var x : 4; $x *: 5; print($x);"));
    }

    @Test
    void divideAssign() {
        assertEquals("2.5", runForOutput("var x : 5; $x /: 2; print($x);"));
    }

    @Test
    void compoundAssignInLoop() {
        assertEquals("10", runForOutput("""
                var sum : 0;
                for(var i : 1; $i <= 4; $i : eval($i + 1)) {
                    $sum +: $i;
                }
                print($sum);
                """));
    }
}
