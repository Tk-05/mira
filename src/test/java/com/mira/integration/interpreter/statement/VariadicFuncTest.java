package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractVariadicFuncTests;

public class VariadicFuncTest extends AbstractVariadicFuncTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void variadicSumSingleArg() {
        assertEquals(42.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn sum(...args) {
                    var total : 0;
                    foreach (var x in $args) {
                        $total : eval($total + $x);
                    }
                    return $total;
                }
                eval(sum(42));
                """)));
    }

    @Test
    void variadicSumNoArgs() {
        assertEquals(0.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn sum(...args) {
                    var total : 0;
                    foreach (var x in $args) {
                        $total : eval($total + $x);
                    }
                    return $total;
                }
                eval(sum());
                """)));
    }

    @Test
    void variadicAccessByIndex() {
        assertEquals("b", backend.runAndGetValue("""
                fn second(...args) { return $args[1]; }
                eval(second("a", "b", "c"));
                """));
    }

    @Test
    void fixedParamBindsCorrectly() {
        assertEquals("hello", backend.runAndGetValue("""
                fn f(prefix, ...rest) { return $prefix; }
                f("hello", 1, 2, 3);
                """));
    }

    @Test
    void fixedPlusVariadicNoRestArgs() {
        assertEquals(0.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                import collection;
                fn f(a, ...rest) { return eval(length($rest)); }
                eval(f(99));
                """)));
    }

    @Test
    void fixedPlusVariadicRestContainsCorrectValues() {
        assertEquals("b", backend.runAndGetValue("""
                fn f(a, ...rest) { return $rest[0]; }
                eval(f("a", "b", "c"));
                """));
    }

    @Test
    void lambdaVariadicEmpty() {
        assertEquals(0.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                import collection;
                var f : fn(...args) { return eval(length($args)); };
                eval(f());
                """)));
    }

    @Test
    void lambdaFixedPlusVariadic() {
        assertEquals(2.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                import collection;
                var f : fn(x, ...rest) { return eval(length($rest)); };
                eval(f(0, 1, 2));
                """)));
    }

    @Test
    void variadicFunctionCalledRecursively() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                fn sum(...args) {
                    var total : 0;
                    foreach (var x in $args) {
                        $total : eval($total + $x);
                    }
                    return $total;
                }
                eval(sum(1, 2, 3, 4));
                """)));
    }
}
