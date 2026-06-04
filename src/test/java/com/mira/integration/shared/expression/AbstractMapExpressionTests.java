package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractMapExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void mapAccessString() {
        assertEquals("hello", runForOutput("var m : {\"key\" : \"hello\"}; print($m[\"key\"]);"));
    }

    @Test
    void mapAccessNumber() {
        assertEquals("42", runForOutput("var m : {\"n\" : 42}; print($m[\"n\"]);"));
    }

    @Test
    void mapAssignment() {
        assertEquals("99", runForOutput("var m : {\"x\" : 1}; $m[\"x\"] : 99; print($m[\"x\"]);"));
    }

    @Test
    void mapAssignNewKey() {
        assertEquals("new", runForOutput("var m : {\"a\" : 1}; $m[\"b\"] : \"new\"; print($m[\"b\"]);"));
    }

    @Test
    void mapPassedToFunction() {
        assertEquals("5", runForOutput("""
                fn getVal(m) { return $m["x"]; }
                var map : {"x" : 5};
                print(getVal($map));
                """));
    }
}
