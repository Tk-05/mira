package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class MathLibTest {

    static com.mira.lib.std.Math math = new com.mira.lib.std.Math();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        math.loadLib(environment);
    }

    @Test
    void testConstants() {
        if (environment.get("pi") instanceof Double d) {
            assertEquals(java.lang.Math.PI, d);
        }

        if (environment.get("e") instanceof Double d) {
            assertEquals(java.lang.Math.E, d);
        }
    }

    @Test
    void testPow() {
        if (environment.get("pow") instanceof NativeFunction nativeFunction) {
            Double power = (Double) nativeFunction.call(interpreter, List.of(2,2));
            assertEquals(4, power);
        }
    }

    @Test
    void testMax() {
        if (environment.get("max") instanceof NativeFunction nativeFunction) {
            Double max = (Double) nativeFunction.call(interpreter, List.of(4,2));
            assertEquals(4, max);
        }
    }

    @Test
    void testMin() {
        if (environment.get("min") instanceof NativeFunction nativeFunction) {
            Double min = (Double) nativeFunction.call(interpreter, List.of(4,2));
            assertEquals(2.0, min);
        }
    }

    @Test
    void testAbs() {
        if (environment.get("abs") instanceof NativeFunction nativeFunction) {
            Double abs = (Double) nativeFunction.call(interpreter, List.of(-2));
            assertEquals(2.0, abs);
        }
    }

    @Test
    void testRand() {
        if (environment.get("rand") instanceof NativeFunction nativeFunction) {
            Double rand = (Double) nativeFunction.call(interpreter, List.of(4,2));
            assertNotNull(rand);
        }
    }

    @Test
    void testRound() {
        if (environment.get("round") instanceof NativeFunction nativeFunction) {
            Long round = (Long) nativeFunction.call(interpreter, List.of(1.8));
            assertEquals(2, round);
        }
    }
}
