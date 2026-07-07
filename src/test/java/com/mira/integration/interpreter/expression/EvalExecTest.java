package com.mira.integration.interpreter.expression;

import org.junit.jupiter.api.BeforeEach;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractEvalExecTests;

public class EvalExecTest extends AbstractEvalExecTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }
}
