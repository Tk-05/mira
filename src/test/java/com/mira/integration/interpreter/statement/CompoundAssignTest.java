package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractCompoundAssignTests;

public class CompoundAssignTest extends AbstractCompoundAssignTests {

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
    void addAssignWithVariable() {
        assertEquals(25.0, InterpreterRunner.normNum(backend.runAndGetValue("var x : 10; var y : 15; x +: y; (x);")));
    }

    @Test
    void chainedCompoundAssigns() {
        assertEquals(24.0,
                InterpreterRunner.normNum(backend.runAndGetValue("var x : 10; x +: 5; x -: 3; x *: 2; (x);")));
    }

    @Test
    void divideAssignResultIsDecimal() {
        assertEquals(3.5, InterpreterRunner.normNum(backend.runAndGetValue("var x : 7; x /: 2; (x);")));
    }

    @Test
    void addAssignOnFieldAccess() {
        assertEquals(15.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var obj : { var n : 10; };
                obj.n +: 5;
                (obj.n);
                """)));
    }

    @Test
    void multiplyAssignOnFieldAccess() {
        assertEquals(6.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var obj : { var n : 2; };
                obj.n *: 3;
                (obj.n);
                """)));
    }
}
