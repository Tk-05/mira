package com.mira.lib.std;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class TimeLibTest {

    static Time lib = new Time();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    @Test
    void testNow() {
        NativeFunction fn = (NativeFunction) environment.get("now");
        double result = (double) fn.call(interpreter, List.of());
        assertTrue(result > 0);
    }

    @Test
    void testElapsed() {
        NativeFunction fn = (NativeFunction) environment.get("elapsed");
        double past = (double) System.currentTimeMillis() - 1000;
        double result = (double) fn.call(interpreter, List.of(past));
        assertTrue(result >= 1000);
    }

    @Test
    void testFromSeconds() {
        NativeFunction fn = (NativeFunction) environment.get("fromSeconds");
        assertEquals(5000.0, fn.call(interpreter, List.of(5.0)));
    }

    @Test
    void testFromMinutes() {
        NativeFunction fn = (NativeFunction) environment.get("fromMinutes");
        assertEquals(60000.0, fn.call(interpreter, List.of(1.0)));
    }

    @Test
    void testFromHours() {
        NativeFunction fn = (NativeFunction) environment.get("fromHours");
        assertEquals(3600000.0, fn.call(interpreter, List.of(1.0)));
    }

    @Test
    void testFormatMs() {
        NativeFunction fn = (NativeFunction) environment.get("format");
        assertEquals("500ms", fn.call(interpreter, List.of(500.0)));
    }

    @Test
    void testFormatSeconds() {
        NativeFunction fn = (NativeFunction) environment.get("format");
        assertEquals("30s", fn.call(interpreter, List.of(30000.0)));
    }

    @Test
    void testFormatMinutes() {
        NativeFunction fn = (NativeFunction) environment.get("format");
        assertEquals("2m 30s", fn.call(interpreter, List.of(150000.0)));
    }

    @Test
    void testFormatHours() {
        NativeFunction fn = (NativeFunction) environment.get("format");
        assertEquals("1h 0m 0s", fn.call(interpreter, List.of(3600000.0)));
    }

    @Test
    void testSleep() {
        NativeFunction fn = (NativeFunction) environment.get("sleep");
        long before = System.currentTimeMillis();
        fn.call(interpreter, List.of(50.0));
        long after = System.currentTimeMillis();
        assertTrue(after - before >= 40);
    }
}
