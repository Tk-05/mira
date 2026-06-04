package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractNullCoalescingTests {

    protected abstract String runForOutput(String source);

    @Test
    void returnsRightWhenLeftIsNull() {
        assertEquals("default", runForOutput("var x; print($x ?? \"default\");"));
    }

    @Test
    void returnsLeftWhenLeftIsNotNull() {
        assertEquals("value", runForOutput("var x : \"value\"; print($x ?? \"default\");"));
    }

    @Test
    void returnsLeftWhenLeftIsZero() {
        assertEquals("0", runForOutput("var x : 0; print($x ?? \"default\");"));
    }

    @Test
    void returnsLeftWhenLeftIsFalse() {
        assertEquals("false", runForOutput("var x : false; print($x ?? \"default\");"));
    }

    @Test
    void returnsLeftWhenLeftIsEmptyString() {
        assertEquals("", runForOutput("var x : \"\"; print($x ?? \"default\");"));
    }
}
