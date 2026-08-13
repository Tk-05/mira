package com.mira.integration.compiler.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.CompilerRunner;
import com.mira.integration.shared.expression.AbstractArrayExpressionTests;

public class ArrayExpressionTest extends AbstractArrayExpressionTests {

    private final CompilerRunner backend = new CompilerRunner();

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void structAssignedIntoArraySurvivesFieldAccess() {
        assertEquals("42", backend.run("""
                var arr : [0];
                $arr[0] : { var foo : 42; };
                print($arr[0].foo);
                """));
    }
}
