package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.InterpreterTestBase;

public class AccessExpressionTest extends InterpreterTestBase {

    @Test
    void simpleIndexAccess() {
        assertEquals(2.0, run("var list : {1, 2, 3}; eval($list[1]);"));
    }

    @Test
    void zeroIndexAccess() {
        assertEquals(1.0, run("var list : {1, 2, 3}; eval($list[0]);"));
    }

    @Test
    void expressionAsIndex() {
        assertEquals(30.0, run("var list : {10, 20, 30}; eval($list[eval(1+1)]);"));
    }

    @Test
    void nestedAccess() {
        assertEquals(4.0, run("var list : {{1, 2}, {3, 4}}; eval($list[1][1]);"));
    }

    @Test
    void multiLevelNestedAccess() {
        assertEquals(69.0, run("var tuple : [[1,2],[3,69],69]; eval($tuple[1][1]);"));
    }

    @Test
    void outOfBoundsThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> run("var list : {1, 2, 3}; $list[5];"));
    }

    @Test
    void emptyContainerThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> run("var tuple : []; $tuple[0];"));
    }

    @Test
    void accessOnFunctionResult() {
        assertEquals(1.0, run("""
                fn getList() { var list : {1, 2, 3}; ret($list); }
                eval(getList()[0]);
                """));
    }

    @Test
    void variableExpressionAsIndex() {
        assertEquals(20.0, run("""
                var list : {10, 20, 30};
                var idx : 1;
                eval($list[eval($idx)]);
                """));
    }
}
