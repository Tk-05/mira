package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractBlockTests;

public class BlockTest extends AbstractBlockTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void funcDeclaredInNestedBlockIsCallableAtTopLevel() {
        assertEquals(1.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                {
                    {
                        fn inner() { return 1; }
                    }
                }
                (inner());
                """)));
    }

    @Test
    void blockVarDoesNotLeakButFuncDoes() {
        assertThrows(UndefinedReferenceError.class, () -> backend.runAndGetValue("""
                {
                    var x : 5;
                    fn getX() { return 5; }
                }
                x;
                """));
    }

    @Test
    void funcInBlockCanCaptureOuterVar() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var base : 10;
                {
                    fn getBase() { return (base); }
                }
                (getBase());
                """)));
    }

    @Test
    void multipleFuncsDeclaredInBlock() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                {
                    fn inc(n) { return (n + 1); }
                    fn dec(n) { return (n - 1); }
                }
                (inc(dec(3)));
                """)));
    }

    @Test
    void blockDoesNotLeakVariables() {
        assertThrows(UndefinedReferenceError.class, () -> backend.runAndGetValue("""
                {
                    var ref : 0;
                }
                ref;
                """));
    }

    @Test
    void blockDoesNotShadowOuterVariable() {
        assertEquals(69.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var ref : 69;
                {
                    var ref : 10;
                }
                (ref);
                """)));
    }

    @Test
    void nestedBlocksInnerVarDoesNotLeak() {
        assertThrows(UndefinedReferenceError.class, () -> backend.runAndGetValue("""
                {
                    var inner : 5;
                    {
                        var deepInner : 10;
                    }
                    deepInner;
                }
                """));
    }
}
