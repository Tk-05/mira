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

    @Test
    void testRand() {
        if (environment.get("rand") instanceof NativeFunction nativeFunction) {
            double result = (double) nativeFunction.call(interpreter, List.of());
            assertEquals(true, result >= 0.0 && result < 1.0);
        }
    }

    @Test
    void testRandInt() {
        if (environment.get("randInt") instanceof NativeFunction nativeFunction) {
            double result = (double) nativeFunction.call(interpreter, List.of(1, 10));
            assertEquals(true, result >= 1.0 && result <= 10.0);
        }
    }

    @Test
    void testLog() {
        if (environment.get("log") instanceof NativeFunction nativeFunction) {
            assertEquals(0.0, nativeFunction.call(interpreter, List.of(1)));
        }
    }

    @Test
    void testLog10() {
        if (environment.get("log10") instanceof NativeFunction nativeFunction) {
            assertEquals(2.0, nativeFunction.call(interpreter, List.of(100)));
        }
    }

    @Test
    void testLog2() {
        if (environment.get("log2") instanceof NativeFunction nativeFunction) {
            assertEquals(3.0, nativeFunction.call(interpreter, List.of(8)));
        }
    }

    @Test
    void testTan() {
        if (environment.get("tan") instanceof NativeFunction nativeFunction) {
            assertEquals(0.0, nativeFunction.call(interpreter, List.of(0)));
        }
    }

    @Test
    void testAsin() {
        if (environment.get("asin") instanceof NativeFunction nativeFunction) {
            assertEquals(0.0, nativeFunction.call(interpreter, List.of(0)));
        }
    }

    @Test
    void testAcos() {
        if (environment.get("acos") instanceof NativeFunction nativeFunction) {
            assertEquals(java.lang.Math.PI / 2, nativeFunction.call(interpreter, List.of(0)));
        }
    }

    @Test
    void testAtan() {
        if (environment.get("atan") instanceof NativeFunction nativeFunction) {
            assertEquals(0.0, nativeFunction.call(interpreter, List.of(0)));
        }
    }

    @Test
    void testAtan2() {
        if (environment.get("atan2") instanceof NativeFunction nativeFunction) {
            assertEquals(java.lang.Math.PI / 4, nativeFunction.call(interpreter, List.of(1, 1)));
        }
    }

    @Test
    void testGcd() {
        NativeFunction fn = (NativeFunction) environment.get("gcd");
        assertEquals(4.0, fn.call(interpreter, List.of(12, 8)));
        assertEquals(1.0, fn.call(interpreter, List.of(7, 13)));
    }

    @Test
    void testLcm() {
        NativeFunction fn = (NativeFunction) environment.get("lcm");
        assertEquals(12.0, fn.call(interpreter, List.of(4, 6)));
        assertEquals(0.0, fn.call(interpreter, List.of(0, 5)));
    }

    @Test
    void testFactorial() {
        NativeFunction fn = (NativeFunction) environment.get("factorial");
        assertEquals(120.0, fn.call(interpreter, List.of(5)));
        assertEquals(1.0, fn.call(interpreter, List.of(0)));
    }

    @Test
    void testTrunc() {
        NativeFunction fn = (NativeFunction) environment.get("trunc");
        assertEquals(-3.0, fn.call(interpreter, List.of(-3.9)));
        assertEquals(3.0, fn.call(interpreter, List.of(3.9)));
    }

    @Test
    void testHypot() {
        NativeFunction fn = (NativeFunction) environment.get("hypot");
        assertEquals(5.0, fn.call(interpreter, List.of(3, 4)));
    }
}
