package com.mira.integration.compiler.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.CompilerRunner;
import com.mira.integration.shared.expression.AbstractMapExpressionTests;

public class MapExpressionTest extends AbstractMapExpressionTests {

    private final CompilerRunner backend = new CompilerRunner();

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void structAssignedIntoMapSurvivesFieldAccess() {
        assertEquals("1", backend.run("""
                var m : {"a": 1};
                var key : "b";
                $m[$key] : { var foo : 1; };
                print($m[$key].foo);
                """));
    }

    @Test
    void structAssignedIntoMapSurvivesFieldAccessViaIntermediateVariable() {
        assertEquals("1", backend.run("""
                var m : {"a": 1};
                var key : "b";
                $m[$key] : { var foo : 1; };
                var v : $m[$key];
                print($v.foo);
                """));
    }
}
