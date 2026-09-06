package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractBitwiseExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void bitwiseAnd() {
        assertEquals("1", runForOutput("print((3 & 1));"));
    }

    @Test
    void bitwiseOr() {
        assertEquals("7", runForOutput("print((5 | 3));"));
    }

    @Test
    void bitwiseXor() {
        assertEquals("6", runForOutput("print((5 ^ 3));"));
    }

    @Test
    void shiftLeft() {
        assertEquals("8", runForOutput("print((1 << 3));"));
    }

    @Test
    void shiftRight() {
        assertEquals("2", runForOutput("print((8 >> 2));"));
    }

    @Test
    void bitwiseAndCompoundAssign() {
        assertEquals("1", runForOutput("var x : 3; x &: 1; print(x);"));
    }

    @Test
    void bitwiseOrCompoundAssign() {
        assertEquals("7", runForOutput("var x : 5; x |: 3; print(x);"));
    }
}
