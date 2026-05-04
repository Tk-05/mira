package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class ArrayExpressionTest extends InterpreterTestBase {

    @Test
    void emptyArrayAccessThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> run("var arr : []; $arr[0];"));
    }

    @Test
    void arrayWithLiterals() {
        assertEquals(2.0, run("var arr : [1,2,3]; eval($arr[1]);"));
    }

    @Test
    void arrayWithExpressions() {
        assertEquals(3.0, run("var arr : [1+2, 3*4, 5]; eval($arr[0]);"));
    }

    @Test
    void nestedArrays() {
        assertEquals(69.0, run("var arr : [[1,2],[3,69],69]; eval($arr[1][1]);"));
    }

    @Test
    void arrayFirstElement() {
        assertEquals(1.0, run("var arr : [1,2,3]; eval($arr[0]);"));
    }

    @Test
    void arrayLastElement() {
        assertEquals(3.0, run("var arr : [1,2,3]; eval($arr[2]);"));
    }

    @Test
    void arrayIsMutable() {
        assertEquals(99.0, run("""
                var arr : [1,2,3];
                $arr[0] : 99;
                eval($arr[0]);
                """));
    }

    @Test
    void arrayMutateMiddleElement() {
        assertEquals(42.0, run("""
                var arr : [10,20,30];
                $arr[1] : 42;
                eval($arr[1]);
                """));
    }

    @Test
    void arrayMutateLastElement() {
        assertEquals(7.0, run("""
                var arr : [1,2,3];
                $arr[2] : 7;
                eval($arr[2]);
                """));
    }

    @Test
    void arrayInForeach() {
        assertEquals(3.0, run("""
                var arr : [1,2,3];
                var last;
                foreach(var element in $arr) {
                    $last : $element;
                }
                $last;
                """));
    }

}
