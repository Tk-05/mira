package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractTypeofTests;

public class TypeofTest extends AbstractTypeofTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void numberFloat() {
        assertEquals("number", backend.runAndGetValue("typeof 3.14;"));
    }

    @Test
    void boolFalse() {
        assertEquals("bool", backend.runAndGetValue("typeof false;"));
    }

    @Test
    void array() {
        assertEquals("array", backend.runAndGetValue("var a : [1, 2, 3]; typeof $a;"));
    }

    @Test
    void fn() {
        assertEquals("fn", backend.runAndGetValue("var f : fn(x) { return $x; }; typeof $f;"));
    }

    @Test
    void namedFn() {
        assertEquals("fn", backend.runAndGetValue("fn add(a, b) { return eval($a + $b); } typeof $add;"));
    }

    @Test
    void object() {
        assertEquals("object", backend.runAndGetValue("var o : { var x : 1; }; typeof $o;"));
    }

    @Test
    void usedInSwitch() {
        assertEquals("number", backend.runAndGetValue("""
                var x : 1;
                var r : switch(typeof $x) {
                    case("number") -> "number"
                    case("string") -> "string"
                    default -> "other"
                };
                $r;
                """));
    }
}
