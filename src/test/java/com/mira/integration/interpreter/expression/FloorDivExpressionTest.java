package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractFloorDivExpressionTests;

public class FloorDivExpressionTest extends AbstractFloorDivExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void floatFloorDiv() {
        assertEquals(2.0, InterpreterRunner.normNum(backend.runAndGetValue("eval(7.5 \\% 3.0);")));
    }

    @Test
    void largeFloorDiv() {
        assertEquals(100.0, InterpreterRunner.normNum(backend.runAndGetValue("eval(1000 \\% 10);")));
    }
}
