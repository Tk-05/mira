package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;

public abstract class AbstractExecBlockTests {

    protected abstract String runForOutput(String source);

    @Test
    void returnValueFromExecBlock() {
        assertEquals("42", runForOutput("print(exec { return 42; });"));
    }

    @Test
    void noReturnYieldsNull() {
        assertEquals("null", runForOutput("print(exec { var x : 1; });"));
    }

    @Test
    void canReadAndModifyOuterVariable() {
        assertEquals("99", runForOutput("""
                var x : 10;
                exec { x : 99; };
                print(x);
                """));
    }

    @Test
    void nestedExecBlocks() {
        assertEquals("7", runForOutput("""
                print(exec {
                    var outer : 3;
                    var inner : exec { return outer + 4; };
                    return inner;
                });
                """));
    }

    @Test
    void returnInExecDoesNotReturnFromOuterFunction() {
        assertEquals("100", runForOutput("""
                fn myFunc() {
                    var r : exec { return 1; };
                    return 100;
                }
                print(myFunc());
                """));
    }

    @Test
    void isolatedCanAccessGlobalVariable() {
        assertEquals("55", runForOutput("""
                var globalVar : 55;
                fn myFunc() {
                    var localVar : 42;
                    return exec isolated { return globalVar; };
                }
                print(myFunc());
                """));
    }

    @Test
    void isolatedCannotAccessOuterLocal() {
        assertThrows(UndefinedReferenceError.class, () -> runForOutput("""
                fn myFunc() {
                    var localVar : 42;
                    return exec isolated { return localVar; };
                }
                print(myFunc());
                """));
    }

    @Test
    void breakInsideLoopEscapesThroughExecBlock() {
        assertEquals("6", runForOutput("""
                fn myFunc() {
                    var total : 0;
                    for (var i : 0; i < 10; i +: 1) {
                        total +: i;
                        exec { if (i == 3) { break; } };
                    }
                    return total;
                }
                print(myFunc());
                """));
    }

    @Test
    void continueInsideLoopEscapesThroughExecBlock() {
        assertEquals("25", runForOutput("""
                fn myFunc() {
                    var total : 0;
                    for (var i : 0; i < 10; i +: 1) {
                        exec { if (i % 2 == 0) { continue; } };
                        total +: i;
                    }
                    return total;
                }
                print(myFunc());
                """));
    }
}
