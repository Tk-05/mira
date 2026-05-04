package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class MapExpressionTest extends InterpreterTestBase {

    @Test
    void mapDeclarationReturnsNull() {
        assertNull(run("var m : {\"a\": 1};"));
    }

    @Test
    void mapAccessString() {
        assertEquals("Alice", run("var m : {\"name\": \"Alice\"}; $m[\"name\"];"));
    }

    @Test
    void mapAccessNumber() {
        assertEquals(42.0, run("var m : {\"score\": 42}; eval($m[\"score\"]);"));
    }

    @Test
    void mapAccessMultipleKeys() {
        assertEquals("Bob", run("var m : {\"a\": \"Alice\", \"b\": \"Bob\"}; $m[\"b\"];"));
    }

    @Test
    void mapAssignment() {
        assertEquals("Bob", run("var m : {\"name\": \"Alice\"}; $m[\"name\"] : \"Bob\"; $m[\"name\"];"));
    }

    @Test
    void mapAssignNewKey() {
        assertEquals("hello", run("var m : {\"a\": 1}; $m[\"b\"] : \"hello\"; $m[\"b\"];"));
    }

    @Test
    void mapKeyNotFoundThrows() {
        assertThrows(RuntimeException.class, () -> run("var m : {\"a\": 1}; $m[\"missing\"];"));
    }

    @Test
    void mapWithBooleanValue() {
        assertEquals(true, run("var m : {\"flag\": true}; $m[\"flag\"];"));
    }

    @Test
    void mapValueFromExpression() {
        assertEquals(5.0, run("var m : {\"x\": eval(2+3)}; eval($m[\"x\"]);"));
    }

    @Test
    void mapPassedToFunction() {
        assertEquals("Alice", run("""
                fn getName(m) {
                    return $m["name"];
                }
                var person : {"name": "Alice"};
                getName($person);
                """));
    }

    @Test
    void mapStoredInVariable() {
        assertEquals("yes", run("""
                var m : {"key": "yes"};
                var copy : $m;
                $copy["key"];
                """));
    }

    @Test
    void mapAssignmentMutatesOriginal() {
        assertEquals("new", run("""
                var m : {"k": "old"};
                $m["k"] : "new";
                $m["k"];
                """));
    }

    @Test
    void mapPrintDoesNotThrow() {
        assertNull(run("var m : {\"a\": 1}; print($m);"));
    }
}
