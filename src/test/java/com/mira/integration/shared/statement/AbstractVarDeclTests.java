package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractVarDeclTests {

    protected abstract String runForOutput(String source);

    @Test
    void numericInitializer() {
        assertEquals("10", runForOutput("var x : 10; print($x);"));
    }

    @Test
    void stringInitializer() {
        assertEquals("hello", runForOutput("var x : \"hello\"; print($x);"));
    }

    @Test
    void expressionInitializer() {
        assertEquals("42", runForOutput("var x : 24; var y : 18; var z : eval($x + $y); print($z);"));
    }

    @Test
    void multipleDeclarations() {
        assertEquals("30", runForOutput("var x : 10; var y : 20; print(eval($x + $y));"));
    }

    @Test
    void declarationWithFunctionResult() {
        assertEquals("5", runForOutput("""
                fn getValue() { return 5; }
                var x : eval(getValue());
                print($x);
                """));
    }

    @Test
    void constDeclaration() {
        assertEquals("0", runForOutput("const x : 0; print($x);"));
    }
}
