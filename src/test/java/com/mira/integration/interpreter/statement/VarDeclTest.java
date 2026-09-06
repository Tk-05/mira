package com.mira.integration.interpreter.statement;

import static com.mira.integration.InterpreterRunner.normNum;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.values.NullValue;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractVarDeclTests;

public class VarDeclTest extends AbstractVarDeclTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void uninitializedDeclaration() {
        assertNull(backend.runAndGetValue("var x;"));
    }

    @Test
    void uninitializedDeclarationValueIsNull() {
        try {
            backend.runAndGetValue("var x; return x;");
        } catch (ReturnSignal r) {
            assertInstanceOf(NullValue.class, r.getValue());
        }
    }

    @Test
    void booleanTrueInitializer() {
        try {
            backend.runAndGetValue("var x : true; return x;");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void booleanFalseInitializer() {
        try {
            backend.runAndGetValue("var x : false; return x;");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.FALSE, r.getValue());
        }
    }

    @Test
    void constDeclarationReassignThrows() {
        assertThrows(ReferenceIsImmutableError.class,
                () -> backend.runAndGetValue("const x : 0; x : 1;"));
    }

    @Test
    void variableUsageInExpression() {
        backend.createNewGlobalContext();
        backend.getInterpreter().getGlobalEnvironment().define("x", 10);
        backend.getInterpreter().getGlobalEnvironment().define("y", 5);
        assertEquals(15.0, normNum(backend.runContinued("(x + y);")));
    }

    @Test
    void variableUsageWithMultipleVars() {
        backend.createNewGlobalContext();
        backend.getInterpreter().getGlobalEnvironment().define("val", 3);
        backend.getInterpreter().getGlobalEnvironment().define("x", 10);
        assertEquals(23.0, normNum(backend.runContinued("(val + x * 2);")));
    }
}
