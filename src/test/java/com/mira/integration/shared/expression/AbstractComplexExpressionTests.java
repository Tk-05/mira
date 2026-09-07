package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractComplexExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void addition() {
        assertEquals("5", runForOutput("print((2 + 3));"));
    }

    @Test
    void subtraction() {
        assertEquals("3", runForOutput("print((7 - 4));"));
    }

    @Test
    void multiplication() {
        assertEquals("12", runForOutput("print((3 * 4));"));
    }

    @Test
    void division() {
        assertEquals("2.5", runForOutput("print((5 / 2));"));
    }

    @Test
    void complexExpressionWithVariables() {
        assertEquals("14", runForOutput("var a : 2; var b : 3; print((a * b + b * b - 1));"));
    }

    @Test
    void operatorPrecedence() {
        assertEquals("7", runForOutput("print((1 + 2 * 3));"));
    }

    @Test
    void stringNumberConcatenation() {
        // "+" tries numeric addition first, falling back to string concatenation
        // only when a side doesn't parse as a number -- a numeric-looking string
        // like "5" would just add (5 + 3 = 8.0), so use a non-numeric string here.
        assertEquals("x3", runForOutput("print(\"x\" + 3);"));
    }

    @Test
    void numberStringConcatenation() {
        assertEquals("3x", runForOutput("print(3 + \"x\");"));
    }

    @Test
    void adjacentStringLiteralsSplice() {
        assertEquals("Hello World", runForOutput("print(\"Hello\" \" \" \"World\");"));
    }

    @Test
    void multiplePartsConcatenation() {
        assertEquals("Hello World", runForOutput("print(\"Hello\" + \" \" + \"World\");"));
    }

    @Test
    void variableStringConcatenation() {
        assertEquals("Hello World", runForOutput("var x : \"Hello\"; print(x + \" World\");"));
    }

    @Test
    void booleanStringConcatenation() {
        assertEquals("truex", runForOutput("print(true + \"x\");"));
    }

    @Test
    void stringBooleanConcatenation() {
        assertEquals("xfalse", runForOutput("print(\"x\" + false);"));
    }
}
