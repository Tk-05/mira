package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractAccessExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void simpleIndexAccess() {
        assertEquals("2", runForOutput("var list : {1, 2, 3}; print($list[1]);"));
    }

    @Test
    void zeroIndexAccess() {
        assertEquals("1", runForOutput("var list : {1, 2, 3}; print($list[0]);"));
    }

    @Test
    void nestedAccess() {
        assertEquals("4", runForOutput("var list : {{1, 2}, {3, 4}}; print($list[1][1]);"));
    }

    @Test
    void accessOnFunctionResult() {
        assertEquals("1", runForOutput("""
                fn getList() { var list : {1, 2, 3}; return $list; }
                print(getList()[0]);
                """));
    }
}
