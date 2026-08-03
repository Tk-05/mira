package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractArrayExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void arrayWithLiterals() {
        assertEquals("1", runForOutput("var arr : [1, 2, 3]; print($arr[0]);"));
    }

    @Test
    void arrayIsMutable() {
        assertEquals("99", runForOutput("var arr : [1, 2, 3]; $arr[1] : 99; print($arr[1]);"));
    }

    @Test
    void nestedArrays() {
        assertEquals("4", runForOutput("var arr : [[1, 2], [3, 4]]; print($arr[1][1]);"));
    }

    @Test
    void arrayInForeach() {
        assertEquals("6", runForOutput("""
                var sum : 0;
                for(var n in [1, 2, 3]) { $sum : eval($sum + $n); }
                print($sum);
                """));
    }
}
