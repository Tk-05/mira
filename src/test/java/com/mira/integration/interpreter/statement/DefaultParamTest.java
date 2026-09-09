package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractDefaultParamTests;

public class DefaultParamTest extends AbstractDefaultParamTests {

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
    void defaultUsedWhenArgOmitted() {
        try {
            backend.runAndGetValue("""
                    fn greet(name, greeting : "Hello") {
                        return greeting + " " + name;
                    }
                    greet("World");
                    """);
        } catch (ReturnSignal r) {
            assertEquals("Hello World", r.getValue());
        }
    }

    @Test
    void allRequiredArgsStillRequired() {
        assertThrows(RuntimeException.class, () -> backend.runAndGetValue("""
                fn add(a, b : 10) {
                    return (a + b);
                }
                add();
                """));
    }

    @Test
    void defaultIsBoolean() {
        try {
            backend.runAndGetValue("""
                    fn check(x, flag : true) {
                        return flag;
                    }
                    check(0);
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void multipleDefaultParams() {
        try {
            backend.runAndGetValue("""
                    fn box(value, prefix : "[", suffix : "]") {
                        return prefix + value + suffix;
                    }
                    box("hi");
                    """);
        } catch (ReturnSignal r) {
            assertEquals("[hi]", r.getValue());
        }
    }

    @Test
    void multipleDefaultParamsPartialOverride() {
        try {
            backend.runAndGetValue("""
                    fn box(value, prefix : "[", suffix : "]") {
                        return prefix + value + suffix;
                    }
                    box("hi", "<");
                    """);
        } catch (ReturnSignal r) {
            assertEquals("<hi]", r.getValue());
        }
    }

    @Test
    void tooManyArgsThrows() {
        assertThrows(RuntimeException.class, () -> backend.runAndGetValue("""
                fn add(a, b : 10) {
                    return (a + b);
                }
                add(1, 2, 3);
                """));
    }

    @Test
    void defaultExpressionEvaluatedAtCallTime() {
        backend.runAndGetValue("""
                var base : 10;
                fn withBase(x, offset : base) {
                    return (x + offset);
                }
                base : 20;
                var result : withBase(5);
                """);
        assertEquals(25.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("result")));
    }
}
