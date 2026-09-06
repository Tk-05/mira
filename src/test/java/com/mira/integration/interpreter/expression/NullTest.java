package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractNullTests;
import com.mira.runtime.values.NullValue;

public class NullTest extends AbstractNullTests {

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
    void nullLiteralReturnsNullValue() {
        assertInstanceOf(NullValue.class, backend.runAndGetValue("null;"));
    }

    @Test
    void uninitializedVarIsNull() {
        assertInstanceOf(NullValue.class, backend.runAndGetValue("var x; x;"));
    }

    @Test
    void explicitNullAssignment() {
        assertInstanceOf(NullValue.class, backend.runAndGetValue("var x : null; x;"));
    }

    @Test
    void nullEqualsNullBoolean() {
        assertEquals(Boolean.TRUE, backend.runAndGetValue("null == null;"));
    }

    @Test
    void nullNotEqualsString() {
        assertEquals(Boolean.FALSE, backend.runAndGetValue("null == \"hello\";"));
    }

    @Test
    void uninitializedVarEqualsNull() {
        assertEquals(Boolean.TRUE, backend.runAndGetValue("var x; x == null;"));
    }

    @Test
    void assignNullToVar() {
        assertEquals(Boolean.TRUE, backend.runAndGetValue("var x : 10; x : null; x == null;"));
    }

    @Test
    void nullIsFalsyInWhile() {
        assertEquals(0.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var count : 0;
                var cond : null;
                while (cond) {
                    count +: 1;
                }
                (count);
                """)));
    }

    @Test
    void reassignFromNullToValue() {
        assertEquals(42.0, InterpreterRunner.normNum(backend.runAndGetValue("var x : null; x : 42; (x);")));
    }
}
