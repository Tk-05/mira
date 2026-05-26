package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractRangeExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void rangeIteratesCorrectly() {
        assertEquals("10", runForOutput("""
                var sum : 0;
                foreach(var i in <0..5>) { $sum : eval($sum + $i); }
                print($sum);
                """));
    }

    @Test
    void rangeCountElements() {
        assertEquals("5", runForOutput("""
                var count : 0;
                foreach(var i in <0..5>) { $count : eval($count + 1); }
                print($count);
                """));
    }
}
