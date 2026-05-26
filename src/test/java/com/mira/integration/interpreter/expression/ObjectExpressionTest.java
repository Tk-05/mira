package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractObjectExpressionTests;

public class ObjectExpressionTest extends AbstractObjectExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void objectWithUninitializedField() {
        assertNull(backend.runAndGetValue("""
                var obj : {
                    var test : 0;
                    var test2;
                };
                $obj.test2;
                """));
    }

    @Test
    void objectDeclarationReturnsNull() {
        assertNull(backend.runAndGetValue("""
                var obj : {
                    var x : 0;
                };
                """));
    }
}
