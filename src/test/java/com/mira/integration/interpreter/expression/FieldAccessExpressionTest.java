package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractFieldAccessExpressionTests;

public class FieldAccessExpressionTest extends AbstractFieldAccessExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void accessUninitializedField() {
        assertNull(backend.runAndGetValue("""
                var obj : { var x; };
                obj.x;
                """));
    }

    @Test
    void accessStringField() {
        org.junit.jupiter.api.Assertions.assertEquals("hello", backend.runAndGetValue("""
                var obj : { var name : "hello"; };
                obj.name;
                """));
    }
}
