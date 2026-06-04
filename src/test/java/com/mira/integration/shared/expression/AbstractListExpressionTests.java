package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractListExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void listCreationAndAccess() {
        assertEquals("2", runForOutput("var list : {1, 2, 3}; print($list[1]);"));
    }

    @Test
    void listAssignment() {
        assertEquals("99", runForOutput("var list : {1, 2, 3}; $list[0] : 99; print($list[0]);"));
    }

    @Test
    void listWithExpressions() {
        assertEquals("6", runForOutput("var x : 2; var list : {$x, eval($x * 2), eval($x * 3)}; print($list[2]);"));
    }

    @Test
    void listForeach() {
        assertEquals("6", runForOutput("""
                var sum : 0;
                foreach(var n in {1, 2, 3}) { $sum : eval($sum + $n); }
                print($sum);
                """));
    }

    @Test
    void listIsMutable() {
        assertEquals("5", runForOutput("var list : {1, 2, 3}; $list[1] : 5; print($list[1]);"));
    }
}
