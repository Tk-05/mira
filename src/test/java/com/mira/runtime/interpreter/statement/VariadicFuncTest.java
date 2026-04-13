package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.mira.runtime.interpreter.InterpreterTestBase;

public class VariadicFuncTest extends InterpreterTestBase {

    @Test
    void variadicOnlyParamNoArgs() {
        assertNull(run("fn f(...args) {} f();"));
    }

    @Test
    void variadicOnlyParamWithArgs() {
        assertNull(run("fn f(...args) {} f(1, 2, 3);"));
    }

    @Test
    void variadicArgsIsListExpression() {
        assertEquals(3.0, run("""
                import collection;
                fn f(...args) { return eval(size($args)); }
                eval(f(10, 20, 30));
                """));
    }

    @Test
    void variadicEmptyArgsIsEmptyList() {
        assertEquals(0.0, run("""
                import collection;
                fn f(...args) { return eval(length($args)); }
                eval(f());
                """));
    }

    @Test
    void variadicSumAllArgs() {
        assertEquals(6.0, run("""
                fn sum(...args) {
                    var total : 0;
                    foreach (var x in $args) {
                        $total : eval($total + $x);
                    }
                    return $total;
                }
                eval(sum(1, 2, 3));
                """));
    }

    @Test
    void variadicSumSingleArg() {
        assertEquals(42.0, run("""
                fn sum(...args) {
                    var total : 0;
                    foreach (var x in $args) {
                        $total : eval($total + $x);
                    }
                    return $total;
                }
                eval(sum(42));
                """));
    }

    @Test
    void variadicSumNoArgs() {
        assertEquals(0.0, run("""
                fn sum(...args) {
                    var total : 0;
                    foreach (var x in $args) {
                        $total : eval($total + $x);
                    }
                    return $total;
                }
                eval(sum());
                """));
    }

    @Test
    void variadicAccessByIndex() {
        assertEquals("b", run("""
                fn second(...args) { return $args[1]; }
                eval(second("a", "b", "c"));
                """));
    }

    @Test
    void fixedPlusVariadic() {
        assertEquals(3.0, run("""
                import collection;
                fn f(a, ...rest) { return eval(length($rest)); }
                eval(f(1, 2, 3, 4));
                """));
    }

    @Test
    void fixedParamBindsCorrectly() {
        assertEquals("hello", run("""
                fn f(prefix, ...rest) { return $prefix; }
                f("hello", 1, 2, 3);
                """));
    }

    @Test
    void fixedPlusVariadicNoRestArgs() {
        assertEquals(0.0, run("""
                import collection;
                fn f(a, ...rest) { return eval(length($rest)); }
                eval(f(99));
                """));
    }

    @Test
    void fixedPlusVariadicRestContainsCorrectValues() {
        assertEquals("b", run("""
                fn f(a, ...rest) { return $rest[0]; }
                eval(f("a", "b", "c"));
                """));
    }

    @Test
    void lambdaVariadic() {
        assertEquals(3.0, run("""
                import collection;
                var f : fn(...args) { return eval(length($args)); };
                eval(f(1, 2, 3));
                """));
    }

    @Test
    void lambdaVariadicEmpty() {
        assertEquals(0.0, run("""
                import collection;
                var f : fn(...args) { return eval(length($args)); };
                eval(f());
                """));
    }

    @Test
    void lambdaFixedPlusVariadic() {
        assertEquals(2.0, run("""
                import collection;
                var f : fn(x, ...rest) { return eval(length($rest)); };
                eval(f(0, 1, 2));
                """));
    }

    @Test
    void variadicListPassedToCollectionFunctions() {
        assertEquals(1.0, run("""
                import collection as col;
                fn first(...args) { return $args; }
                eval(first(1, 2, 3)[0]);
                """));
    }

    @Test
    void variadicFunctionCalledRecursively() {
        assertEquals(10.0, run("""
                fn sum(...args) {
                    var total : 0;
                    foreach (var x in $args) {
                        $total : eval($total + $x);
                    }
                    return $total;
                }
                eval(sum(1, 2, 3, 4));
                """));
    }
}
