package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractNullTests {

    protected abstract String runForOutput(String source);

    @Test
    void nullToStringIsNull() {
        assertEquals("null", runForOutput("print(null);"));
    }

    @Test
    void nullEqualsNull() {
        assertEquals("true", runForOutput("print(eval(null == null));"));
    }

    @Test
    void nullNotEqualsValue() {
        assertEquals("false", runForOutput("print(eval(null == 1));"));
    }

    @Test
    void nullIsFalsyInIf() {
        assertEquals("falsy", runForOutput("if(null) { print(\"truthy\"); } else { print(\"falsy\"); }"));
    }

    @Test
    void nonNullIsNotNull() {
        assertEquals("false", runForOutput("var x : 1; print(eval($x == null));"));
    }
}
