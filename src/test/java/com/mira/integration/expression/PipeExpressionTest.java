package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.integration.InterpreterTestBase;

public class PipeExpressionTest extends InterpreterTestBase {

    @Test
    void pipeToFunctionNoArgs() {
        assertEquals(10.0, normNum(run("""
                fn double(x) { return eval($x * 2); }
                5 |> double();
                """)));
    }

    @Test
    void pipeToFunctionWithExtraArg() {
        assertEquals(10.0, normNum(run("""
                fn add(a, b) { return eval($a + $b); }
                3 |> add(7);
                """)));
    }

    @Test
    void chainedPipe() {
        assertEquals(8.0, normNum(run("""
                fn double(x) { return eval($x * 2); }
                2 |> double() |> double();
                """)));
    }

    @Test
    void pipeWithComplexLeftSide() {
        assertEquals(5.0, normNum(run("""
                fn id(x) { return $x; }
                eval(2 + 3) |> id();
                """)));
    }

    @Test
    void pipeToStoredLambda() {
        assertEquals(15.0, normNum(run("""
                var triple : fn(x) { return eval($x * 3); };
                5 |> triple();
                """)));
    }
}
