package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.InterpreterTestBase;
import com.mira.runtime.interpreter.NullValue;

public class DestructuringTest extends InterpreterTestBase {

    @Test
    void destructuresListIntoVariables() {
        run("""
                var t : {10, 20, 30};
                var (a, b, c) : $t;
                """);
        assertEquals(10.0, normNum(interpreter.getGlobalEnvironment().get("a")));
        assertEquals(20.0, normNum(interpreter.getGlobalEnvironment().get("b")));
        assertEquals(30.0, normNum(interpreter.getGlobalEnvironment().get("c")));
    }

    @Test
    void destructuresList() {
        run("""
                var (x, y) : {1, 2};
                """);
        assertEquals(1.0, normNum(interpreter.getGlobalEnvironment().get("x")));
        assertEquals(2.0, normNum(interpreter.getGlobalEnvironment().get("y")));
    }

    @Test
    void destructuresArray() {
        run("""
                var (p, q) : [3, 4];
                """);
        assertEquals(3.0, normNum(interpreter.getGlobalEnvironment().get("p")));
        assertEquals(4.0, normNum(interpreter.getGlobalEnvironment().get("q")));
    }

    @Test
    void destructuresSingleElement() {
        run("""
                var (only,) : {99};
                """);
        assertEquals(99.0, normNum(interpreter.getGlobalEnvironment().get("only")));
    }

    @Test
    void fewerNamesThanElementsIgnoresRest() {
        run("""
                var (a, b) : {1, 2, 3, 4};
                """);
        assertEquals(1.0, normNum(interpreter.getGlobalEnvironment().get("a")));
        assertEquals(2.0, normNum(interpreter.getGlobalEnvironment().get("b")));
    }

    @Test
    void moreNamesThanElementsBindsNullToExtras() {
        run("""
                var (a, b, c) : {1, 2};
                """);
        assertEquals(1.0, normNum(interpreter.getGlobalEnvironment().get("a")));
        assertEquals(2.0, normNum(interpreter.getGlobalEnvironment().get("b")));
        assertEquals(NullValue.INSTANCE, interpreter.getGlobalEnvironment().get("c"));
    }

    @Test
    void destructuresStrings() {
        run("""
                var (first, second) : {"hello", "world"};
                """);
        assertEquals("hello", interpreter.getGlobalEnvironment().get("first"));
        assertEquals("world", interpreter.getGlobalEnvironment().get("second"));
    }

    @Test
    void destructuresInLocalScope() {
        run("""
                var result : 0;
                fn test() {
                    var (a, b) : {3, 7};
                    $result : eval($a + $b);
                }
                test();
                """);
        assertEquals(10.0, normNum(interpreter.getGlobalEnvironment().get("result")));
    }

    @Test
    void destructuresComputedExpression() {
        run("""
                var x : 5;
                var (a, b) : {eval($x * 2), eval($x * 3)};
                """);
        assertEquals(10.0, normNum(interpreter.getGlobalEnvironment().get("a")));
        assertEquals(15.0, normNum(interpreter.getGlobalEnvironment().get("b")));
    }

    @Test
    void throwsOnNonDestructurableValue() {
        assertThrows(RuntimeException.class, () -> run("""
                var (a, b) : "not a collection";
                """));
    }
}
