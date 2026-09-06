package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractArrowLambdaTests;

public class ArrowLambdaTest extends AbstractArrowLambdaTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void asArgument() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "fn apply(f, x) { return f(x); } (apply((x) -> eval(x * 2), 5));")));
    }

    @Test
    void defaultParam() {
        assertEquals(5.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var f : (x : 5) -> x; f();")));
    }

    @Test
    void stringResult() {
        assertEquals("hello", backend.runAndGetValue("var f : (s) -> s; f(\"hello\");"));
    }

    @Test
    void closure() {
        assertEquals(8.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var base : 3; var f : (x) -> (x + base); f(5);")));
    }
}
