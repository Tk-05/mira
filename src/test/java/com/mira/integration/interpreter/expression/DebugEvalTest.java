package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractDebugEvalTests;

public class DebugEvalTest extends AbstractDebugEvalTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void evalTopLevelValue() {
        assertEquals(7.0, InterpreterRunner.normNum(backend.runAndGetValue("(3 + 4);")));
    }

    @Test
    void evalInFunctionValue() {
        assertEquals(7.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "fn add(a, b) { return (a + b); } add(3, 4);")));
    }
}
