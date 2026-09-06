package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.IndexOutOfBoundsError;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractListExpressionTests;

public class ListExpressionTest extends AbstractListExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void emptyList() {
        assertNull(backend.runAndGetValue("var list : {};"));
    }

    @Test
    void listAccessLastElement() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var list : {1, 2, 3}; (list[2]);")));
    }

    @Test
    void nestedListAccess() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var list : {{1, 2}, {3, 4}}; (list[1][0]);")));
    }

    @Test
    void nestedListAssignment() {
        assertEquals(99.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var list : {{1, 2}, {3, 4}}; list[1][1] : 99; (list[1][1]);")));
    }

    @Test
    void nestedListAssignmentInArray() {
        assertEquals(99.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var list : [{1, 2}, {3, 4}]; list[1][1] : 99; (list[1][1]);")));
    }

    @Test
    void expressionAsIndex() {
        assertEquals(30.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var list : {10, 20, 30}; (list[eval(1+1)]);")));
    }

    @Test
    void indexOutOfBounds() {
        assertThrows(IndexOutOfBoundsError.class, () -> backend.runAndGetValue("var list : {1, 2, 3}; list[5];"));
    }

    @Test
    void implicitListAccessFromFunction() {
        assertEquals(1.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn getList() {
                    var list : {1, 2, 3};
                    return list;
                }
                (getList()[0]);
                """)));
    }
}
