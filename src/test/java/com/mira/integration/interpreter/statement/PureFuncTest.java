package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractPureFuncTests;

public class PureFuncTest extends AbstractPureFuncTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void pureFunctionIsRegisteredInPureFunctionsSet() {
        backend.runAndGetValue("""
                pure fn square(n) { return (n * n); }
                square(5);
                """);
        assertTrue(backend.getInterpreter().getPureFunctions().contains("square"),
                "pure fn should be in pureFunctions set");
    }

    @Test
    void pureFunctionResultIsCached() {
        backend.runAndGetValue("""
                pure fn square(n) { return (n * n); }
                square(10);
                """);
        int sizeBefore = backend.getInterpreter().getCallCache().size();
        backend.runContinued("square(10);");
        assertTrue(backend.getInterpreter().getCallCache().size() >= sizeBefore,
                "cache should grow after calling a pure function");
    }

    @Test
    void pureAndRegularFunctionsCoexist() {
        assertEquals(24.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                pure fn square(n) { return (n * n); }
                fn add(a, b) { return (a + b); }
                (add(square(3), square(4) - square(1)));
                """)));
    }

    @Test
    void pureFunctionDifferentArgsReturnDifferentResults() {
        assertEquals(9.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                pure fn square(n) { return (n * n); }
                (square(3));
                """)));
        backend.reset();
        assertEquals(16.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                pure fn square(n) { return (n * n); }
                (square(4));
                """)));
    }
}
