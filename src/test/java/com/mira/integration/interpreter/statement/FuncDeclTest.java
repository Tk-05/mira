package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractFuncDeclTests;

public class FuncDeclTest extends AbstractFuncDeclTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void simpleFunctionDeclaration() {
        assertNull(backend.runAndGetValue("fn foo() {}"));
    }

    @Test
    void voidFunctionCallReturnsNull() {
        assertNull(backend.runAndGetValue("fn foo() {} foo();"));
    }

    @Test
    void functionWithStringReturn() {
        try {
            backend.runAndGetValue("""
                    fn greet(name) {
                        return "Hello " $name;
                    }
                    var greeting : "return greet(\\"World\\");";
                    exec($greeting);
                    """);
        } catch (ReturnSignal r) {
            assertEquals("Hello World", r.getValue());
        }
    }
}
