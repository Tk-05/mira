package com.mira.integration.compiler.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.CompilerRunner;
import com.mira.integration.shared.expression.AbstractEvalExecTests;

public class EvalExecTest extends AbstractEvalExecTests {

    private final CompilerRunner backend = new CompilerRunner();

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void evalDeclaredTopLevelVariableIsVisibleToCompiledCode() {
        assertEquals("42", backend.run("""
                eval("var dynamicVar : 42;");
                print($dynamicVar);
                """));
    }
}
