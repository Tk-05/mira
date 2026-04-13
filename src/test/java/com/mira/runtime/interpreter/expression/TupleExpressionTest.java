package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.ImmutableCollectionError;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class TupleExpressionTest extends InterpreterTestBase {

    @Test
    void emptyTupleAccessThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> run("var tuple : []; $tuple[0];"));
    }

    @Test
    void tupleWithLiterals() {
        assertEquals(2.0, run("var tuple : [1,2,3]; eval($tuple[1]);"));
    }

    @Test
    void tupleWithExpressions() {
        assertEquals(3.0, run("var tuple : [1+2, 3*4, 5]; eval($tuple[0]);"));
    }

    @Test
    void nestedTuples() {
        assertEquals(69.0, run("var tuple : [[1,2],[3,69],69]; eval($tuple[1][1]);"));
    }

    @Test
    void tupleFirstElement() {
        assertEquals(1.0, run("var tuple : [1,2,3]; eval($tuple[0]);"));
    }

    @Test
    void tupleLastElement() {
        assertEquals(3.0, run("var tuple : [1,2,3]; eval($tuple[2]);"));
    }

    @Test
    void tupleIsImmutable() {
        assertThrows(ImmutableCollectionError.class, () -> run("""
                var tuple : [1,2,3];
                $tuple[0] : 99;
                """));
    }

    @Test
    void tupleInForeach() {
        assertEquals(3.0, run("""
                var list : [1,2,3];
                var last;
                foreach(var element in $list) {
                    $last : $element;
                }
                $last;
                """));
    }
}
