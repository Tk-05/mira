package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.InterpreterTestBase;

public class ObjectExpressionTest extends InterpreterTestBase {

    @Test
    void objectWithSingleField() {
        assertEquals(0.0, run("""
                var obj : {
                    var test : 0;
                };
                $obj.test;
                """));
    }

    @Test
    void objectWithUninitializedField() {
        assertNull(run("""
                var obj : {
                    var test : 0;
                    var test2;
                };
                $obj.test2;
                """));
    }

    @Test
    void objectWithMultipleFields() {
        assertEquals("hello", run("""
                var obj : {
                    var x : 0;
                    var name : "hello";
                };
                $obj.name;
                """));
    }

    @Test
    void objectFieldArithmetic() {
        assertEquals(10.0, run("""
                var obj : {
                    var x : 10;
                };
                eval($obj.x);
                """));
    }

    @Test
    void objectDeclarationReturnsNull() {
        assertNull(run("""
                var obj : {
                    var x : 0;
                };
                """));
    }

    @Test
    void objectFieldCanBeReassigned() {
        assertEquals(42.0, run("""
                var obj : {
                    var x : 0;
                };
                $obj.x : 42;
                eval($obj.x);
                """));
    }
}
