package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.integration.InterpreterTestBase;
import com.mira.runtime.values.NullValue;

public class ExecBlockTest extends InterpreterTestBase {

    @Test
    void returnValueFromExecBlock() {
        assertEquals(42.0, run("eval(exec { return 42; });"));
    }

    @Test
    void returnWithComputation() {
        assertEquals(20.0, run("""
                var result : exec { var x : 10; return eval($x * 2); };
                eval($result);
                """));
    }

    @Test
    void noReturnYieldsNull() {
        assertInstanceOf(NullValue.class, run("var result : exec { var x : 1; }; $result;"));
    }

    @Test
    void variablesDoNotLeakOutside() {
        assertThrows(UndefinedReferenceError.class, () -> run("""
                exec { var secret : 5; };
                $secret;
                """));
    }

    @Test
    void canReadOuterVariable() {
        assertEquals(10.0, run("""
                var x : 10;
                eval(exec { return eval($x); });
                """));
    }

    @Test
    void canModifyOuterVariable() {
        assertEquals(99.0, run("""
                var x : 10;
                exec { $x : 99; };
                eval($x);
                """));
    }

    @Test
    void execAsExpressionInBinaryOp() {
        assertEquals(50.0, run("""
                var a : exec { return 20; };
                var b : exec { return 30; };
                eval($a + $b);
                """));
    }

    @Test
    void execWithLoop() {
        assertEquals(10.0, run("""
                var result : exec {
                    var sum : 0;
                    for (var i : 0; $i < 5; $i++) {
                        $sum : eval($sum + $i);
                    }
                    return eval($sum);
                };
                eval($result);
                """));
    }

    @Test
    void nestedExecBlocks() {
        skipCompilerTest = true;
        assertEquals(7.0, run("""
                eval(exec {
                    var outer : 3;
                    var inner : exec { return eval($outer + 4); };
                    return eval($inner);
                });
                """));
    }

    @Test
    void isolatedCannotAccessLocalVariable() {
        assertThrows(UndefinedReferenceError.class, () -> run("""
                fn test() {
                    var localVar : 42;
                    return exec isolated { return eval($localVar); };
                }
                eval(test());
                """));
    }

    @Test
    void isolatedCanAccessGlobalVariable() {
        assertEquals(55.0, run("""
                var globalVar : 55;
                fn test() {
                    var localVar : 42;
                    return exec isolated { return eval($globalVar); };
                }
                eval(test());
                """));
    }

    @Test
    void localModeCanAccessFunctionLocalVariable() {
        skipCompilerTest = true;
        assertEquals(42.0, run("""
                fn test() {
                    var localVar : 42;
                    return exec { return eval($localVar); };
                }
                eval(test());
                """));
    }

    @Test
    void returnInExecDoesNotReturnFromOuterFunction() {
        assertEquals(100.0, run("""
                fn test() {
                    var r : exec { return 1; };
                    return 100;
                }
                eval(test());
                """));
    }

    @Test
    void execWithConditional() {
        assertEquals(5.0, run("""
                var flag : true;
                eval(exec {
                    if ($flag) {
                        return 5;
                    }
                    return 0;
                });
                """));
    }
}
