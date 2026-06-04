package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractComplexExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void addition() {
        assertEquals("5", runForOutput("print(eval(2 + 3));"));
    }

    @Test
    void subtraction() {
        assertEquals("3", runForOutput("print(eval(7 - 4));"));
    }

    @Test
    void multiplication() {
        assertEquals("12", runForOutput("print(eval(3 * 4));"));
    }

    @Test
    void division() {
        assertEquals("2.5", runForOutput("print(eval(5 / 2));"));
    }

    @Test
    void complexExpressionWithVariables() {
        assertEquals("14", runForOutput("var a : 2; var b : 3; print(eval($a * $b + $b * $b - 1));"));
    }

    @Test
    void operatorPrecedence() {
        assertEquals("7", runForOutput("print(eval(1 + 2 * 3));"));
    }

    @Test
    void stringNumberJuxtaposition() {
        assertEquals("53", runForOutput("print(\"5\" 3);"));
    }

    @Test
    void numberStringJuxtaposition() {
        assertEquals("35", runForOutput("print(3 \"5\");"));
    }

    @Test
    void numberNumberJuxtaposition() {
        assertEquals("53", runForOutput("print(5 3);"));
    }

    @Test
    void multiplePartsJuxtaposition() {
        assertEquals("Hello World", runForOutput("print(\"Hello\" \" \" \"World\");"));
    }

    @Test
    void variableStringJuxtaposition() {
        assertEquals("Hello World", runForOutput("var x : \"Hello\"; print($x \" World\");"));
    }
}
