package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        if (environment.get("inf") instanceof Double d) {
            assertEquals(Double.POSITIVE_INFINITY, d);
        }

        if (environment.get("nan") instanceof Double d) {
            assertEquals(Double.NaN, d);
        }
    }

    @Test
    void testFunctions() {
        if (environment.get("pow") instanceof NativeFunction nativeFunction) {
            assertEquals(4.0, nativeFunction.call(interpreter, List.of(2, 2)));
        }

        if (environment.get("max") instanceof NativeFunction nativeFunction) {
            assertEquals(5.0, nativeFunction.call(interpreter, List.of(3, 5)));
        }

        if (environment.get("min") instanceof NativeFunction nativeFunction) {
            assertEquals(3.0, nativeFunction.call(interpreter, List.of(3, 5)));
        }

        if (environment.get("abs") instanceof NativeFunction nativeFunction) {
            assertEquals(5.0, nativeFunction.call(interpreter, List.of(-5)));
        }

        if (environment.get("round") instanceof NativeFunction nativeFunction) {
            assertEquals(4.0, nativeFunction.call(interpreter, List.of(3.6)));
        }

        if (environment.get("floor") instanceof NativeFunction nativeFunction) {
            assertEquals(3.0, nativeFunction.call(interpreter, List.of(3.9)));
        }

        if (environment.get("ceil") instanceof NativeFunction nativeFunction) {
            assertEquals(4.0, nativeFunction.call(interpreter, List.of(3.1)));
        }

        if (environment.get("sqrt") instanceof NativeFunction nativeFunction) {
            assertEquals(3.0, nativeFunction.call(interpreter, List.of(9)));
        }

        if (environment.get("cbrt") instanceof NativeFunction nativeFunction) {
            assertEquals(2.0, nativeFunction.call(interpreter, List.of(8)));
        }

        if (environment.get("sin") instanceof NativeFunction nativeFunction) {
            assertEquals(0.0, nativeFunction.call(interpreter, List.of(0)));
        }

        if (environment.get("cos") instanceof NativeFunction nativeFunction) {
            assertEquals(1.0, nativeFunction.call(interpreter, List.of(0)));
        }

        if (environment.get("toRad") instanceof NativeFunction nativeFunction) {
            assertEquals(java.lang.Math.PI, nativeFunction.call(interpreter, List.of(180)));
        }

        if (environment.get("toDeg") instanceof NativeFunction nativeFunction) {
            assertEquals(180.0, nativeFunction.call(interpreter, List.of(java.lang.Math.PI)));
        }

        if (environment.get("sign") instanceof NativeFunction nativeFunction) {
            assertEquals(1.0, nativeFunction.call(interpreter, List.of(5)));
            assertEquals(-1.0, nativeFunction.call(interpreter, List.of(-5)));
        }

        if (environment.get("clamp") instanceof NativeFunction nativeFunction) {
            assertEquals(5.0, nativeFunction.call(interpreter, List.of(10, 0, 5)));
            assertEquals(5.0, nativeFunction.call(interpreter, List.of(5, 0, 10)));
        }

        if (environment.get("isNaN") instanceof NativeFunction nativeFunction) {
            assertEquals(true, nativeFunction.call(interpreter, List.of(Double.NaN)));
            assertEquals(false, nativeFunction.call(interpreter, List.of(5.0)));
        }

        if (environment.get("isInf") instanceof NativeFunction nativeFunction) {
            assertEquals(true, nativeFunction.call(interpreter, List.of(Double.POSITIVE_INFINITY)));
            assertEquals(false, nativeFunction.call(interpreter, List.of(5.0)));
        }
    }
}
