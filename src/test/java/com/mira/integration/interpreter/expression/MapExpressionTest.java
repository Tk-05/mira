package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractMapExpressionTests;

public class MapExpressionTest extends AbstractMapExpressionTests {

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
    void mapDeclarationReturnsNull() {
        assertNull(backend.runAndGetValue("var m : {\"a\": 1};"));
    }

    @Test
    void mapAccessMultipleKeys() {
        assertEquals("Bob", backend.runAndGetValue("var m : {\"a\": \"Alice\", \"b\": \"Bob\"}; $m[\"b\"];"));
    }

    @Test
    void mapKeyNotFoundThrows() {
        assertThrows(RuntimeException.class, () -> backend.runAndGetValue("var m : {\"a\": 1}; $m[\"missing\"];"));
    }

    @Test
    void mapWithBooleanValue() {
        assertEquals(true, backend.runAndGetValue("var m : {\"flag\": true}; $m[\"flag\"];"));
    }

    @Test
    void mapValueFromExpression() {
        assertEquals(5.0, InterpreterRunner.normNum(backend.runAndGetValue("var m : {\"x\": eval(2+3)}; eval($m[\"x\"]);")));
    }

    @Test
    void mapStoredInVariable() {
        assertEquals("yes", backend.runAndGetValue("""
                var m : {"key": "yes"};
                var copy : $m;
                $copy["key"];
                """));
    }

    @Test
    void mapAssignmentMutatesOriginal() {
        assertEquals("new", backend.runAndGetValue("""
                var m : {"k": "old"};
                $m["k"] : "new";
                $m["k"];
                """));
    }

    @Test
    void mapPrintDoesNotThrow() {
        assertNull(backend.runAndGetValue("var m : {\"a\": 1}; print($m);"));
    }

    @Test
    void structAssignedIntoMapSurvivesFieldAccess() {
        assertEquals(1.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var m : {"a": 1};
                var key : "b";
                $m[$key] : { var foo : 1; };
                eval($m[$key].foo);
                """)));
    }

    @Test
    void structAssignedIntoMapSurvivesFieldAccessViaIntermediateVariable() {
        assertEquals(1.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var m : {"a": 1};
                var key : "b";
                $m[$key] : { var foo : 1; };
                var v : $m[$key];
                eval($v.foo);
                """)));
    }
}
