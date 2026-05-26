package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractSwitchTests;

public class SwitchTest extends AbstractSwitchTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void noMatchNoDefaultReturnsNull() {
        assertNull(backend.runAndGetValue("""
                var x : 99;
                switch ($x) {
                    case (1) { return false; }
                    case (2) { return false; }
                }
                """));
    }

    @Test
    void onlyFirstMatchExecutes() {
        backend.runAndGetValue("""
                var x : 1;
                var count : 0;
                switch ($x) {
                    case (1) { $count : eval($count + 1); }
                    case (1) { $count : eval($count + 1); }
                }
                """);
        assertEquals(1.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("count")));
    }

    @Test
    void caseBodyCanHaveMultipleStatements() {
        backend.runAndGetValue("""
                var x : 2;
                var a : 0;
                var b : 0;
                switch ($x) {
                    case (1) { $a : 1; $b : 1; }
                    case (2) { $a : 10; $b : 20; }
                }
                """);
        assertEquals(10.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("a")));
        assertEquals(20.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("b")));
    }

    @Test
    void switchWithReturnSignal() {
        try {
            backend.runAndGetValue("""
                    var x : 1;
                    switch ($x) {
                        case (1) { return true; }
                        case (2) { return false; }
                    }
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void defaultWithNoMatchingCase() {
        backend.runAndGetValue("""
                var result : 0;
                switch (eval(5)) {
                    case (1) { $result : 1; }
                    case (2) { $result : 2; }
                    default  { $result : 99; }
                }
                """);
        assertEquals(99.0, InterpreterRunner.normNum(backend.getInterpreter().getGlobalEnvironment().get("result")));
    }
}
