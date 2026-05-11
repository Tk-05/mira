package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
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
            ListExpression split = (ListExpression) nativeFunction.call(interpreter, List.of("ABC ABC", " "));
            assertEquals("ABC", ((DumbExpression) split.getMembers().get(0)).getValue());
            assertEquals("ABC", ((DumbExpression) split.getMembers().get(1)).getValue());
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

    @Test
    void testUpper() {
        NativeFunction fn = (NativeFunction) environment.get("upper");
        assertEquals("HELLO", fn.call(interpreter, List.of("hello")));
    }

    @Test
    void testLower() {
        NativeFunction fn = (NativeFunction) environment.get("lower");
        assertEquals("hello", fn.call(interpreter, List.of("HELLO")));
    }

    @Test
    void testStartsWith() {
        NativeFunction fn = (NativeFunction) environment.get("startsWith");
        assertEquals(true, fn.call(interpreter, List.of("hello", "he")));
        assertEquals(false, fn.call(interpreter, List.of("hello", "lo")));
    }

    @Test
    void testEndsWith() {
        NativeFunction fn = (NativeFunction) environment.get("endsWith");
        assertEquals(true, fn.call(interpreter, List.of("hello", "lo")));
        assertEquals(false, fn.call(interpreter, List.of("hello", "he")));
    }

    @Test
    void testContains() {
        NativeFunction fn = (NativeFunction) environment.get("contains");
        assertEquals(true, fn.call(interpreter, List.of("hello world", "world")));
        assertEquals(false, fn.call(interpreter, List.of("hello", "xyz")));
    }

    @Test
    void testRepeat() {
        NativeFunction fn = (NativeFunction) environment.get("repeat");
        assertEquals("abcabc", fn.call(interpreter, List.of("abc", "2")));
        assertEquals("", fn.call(interpreter, List.of("abc", "0")));
    }

    @Test
    void testToNumber() {
        NativeFunction fn = (NativeFunction) environment.get("toNumber");
        assertEquals(42.0, fn.call(interpreter, List.of("42")));
        assertEquals(3.14, fn.call(interpreter, List.of("3.14")));
    }

    @Test
    void testPadLeft() {
        NativeFunction fn = (NativeFunction) environment.get("padLeft");
        assertEquals("  hi", fn.call(interpreter, List.of("hi", "4")));
        assertEquals("hi", fn.call(interpreter, List.of("hi", "1")));
    }

    @Test
    void testPadRight() {
        NativeFunction fn = (NativeFunction) environment.get("padRight");
        assertEquals("hi  ", fn.call(interpreter, List.of("hi", "4")));
        assertEquals("hi", fn.call(interpreter, List.of("hi", "1")));
    }

    @Test
    void testIsNumeric() {
        NativeFunction fn = (NativeFunction) environment.get("isNumeric");
        assertEquals(true, fn.call(interpreter, List.of("42")));
        assertEquals(true, fn.call(interpreter, List.of("3.14")));
        assertEquals(false, fn.call(interpreter, List.of("abc")));
        assertEquals(false, fn.call(interpreter, List.of("")));
    }
}
