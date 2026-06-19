package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class NumberLibTest {

    static NumberLib lib = new NumberLib();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    @Test
    void testToFixed() {
        NativeFunction fn = (NativeFunction) environment.get("toFixed");
        assertEquals("3.14", fn.call(interpreter, List.of(3.14159, 2.0)));
        assertEquals("3.1416", fn.call(interpreter, List.of(3.14159, 4.0)));
    }

    @Test
    void testToHex() {
        NativeFunction fn = (NativeFunction) environment.get("toHex");
        assertEquals("FF", fn.call(interpreter, List.of(255.0)));
        assertEquals("1A", fn.call(interpreter, List.of(26.0)));
    }

    @Test
    void testToBinary() {
        NativeFunction fn = (NativeFunction) environment.get("toBinary");
        assertEquals("1010", fn.call(interpreter, List.of(10.0)));
        assertEquals("1111", fn.call(interpreter, List.of(15.0)));
    }

    @Test
    void testToOctal() {
        NativeFunction fn = (NativeFunction) environment.get("toOctal");
        assertEquals("17", fn.call(interpreter, List.of(15.0)));
    }

    @Test
    void testToScientific() {
        NativeFunction fn = (NativeFunction) environment.get("toScientific");
        String result = (String) fn.call(interpreter, List.of(31400.0, 2.0));
        assertTrue(result.contains("e") || result.contains("E"));
    }

    @Test
    void testWithCommas() {
        NativeFunction fn = (NativeFunction) environment.get("withCommas");
        assertEquals("1,234,567", fn.call(interpreter, List.of(1234567.0)));
    }

    @Test
    void testFromHex() {
        NativeFunction fn = (NativeFunction) environment.get("fromHex");
        assertEquals(255.0, fn.call(interpreter, List.of("FF")));
        assertEquals(26.0, fn.call(interpreter, List.of("1a")));
    }

    @Test
    void testFromBinary() {
        NativeFunction fn = (NativeFunction) environment.get("fromBinary");
        assertEquals(10.0, fn.call(interpreter, List.of("1010")));
    }

    @Test
    void testFromOctal() {
        NativeFunction fn = (NativeFunction) environment.get("fromOctal");
        assertEquals(15.0, fn.call(interpreter, List.of("17")));
    }

    @Test
    void testIsInteger() {
        NativeFunction fn = (NativeFunction) environment.get("isInteger");
        assertTrue((Boolean) fn.call(interpreter, List.of(5.0)));
        assertFalse((Boolean) fn.call(interpreter, List.of(5.5)));
    }
}
