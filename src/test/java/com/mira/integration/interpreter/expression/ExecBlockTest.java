package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractExecBlockTests;
import com.mira.runtime.values.NullValue;

public class ExecBlockTest extends AbstractExecBlockTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void returnValueFromExecBlock() {
        assertEquals(42.0, InterpreterRunner.normNum(backend.runAndGetValue("eval(exec { return 42; });")));
    }

    @Test
    void returnWithComputation() {
        assertEquals(20.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var result : exec { var x : 10; return eval($x * 2); };
                eval($result);
                """)));
    }

    @Test
    void noReturnYieldsNull() {
        assertInstanceOf(NullValue.class, backend.runAndGetValue("var result : exec { var x : 1; }; $result;"));
    }

    @Test
    void variablesDoNotLeakOutside() {
        assertThrows(UndefinedReferenceError.class, () -> backend.runAndGetValue("""
                exec { var secret : 5; };
                $secret;
                """));
    }

    @Test
    void canReadOuterVariable() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var x : 10;
                eval(exec { return eval($x); });
                """)));
    }

    @Test
    void canModifyOuterVariable() {
        assertEquals(99.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var x : 10;
                exec { $x : 99; };
                eval($x);
                """)));
    }

    @Test
    void execAsExpressionInBinaryOp() {
        assertEquals(50.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var a : exec { return 20; };
                var b : exec { return 30; };
                eval($a + $b);
                """)));
    }

    @Test
    void nestedExecBlocks() {
        assertEquals(7.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                eval(exec {
                    var outer : 3;
                    var inner : exec { return eval($outer + 4); };
                    return eval($inner);
                });
                """)));
    }

    @Test
    void isolatedCannotAccessLocalVariable() {
        assertThrows(UndefinedReferenceError.class, () -> backend.runAndGetValue("""
                fn test() {
                    var localVar : 42;
                    return exec isolated { return eval($localVar); };
                }
                eval(test());
                """));
    }

    @Test
    void isolatedCanAccessGlobalVariable() {
        assertEquals(55.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var globalVar : 55;
                fn test() {
                    var localVar : 42;
                    return exec isolated { return eval($globalVar); };
                }
                eval(test());
                """)));
    }

    @Test
    void returnInExecDoesNotReturnFromOuterFunction() {
        assertEquals(100.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn test() {
                    var r : exec { return 1; };
                    return 100;
                }
                eval(test());
                """)));
    }
}
