package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractAssignTests;

public class AssignTest extends AbstractAssignTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void assignNestedListIndex() {
        assertEquals(99.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var list : {{1, 2}, {3, 4}};
                list[1][1] : 99;
                (list[1][1]);
                """)));
    }

    @Test
    void assignToNonListThrows() {
        assertThrows(ReferenceIsImmutableError.class,
                () -> backend.runAndGetValue("var x : 5; x[0] : 10;"));
    }

    @Test
    void assignEvaluatedExpression() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var list : {1, 2, 3};
                list[0] : (1+2);
                (list[0]);
                """)));
    }

    @Test
    void assignTupleIndexWithExpression() {
        assertEquals(99.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var list : [{1, 2}, {3, 4}];
                list[1][1] : 99;
                (list[1][1]);
                """)));
    }
}
