package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractOptionalChainingTests {

    protected abstract String runForOutput(String source);

    @Test
    void returnsNullWhenObjectIsNull() {
        assertEquals("null", runForOutput("var x; print($x?.field ?? \"null\");"));
    }

    @Test
    void accessesFieldWhenObjectIsNotNull() {
        assertEquals("42", runForOutput("var obj : { var x : 42; }; print($obj?.x);"));
    }

    @Test
    void combinesWithNullCoalescing() {
        assertEquals("default", runForOutput("var x; print($x?.field ?? \"default\");"));
    }
}
