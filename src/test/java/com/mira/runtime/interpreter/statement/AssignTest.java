package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class AssignTest extends InterpreterTestBase {

    @Test
    void simpleAssignment() {
        try {
            run("var x : 24; var y : 18; var z; $z : $x + $y; return eval($z);");
        } catch (ReturnSignal r) {
            assertEquals(42.0, normNum(r.getValue()));
        }
    }

    @Test
    void reassignVariable() {
        assertEquals(20.0, run("var x : 10; $x : 20; eval($x);"));
    }

    @Test
    void assignArithmeticExpression() {
        assertEquals(15.0, run("var x : 10; $x : $x + 5; eval($x);"));
    }

    @Test
    void assignListIndex() {
        assertEquals(10.0, run("""
                var list : {1, 2, 3};
                $list[1] : 10;
                eval($list[1]);
                """));
    }

    @Test
    void assignNestedListIndex() {
        assertEquals(99.0, run("""
                var list : {{1, 2}, {3, 4}};
                $list[1][1] : 99;
                eval($list[1][1]);
                """));
    }

    @Test
    void assignToNonListThrows() {
        assertThrows(ReferenceIsImmutableError.class, () -> run("var x : 5; $x[0] : 10;"));
    }

    @Test
    void assignEvaluatedExpression() {
        assertEquals(3.0, run("""
                var list : {1, 2, 3};
                $list[0] : eval(1+2);
                eval($list[0]);
                """));
    }

    @Test
    void chainedAssignments() {
        assertEquals(30.0, run("var x : 10; var y : 20; $x : $x + $y; eval($x);"));
    }

    @Test
    void assignTupleIndexWithExpression() {
        assertEquals(99.0, run("""
                var list : [{1, 2}, {3, 4}];
                $list[1][1] : 99;
                eval($list[1][1]);
                """));
    }
}
