package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class DefaultParamTest extends InterpreterTestBase {

    @Test
    void defaultUsedWhenArgOmitted() {
        try {
            run("""
                    fn greet(name, greeting : "Hello") {
                        return $greeting " " $name;
                    }
                    greet("World");
                    """);
        } catch (ReturnSignal r) {
            assertEquals("Hello World", r.getValue());
        }
    }

    @Test
    void explicitArgOverridesDefault() {
        try {
            run("""
                    fn greet(name, greeting : "Hello") {
                        return $greeting " " $name;
                    }
                    greet("World", "Hi");
                    """);
        } catch (ReturnSignal r) {
            assertEquals("Hi World", r.getValue());
        }
    }

    @Test
    void allRequiredArgsStillRequired() {
        assertThrows(RuntimeException.class, () -> run("""
                fn add(a, b : 10) {
                    return eval($a + $b);
                }
                add();
                """));
    }

    @Test
    void defaultIsNumber() {
        try {
            run("""
                    fn increment(x, step : 1) {
                        return eval($x + $step);
                    }
                    increment(5);
                    """);
        } catch (ReturnSignal r) {
            assertEquals(6.0, normNum(r.getValue()));
        }
    }

    @Test
    void defaultIsBoolean() {
        try {
            run("""
                    fn check(x, flag : true) {
                        return $flag;
                    }
                    check(0);
                    """);
        } catch (ReturnSignal r) {
            assertEquals(Boolean.TRUE, r.getValue());
        }
    }

    @Test
    void multipleDefaultParams() {
        try {
            run("""
                    fn box(value, prefix : "[", suffix : "]") {
                        return $prefix $value $suffix;
                    }
                    box("hi");
                    """);
        } catch (ReturnSignal r) {
            assertEquals("[hi]", r.getValue());
        }
    }

    @Test
    void multipleDefaultParamsPartialOverride() {
        try {
            run("""
                    fn box(value, prefix : "[", suffix : "]") {
                        return $prefix $value $suffix;
                    }
                    box("hi", "<");
                    """);
        } catch (ReturnSignal r) {
            assertEquals("<hi]", r.getValue());
        }
    }

    @Test
    void tooManyArgsThrows() {
        assertThrows(RuntimeException.class, () -> run("""
                fn add(a, b : 10) {
                    return eval($a + $b);
                }
                add(1, 2, 3);
                """));
    }

    @Test
    void defaultParamInLambda() {
        try {
            run("""
                    var add : fn(x, step : 1) { return eval($x + $step); };
                    add(5);
                    """);
        } catch (ReturnSignal r) {
            assertEquals(6.0, normNum(r.getValue()));
        }
    }

    @Test
    void defaultParamInLambdaOverridden() {
        try {
            run("""
                    var add : fn(x, step : 1) { return eval($x + $step); };
                    add(5, 10);
                    """);
        } catch (ReturnSignal r) {
            assertEquals(15.0, normNum(r.getValue()));
        }
    }

    @Test
    void defaultExpressionEvaluatedAtCallTime() {
        run("""
                var base : 10;
                fn withBase(x, offset : $base) {
                    return eval($x + $offset);
                }
                $base : 20;
                var result : withBase(5);
                """);
        assertEquals(25.0, normNum(interpreter.getGlobalEnvironment().get("result")));
    }
}
