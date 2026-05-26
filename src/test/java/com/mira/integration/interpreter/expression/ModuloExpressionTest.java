package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.Evaluator;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractModuloExpressionTests;

public class ModuloExpressionTest extends AbstractModuloExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void moduloViaEvaluator() {
        assertEquals(1.0, ((Number) Evaluator.evaluate("10 % 3", false)).doubleValue(), 0.0001);
        assertEquals(0.0, ((Number) Evaluator.evaluate("8 % 4", false)).doubleValue(), 0.0001);
    }
}
