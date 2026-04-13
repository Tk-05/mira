package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class StringLibTest {

    static com.mira.lib.std.Strings strings = new com.mira.lib.std.Strings();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        strings.loadLib(environment);
    }

    @Test
    void testCharAt() {
        if (environment.get("charAt") instanceof NativeFunction nativeFunction) {
            char ch = (char) nativeFunction.call(interpreter, List.of("ABC", 0));
            assertEquals('A', ch);
        }
    }

    @Test
    void testIndexOf() {
        if (environment.get("indexOf") instanceof NativeFunction nativeFunction) {
            int index = (int) nativeFunction.call(interpreter, List.of("ABC", 'A'));
            assertEquals(0, index);
        }
    }

    @Test
    void testTrim() {
        if (environment.get("trim") instanceof NativeFunction nativeFunction) {
            String trim = (String) nativeFunction.call(interpreter, List.of("ABC "));
            assertEquals("ABC", trim);
        }
    }

    @Test
    void testSplit() {
        if (environment.get("split") instanceof NativeFunction nativeFunction) {
            String[] split = (String[]) nativeFunction.call(interpreter, List.of("ABC ABC", " "));
            assertEquals("ABC", split[0]);
            assertEquals("ABC", split[1]);
        }
    }

    @Test
    void testSubstr() {
        if (environment.get("substr") instanceof NativeFunction nativeFunction) {
            String result = (String) nativeFunction.call(interpreter, List.of("ABC", 0, 1));
            assertEquals("A", result);
        }
    }

    @Test
    void testStrEqual() {
        if (environment.get("strEqual") instanceof NativeFunction nativeFunction) {
            boolean result = (boolean) nativeFunction.call(interpreter, List.of("ABC", "ABC"));
            assertEquals(true, result);
        }
    }

    @Test
    void testReplace() {
        if (environment.get("replace") instanceof NativeFunction nativeFunction) {
            String result = (String) nativeFunction.call(interpreter, List.of("hello", 'l', 'r'));
            assertEquals("herro", result);
        }
    }
}
