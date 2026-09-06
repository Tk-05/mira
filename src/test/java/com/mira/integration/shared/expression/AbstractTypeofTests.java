package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractTypeofTests {

    protected abstract String runForOutput(String source);

    @Test
    void number() {
        assertEquals("number", runForOutput("print(typeof(42));"));
    }

    @Test
    void string() {
        assertEquals("string", runForOutput("print(typeof(\"hello\"));"));
    }

    @Test
    void boolTrue() {
        assertEquals("bool", runForOutput("print(typeof(true));"));
    }

    @Test
    void nullVal() {
        assertEquals("null", runForOutput("print(typeof(null));"));
    }

    @Test
    void list() {
        assertEquals("list", runForOutput("print(typeof({1, 2, 3}));"));
    }

    @Test
    void usedInCondition() {
        assertEquals("yes", runForOutput("var x : 42; print((typeof(x) == \"number\" ? \"yes\" : \"no\"));"));
    }
}
