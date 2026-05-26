package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractPipeExpressionTests;

public class PipeExpressionTest extends AbstractPipeExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void pipeToFunctionNoArgsValue() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn double(x) { return eval($x * 2); }
                5 |> double();
                """)));
    }

    @Test
    void pipeToStoredLambda() {
        assertEquals(15.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var triple : fn(x) { return eval($x * 3); };
                5 |> triple();
                """)));
    }

    @Test
    void pipeWithComplexLeftSide() {
        assertEquals(5.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn id(x) { return $x; }
                eval(2 + 3) |> id();
                """)));
    }
}
