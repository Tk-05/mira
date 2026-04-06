package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class BlockTest extends InterpreterTestBase {

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
