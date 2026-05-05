package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;
import com.mira.runtime.values.NullValue;

public class NullTest extends InterpreterTestBase {

    @Test
    void nullLiteralReturnsNullValue() {
        assertInstanceOf(NullValue.class, run("null;"));
    }

    @Test
    void uninitializedVarIsNull() {
        assertInstanceOf(NullValue.class, run("var x; $x;"));
    }

    @Test
    void explicitNullAssignment() {
        assertInstanceOf(NullValue.class, run("var x : null; $x;"));
    }

    @Test
    void nullToStringIsNull() {
        assertEquals("null", run("\"\" null;"));
    }

    @Test
    void nullEqualsNull() {
        assertEquals(Boolean.TRUE, run("null == null;"));
    }

    @Test
    void nullNotEqualsValue() {
        assertEquals(Boolean.FALSE, run("null == 5;"));
    }

    @Test
    void nullNotEqualsString() {
        assertEquals(Boolean.FALSE, run("null == \"hello\";"));
    }

    @Test
    void uninitializedVarEqualsNull() {
        assertEquals(Boolean.TRUE, run("var x; $x == null;"));
    }

    @Test
    void assignNullToVar() {
        assertEquals(Boolean.TRUE, run("var x : 10; $x : null; $x == null;"));
    }

    @Test
    void nullIsFalsyInIf() {
        assertEquals("no", run("""
                var x : null;
                var result;
                if ($x) {
                    $result : "yes";
                } else {
                    $result : "no";
                }
                $result;
                """));
    }

    @Test
    void nullIsFalsyInWhile() {
        assertEquals(0.0, run("""
                var count : 0;
                var cond : null;
                while ($cond) {
                    $count += 1;
                }
                eval($count);
                """));
    }

    @Test
    void nonNullIsNotNull() {
        assertEquals(Boolean.FALSE, run("var x : 5; $x == null;"));
    }

    @Test
    void reassignFromNullToValue() {
        assertEquals(42.0, run("var x : null; $x : 42; eval($x);"));
    }
}
