package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.InterpreterTestBase;

public class ListExpressionTest extends InterpreterTestBase {

    @Test
    void emptyList() {
        assertNull(run("var list : {};"));
    }

    @Test
    void listCreationAndAccess() {
        assertEquals(1.0, run("var list : {1, 2, 3}; eval($list[0]);"));
    }

    @Test
    void listAccessLastElement() {
        assertEquals(3.0, run("var list : {1, 2, 3}; eval($list[2]);"));
    }

    @Test
    void listAssignment() {
        assertEquals(10.0, run("var list : {1, 2, 3}; $list[1] : 10; eval($list[1]);"));
    }

    @Test
    void nestedListAccess() {
        assertEquals(3.0, run("var list : {{1, 2}, {3, 4}}; eval($list[1][0]);"));
    }

    @Test
    void nestedListAssignment() {
        assertEquals(99.0, run("var list : {{1, 2}, {3, 4}}; $list[1][1] : 99; eval($list[1][1]);"));
    }

    @Test
    void nestedListAssignmentWithTuple() {
        assertEquals(99.0, run("var list : [{1, 2}, {3, 4}]; $list[1][1] : 99; eval($list[1][1]);"));
    }

    @Test
    void listWithExpressions() {
        assertEquals(3.0, run("var list : {1+2, 3*4, 5}; eval($list[0]);"));
    }

    @Test
    void expressionAsIndex() {
        assertEquals(30.0, run("var list : {10, 20, 30}; eval($list[eval(1+1)]);"));
    }

    @Test
    void indexOutOfBounds() {
        assertThrows(IndexOutOfBoundsException.class, () -> run("var list : {1, 2, 3}; $list[5];"));
    }

    @Test
    void assignmentEvaluatesExpression() {
        assertEquals(3.0, run("var list : {1, 2, 3}; $list[0] : eval(1+2); eval($list[0]);"));
    }

    @Test
    void implicitListAccessFromFunction() {
        assertEquals(1.0, run("""
                fn getList() {
                    var list : {1, 2, 3};
                    return $list;
                }

                eval(getList()[0]);
                """));
    }

    @Test
    void listForeach() {
        assertEquals(3.0, run("""
                var list : {1,2,3};
                var last;
                foreach(var element in $list) {
                    $last : $element;
                }
                $last;
                """));
    }

    @Test
    void listIsMutable() {
        assertEquals(42.0, run("""
                var list : {1, 2, 3};
                $list[0] : 42;
                eval($list[0]);
                """));
    }
}
