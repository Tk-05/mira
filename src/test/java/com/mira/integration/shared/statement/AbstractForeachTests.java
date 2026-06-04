package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractForeachTests {

    protected abstract String runForOutput(String source);

    @Test
    void foreachOnList() {
        assertEquals("6", runForOutput("""
                var sum : 0;
                foreach(var i in {1, 2, 3}) {
                    $sum : eval($sum + $i);
                }
                print($sum);
                """));
    }

    @Test
    void foreachOnString() {
        assertEquals("3", runForOutput("""
                var count : 0;
                foreach(var c in "abc") {
                    $count : eval($count + 1);
                }
                print($count);
                """));
    }

    @Test
    void foreachWithCounter() {
        assertEquals("10", runForOutput("""
                var total : 0;
                foreach(var i in <0..5>) {
                    $total : eval($total + $i);
                }
                print($total);
                """));
    }

    @Test
    void foreachIteratesAllElements() {
        assertEquals("4", runForOutput("""
                var last : 0;
                foreach(var n in {10, 20, 30, 40}) {
                    $last : eval($last + 1);
                }
                print($last);
                """));
    }
}
