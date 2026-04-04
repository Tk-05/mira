package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class CallExpressionTest extends InterpreterTestBase {

    @Test
    void callVoidFunction() {
        assertNull(run("fn test() {} test();"));
    }

    @Test
    void callFunctionWithReturn() {
        assertEquals(42.0, run("fn answer() { ret(42); } eval(answer());"));
    }

    @Test
    void callFunctionWithArguments() {
        assertEquals(7.0, run("fn add(a, b) { ret(eval($a + $b)); } eval(add(3, 4));"));
    }

    @Test
    void callBuiltinEval() {
        assertEquals(3.0, run("eval(1+2);"));
    }

    @Test
    void callBuiltinPrint() {
        assertNull(run("print(\"hello\");"));
    }

    @Test
    void callBuiltinExecWithReturn() {
        try {
            run("var x : \"ret(eval(0));\"; exec($x);");
        } catch (ReturnSignal r) {
            assertEquals(0.0, r.getValue());
        }
    }

    @Test
    void callBuiltinExecWithComplexReturn() {
        try {
            run("var x : \"ret(eval(2+2));\"; exec($x);");
        } catch (ReturnSignal r) {
            assertEquals(4.0, r.getValue());
        }
    }

    @Test
    void callBuiltinExecWithVariableAccess() {
        try {
            run("var x : 7; var y : \"ret(eval($x * 3));\"; exec($y);");
        } catch (ReturnSignal r) {
            assertEquals(21.0, r.getValue());
        }
    }

    @Test
    void callFunctionResultUsedInExpression() {
        assertEquals(14.0, run("fn double(n) { ret(eval($n * 2)); } eval(double(7));"));
    }

    @Test
    void callImplicitListAccess() {
        assertEquals(1.0, run("""
                fn getList() {
                    var list : {1, 2, 3};
                    ret($list);
                }

                eval(getList()[0]);
                """));
    }
}
