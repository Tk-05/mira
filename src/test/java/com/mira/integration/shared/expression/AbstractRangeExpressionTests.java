package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractRangeExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void rangeIteratesCorrectly() {
        assertEquals("10", runForOutput("""
                var sum : 0;
                for(var i in <0..5>) { $sum : eval($sum + $i); }
                print($sum);
                """));
    }

    @Test
    void rangeCountElements() {
        assertEquals("5", runForOutput("""
                var count : 0;
                for(var i in <0..5>) { $count : eval($count + 1); }
                print($count);
                """));
    }

    @Test
    void rangeAsGeneralExpression() {
        assertEquals("{0, 1, 2, 3, 4}", runForOutput("""
                var r : <0..5>;
                print($r);
                """));
    }

    @Test
    void rangeAsGeneralExpressionPassedDirectly() {
        assertEquals("{0, 1, 2}", runForOutput("print(<0..3>);"));
    }

    @Test
    void rangeOperandsRespectPrecedence() {
        assertEquals("{0, 1, 2, 3, 4, 5}", runForOutput("""
                var r : <1-1..2*3>;
                print($r);
                """));
    }
}
