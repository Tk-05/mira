package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.IndexOutOfBoundsError;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractAccessExpressionTests;

public class AccessExpressionTest extends AbstractAccessExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void expressionAsIndex() {
        assertEquals(30.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var list : {10, 20, 30}; (list[eval(1+1)]);")));
    }

    @Test
    void multiLevelNestedAccess() {
        assertEquals(69.0, InterpreterRunner.normNum(backend.runAndGetValue(
                "var tuple : [[1,2],[3,69],69]; (tuple[1][1]);")));
    }

    @Test
    void outOfBoundsThrows() {
        assertThrows(IndexOutOfBoundsError.class, () -> backend.run("var list : {1, 2, 3}; print(list[5]);"));
    }

    @Test
    void emptyContainerThrows() {
        assertThrows(IndexOutOfBoundsError.class, () -> backend.runAndGetValue("var tuple : []; tuple[0];"));
    }

    @Test
    void variableExpressionAsIndex() {
        assertEquals(20.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var list : {10, 20, 30};
                var idx : 1;
                (list[eval(idx)]);
                """)));
    }
}
