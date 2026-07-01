package com.mira.lib.std;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class TermLibTest {

    static Term lib = new Term();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    @Test
    void testRed() {
        NativeFunction fn = (NativeFunction) environment.get("red");
        String result = (String) fn.call(interpreter, List.of("hello"));
        assertTrue(result.contains("hello"));
        assertTrue(result.startsWith("\033["));
        assertTrue(result.endsWith("\033[0m"));
    }

    @Test
    void testGreen() {
        NativeFunction fn = (NativeFunction) environment.get("green");
        String result = (String) fn.call(interpreter, List.of("ok"));
        assertTrue(result.contains("\033[32m"));
    }

    @Test
    void testBold() {
        NativeFunction fn = (NativeFunction) environment.get("bold");
        String result = (String) fn.call(interpreter, List.of("title"));
        assertTrue(result.contains("\033[1m"));
    }

    @Test
    void testStripAnsi() {
        NativeFunction fn = (NativeFunction) environment.get("stripAnsi");
        String colored = "\033[31mhello\033[0m";
        assertEquals("hello", fn.call(interpreter, List.of(colored)));
    }

    @Test
    void testClear() {
        NativeFunction fn = (NativeFunction) environment.get("clear");
        String result = (String) fn.call(interpreter, List.of());
        assertTrue(result.contains("\033[2J"));
    }

    @Test
    void testAllColors() {
        for (String color : List.of("yellow", "blue", "magenta", "cyan", "white")) {
            NativeFunction fn = (NativeFunction) environment.get(color);
            String result = (String) fn.call(interpreter, List.of("x"));
            assertTrue(result.contains("x"), color + " should contain text");
            assertTrue(result.startsWith("\033["), color + " should start with ANSI escape");
        }
    }

    @Test
    void testStyles() {
        for (String style : List.of("dim", "italic", "underline")) {
            NativeFunction fn = (NativeFunction) environment.get(style);
            String result = (String) fn.call(interpreter, List.of("x"));
            assertTrue(result.contains("\033["), style + " should contain ANSI escape");
        }
    }
}
