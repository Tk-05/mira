package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractContinueTests {

    protected abstract String runForOutput(String source);

    @Test
    void continueInsideWhile() {
        assertEquals("5", runForOutput("""
                var evens : 0;
                var i : 0;
                while($i < 10) {
                    $i : eval($i + 1);
                    if(eval($i % 2) != 0) { continue; }
                    $evens : eval($evens + 1);
                }
                print($evens);
                """));
    }

    @Test
    void continueInsideFor() {
        assertEquals("20", runForOutput("""
                var sum : 0;
                for(var i : 0; $i < 10; $i : eval($i + 1)) {
                    if(eval($i % 2) != 0) { continue; }
                    $sum : eval($sum + $i);
                }
                print($sum);
                """));
    }

    @Test
    void continueInsideForeachRange() {
        assertEquals("20", runForOutput("""
                var sum : 0;
                foreach(var i in <0..10>) {
                    if(eval($i % 2) != 0) { continue; }
                    $sum : eval($sum + $i);
                }
                print($sum);
                """));
    }
}
