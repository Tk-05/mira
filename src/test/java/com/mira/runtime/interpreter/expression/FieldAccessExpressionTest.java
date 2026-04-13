package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.InterpreterTestBase;

public class FieldAccessExpressionTest extends InterpreterTestBase {

    @Test
    void accessInitializedField() {
        assertEquals(0.0, run("""
                var obj : { var x : 0; };
                $obj.x;
                """));
    }

    @Test
    void accessUninitializedField() {
        assertNull(run("""
                var obj : { var x; };
                $obj.x;
                """));
    }

    @Test
    void accessStringField() {
        assertEquals("hello", run("""
                var obj : { var name : "hello"; };
                $obj.name;
                """));
    }

    @Test
    void accessFieldInArithmetic() {
        assertEquals(15.0, run("""
                var obj : { var x : 10; var y : 5; };
                eval($obj.x + $obj.y);
                """));
    }

    @Test
    void multipleFieldsOnSameObject() {
        assertEquals(0.0, run("""
                var obj : {
                    var a : 0;
                    var b : 1;
                    var c : 2;
                };
                $obj.a;
                """));
    }

    @Test
    void fieldAccessAfterAssignment() {
        assertEquals(99.0, run("""
                var obj : { var x : 0; };
                $obj.x : 99;
                eval($obj.x);
                """));
    }

}
