package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractPowerExpressionTests;

public class PowerExpressionTest extends AbstractPowerExpressionTests {

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
    void squareRoot() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("eval(9 ** 0.5);")));
    }

    @Test
    void powerPrecedenceHigherThanMultiply() {
        assertEquals(18.0, InterpreterRunner.normNum(backend.runAndGetValue("eval(2 * 3 ** 2);")));
    }

    @Test
    void powerPrecedenceHigherThanAdd() {
        assertEquals(9.0, InterpreterRunner.normNum(backend.runAndGetValue("eval(1 + 2 ** 3);")));
    }

    @Test
    void negativePower() {
        assertEquals(0.25, InterpreterRunner.normNum(backend.runAndGetValue("eval(2 ** -2);")));
    }

    @Test
    void powerOfLargeNumber() {
        assertEquals(1000000.0, InterpreterRunner.normNum(backend.runAndGetValue("eval(10 ** 6);")));
    }
}
