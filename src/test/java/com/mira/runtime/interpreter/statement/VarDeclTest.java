package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.interpreter.InterpreterTestBase;
import com.mira.runtime.interpreter.NullValue;

public class VarDeclTest extends InterpreterTestBase {

    @Test
    void uninitializedDeclaration() {
        assertNull(run("var x;"));
    }

    @Test
    void uninitializedDeclarationValueIsNull() {
        try {
            run("var x; ret($x);");
        } catch (ReturnSignal r) {
            assertInstanceOf(NullValue.class, r.getValue());
        }
    }

    @Test
    void numericInitializer() {
        assertEquals(10.0, run("var x : 10; eval($x);"));
    }

    @Test
    void stringInitializer() {
        try {
            run("var x : \"hello\"; ret($x);");
        } catch (ReturnSignal r) {
            assertEquals("hello", r.getValue());
        }
    }

    @Test
    void expressionInitializer() {
        assertEquals(42.0, run("var x : 24; var y : 18; var z : eval($x + $y); eval($z);"));
    }

    @Test
    void multipleDeclarations() {
        assertEquals(30.0, run("var x : 10; var y : 20; eval($x + $y);"));
    }

    @Test
    void declarationWithFunctionResult() {
        assertEquals(5.0, run("""
                fn getValue() { ret(5); }
                var x : eval(getValue());
                eval($x);
                """));
    }

    @Test
    void booleanTrueInitializer() {
        try {
            run("var x : true; ret($x);");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void booleanFalseInitializer() {
        try {
            run("var x : false; ret($x);");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.FALSE, r.getValue());
        }
    }

    @Test
    void constDeclaration() {
        assertEquals(0.0, run("const test : 0; eval($test);"));
    }

    @Test
    void constDeclarationReassignThrows() {
        assertThrows(ReferenceIsImmutableError.class, () -> run("const test : 0; $test : 1;"));
    }

    @Test
    void variableUsageInExpression() {
        createNewGlobalContext();
        Interpreter.getGlobalEnvironment().define("x", 10);
        Interpreter.getGlobalEnvironment().define("y", 5);
        assertEquals(15.0, runContinued("eval($x + $y);"));
    }

    @Test
    void variableUsageWithMultipleVars() {
        createNewGlobalContext();
        Interpreter.getGlobalEnvironment().define("val", 3);
        Interpreter.getGlobalEnvironment().define("x", 10);
        assertEquals(23.0, runContinued("eval($val + $x * 2);"));
    }
}
