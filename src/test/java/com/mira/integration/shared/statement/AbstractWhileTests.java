package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractWhileTests {

    protected abstract String runForOutput(String source);

    @Test
    void whileCountsCorrectly() {
        assertEquals("5", runForOutput("""
                var i : 0;
                while($i < 5){ $i : eval($i + 1); }
                print($i);
                """));
    }

    @Test
    void whileFalseNeverExecutes() {
        assertEquals("false", runForOutput("""
                var executed : false;
                while(0){ $executed : true; }
                print($executed);
                """));
    }

    @Test
    void whileWithBreakChecksValue() {
        assertEquals("3", runForOutput("""
                var x : 0;
                while(1) {
                    $x : eval($x + 1);
                    if($x >= 3) { break; }
                }
                print($x);
                """));
    }

    @Test
    void nestedWhile() {
        assertEquals("9", runForOutput("""
                var outer : 0;
                var inner : 0;
                var total : 0;
                while($outer < 3) {
                    $outer : eval($outer + 1);
                    $inner : 0;
                    while($inner < 3) {
                        $inner : eval($inner + 1);
                        $total : eval($total + 1);
                    }
                }
                print($total);
                """));
    }

    @Test
    void doWhileExecutesAtLeastOnce() {
        assertEquals("true", runForOutput("""
                var executed : false;
                do { $executed : true; } while(0);
                print($executed);
                """));
    }

    @Test
    void doWhileCountsCorrectly() {
        assertEquals("5", runForOutput("""
                var i : 0;
                do { $i : eval($i + 1); } while($i < 5);
                print($i);
                """));
    }
}
