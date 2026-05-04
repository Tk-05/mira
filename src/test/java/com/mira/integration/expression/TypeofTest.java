package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class TypeofTest extends InterpreterTestBase {

    @Test
    void number() {
        assertEquals("number", run("typeof 42;"));
    }

    @Test
    void numberFloat() {
        assertEquals("number", run("typeof 3.14;"));
    }

    @Test
    void string() {
        assertEquals("string", run("typeof \"hello\";"));
    }

    @Test
    void boolTrue() {
        assertEquals("bool", run("typeof true;"));
    }

    @Test
    void boolFalse() {
        assertEquals("bool", run("typeof false;"));
    }

    @Test
    void nullVal() {
        assertEquals("null", run("typeof null;"));
    }

    @Test
    void list() {
        assertEquals("list", run("var l : {1, 2, 3}; typeof $l;"));
    }

    @Test
    void array() {
        assertEquals("array", run("var a : [1, 2, 3]; typeof $a;"));
    }

    @Test
    void fn() {
        assertEquals("fn", run("var f : fn(x) { return $x; }; typeof $f;"));
    }

    @Test
    void namedFn() {
        assertEquals("fn", run("fn add(a, b) { return eval($a + $b); } typeof $add;"));
    }

    @Test
    void object() {
        assertEquals("object", run("var o : { var x : 1; }; typeof $o;"));
    }

    @Test
    void variable() {
        assertEquals("number", run("var x : 99; typeof $x;"));
    }

    @Test
    void usedInCondition() {
        assertEquals("yes", run("var x : 42; var r : typeof $x == \"number\" ? \"yes\" : \"no\"; $r;"));
    }

    @Test
    void usedInSwitch() {
        assertEquals("number", run("""
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
