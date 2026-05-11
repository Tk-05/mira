package com.mira.integration.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.integration.InterpreterTestBase;

public class BlockTest extends InterpreterTestBase {

    @Test
    void funcDeclaredInBlockIsCallableAfterBlock() {
        assertEquals(42.0, run("""
                {
                    fn answer() { return 42; }
                }
                eval(answer());
                """));
    }

    @Test
    void funcDeclaredInBlockReceivesArgs() {
        assertEquals(7.0, run("""
                {
                    fn add(a, b) { return eval($a + $b); }
                }
                eval(add(3, 4));
                """));
    }

    @Test
    void funcDeclaredInNestedBlockIsCallableAtTopLevel() {
        assertEquals(1.0, run("""
                {
                    {
                        fn inner() { return 1; }
                    }
                }
                eval(inner());
                """));
    }

    @Test
    void blockVarDoesNotLeakButFuncDoes() {
        assertThrows(UndefinedReferenceError.class, () -> run("""
                {
                    var x : 5;
                    fn getX() { return 5; }
                }
                $x;
                """));
    }

    @Test
    void funcInBlockCanCaptureOuterVar() {
        assertEquals(10.0, run("""
                var base : 10;
                {
                    fn getBase() { return eval($base); }
                }
                eval(getBase());
                """));
    }

    @Test
    void multipleFuncsDeclaredInBlock() {
        assertEquals(3.0, run("""
                {
                    fn inc(n) { return eval($n + 1); }
                    fn dec(n) { return eval($n - 1); }
                }
                eval(inc(dec(3)));
                """));
    }

    @Test
    void blockDoesNotLeakVariables() {
        assertThrows(UndefinedReferenceError.class, () -> run("""
                {
                    var ref : 0;
                }
                $ref;
                """));
    }

    @Test
    void blockDoesNotShadowOuterVariable() {
        assertEquals(69.0, run("""
                var ref : 69;
                {
                    var ref : 10;
                }
                eval($ref);
                """));
    }

    @Test
    void blockCanAccessOuterVariable() {
        assertEquals(10.0, run("""
                var x : 10;
                var result;
                {
                    $result : eval($x);
                }
                eval($result);
                """));
    }

    @Test
    void blockCanModifyOuterVariable() {
        assertEquals(20.0, run("""
                var x : 10;
                {
                    $x : 20;
                }
                eval($x);
                """));
    }

    @Test
    void nestedBlocks() {
        assertThrows(UndefinedReferenceError.class, () -> run("""
                {
                    var inner : 5;
                    {
                        var deepInner : 10;
                    }
                    $deepInner;
                }
                """));
    }

    @Test
    void blockWithMultipleStatements() {
        assertEquals(30.0, run("""
                var result : 0;
                {
                    var a : 10;
                    var b : 20;
                    $result : eval($a + $b);
                }
                eval($result);
                """));
    }
}
