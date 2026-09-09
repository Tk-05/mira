package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractCallExpressionTests;

public class CallExpressionTest extends AbstractCallExpressionTests {

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
    void callVoidFunction() {
        assertNull(backend.runAndGetValue("fn foo() {} foo();"));
    }

    @Test
    void callBuiltinEval() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("(1+2);")));
    }

    @Test
    void callBuiltinEvalWithReturn() {
        try {
            backend.runAndGetValue("var x : \"return (0);\"; eval(x);");
        } catch (ReturnSignal r) {
            assertEquals(0.0, InterpreterRunner.normNum(r.getValue()));
        }
    }

    @Test
    void callBuiltinEvalWithVariableAccess() {
        try {
            backend.runAndGetValue("var x : 7; var y : \"return (x * 3);\"; eval(y);");
        } catch (ReturnSignal r) {
            assertEquals(21.0, InterpreterRunner.normNum(r.getValue()));
        }
    }

    @Test
    void callImplicitListAccess() {
        assertEquals(1.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn getList() {
                    var list : {1, 2, 3};
                    return list;
                }
                (getList()[0]);
                """)));
    }
}
