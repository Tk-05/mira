package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class PathLibTest {

    static PathLib lib = new PathLib();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    @Test
    void testJoin() {
        NativeFunction fn = (NativeFunction) environment.get("join");
        String result = (String) fn.call(interpreter, List.of("foo", "bar", "baz.txt"));
        assertTrue(result.contains("foo"));
        assertTrue(result.contains("bar"));
        assertTrue(result.contains("baz.txt"));
    }

    @Test
    void testNormalize() {
        NativeFunction fn = (NativeFunction) environment.get("normalize");
        String result = (String) fn.call(interpreter, List.of("foo/../bar"));
        assertEquals("bar", result);
    }

    @Test
    void testFileName() {
        NativeFunction fn = (NativeFunction) environment.get("fileName");
        assertEquals("file.txt", fn.call(interpreter, List.of("/some/path/file.txt")));
    }

    @Test
    void testStem() {
        NativeFunction fn = (NativeFunction) environment.get("stem");
        assertEquals("file", fn.call(interpreter, List.of("/some/path/file.txt")));
    }

    @Test
    void testExtension() {
        NativeFunction fn = (NativeFunction) environment.get("extension");
        assertEquals("txt", fn.call(interpreter, List.of("/some/path/file.txt")));
        assertEquals("", fn.call(interpreter, List.of("/some/path/noext")));
    }

    @Test
    void testParent() {
        NativeFunction fn = (NativeFunction) environment.get("parent");
        String result = (String) fn.call(interpreter, List.of("/some/path/file.txt"));
        assertTrue(result.endsWith("path") || result.endsWith("path/") || result.contains("path"));
    }

    @Test
    void testIsAbsolute() {
        NativeFunction fn = (NativeFunction) environment.get("isAbsolute");
        assertFalse((Boolean) fn.call(interpreter, List.of("relative/path")));
    }

    @Test
    void testSplit() {
        NativeFunction fn = (NativeFunction) environment.get("split");
        Object result = fn.call(interpreter, List.of("foo/bar/baz"));
        assertTrue(result instanceof ListExpression);
        assertEquals(3, ((ListExpression) result).getMembers().size());
    }
}
