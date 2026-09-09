package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.IndexOutOfBoundsError;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractArrayExpressionTests;

public class ArrayExpressionTest extends AbstractArrayExpressionTests {

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
    void emptyArrayAccessThrows() {
        assertThrows(IndexOutOfBoundsError.class, () -> backend.runAndGetValue("var arr : []; arr[0];"));
    }

    @Test
    void arrayWithExpressions() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("var arr : [1+2, 3*4, 5]; (arr[0]);")));
    }

    @Test
    void arrayFirstElement() {
        assertEquals(1.0, InterpreterRunner.normNum(backend.runAndGetValue("var arr : [1,2,3]; (arr[0]);")));
    }

    @Test
    void arrayLastElement() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("var arr : [1,2,3]; (arr[2]);")));
    }

    @Test
    void arrayMutateMiddleElement() {
        assertEquals(42.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var arr : [10,20,30];
                arr[1] : 42;
                (arr[1]);
                """)));
    }

    @Test
    void arrayMutateLastElement() {
        assertEquals(7.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var arr : [1,2,3];
                arr[2] : 7;
                (arr[2]);
                """)));
    }

    @Test
    void structAssignedIntoArraySurvivesFieldAccess() {
        assertEquals(42.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var arr : [0];
                arr[0] : { var foo : 42; };
                (arr[0].foo);
                """)));
    }
}
