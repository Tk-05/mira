package com.mira.lib.std;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class UrlLibTest {

    static Url lib = new Url();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    @Test
    void testParse() {
        NativeFunction fn = (NativeFunction) environment.get("parse");
        Object result = fn.call(interpreter, List.of("https://example.com:8080/path?key=value#frag"));
        assertTrue(result instanceof MapExpression);
        MapExpression map = (MapExpression) result;
        assertTrue(map.getEntries().containsKey("scheme"));
        assertTrue(map.getEntries().containsKey("host"));
        assertTrue(map.getEntries().containsKey("port"));
        assertTrue(map.getEntries().containsKey("path"));
        assertTrue(map.getEntries().containsKey("query"));
    }

    @Test
    void testEncodeDecode() {
        NativeFunction encode = (NativeFunction) environment.get("encode");
        NativeFunction decode = (NativeFunction) environment.get("decode");
        String original = "hello world & more";
        String encoded = (String) encode.call(interpreter, List.of(original));
        assertTrue(encoded.contains("%"));
        String decoded = (String) decode.call(interpreter, List.of(encoded));
        assertEquals(original, decoded);
    }

    @Test
    void testGetParam() {
        NativeFunction fn = (NativeFunction) environment.get("getParam");
        Object result = fn.call(interpreter, List.of("https://example.com?name=Alice&age=30", "name"));
        assertEquals("Alice", result.toString());
    }

    @Test
    void testGetParams() {
        NativeFunction fn = (NativeFunction) environment.get("getParams");
        Object result = fn.call(interpreter, List.of("https://example.com?a=1&b=2"));
        assertTrue(result instanceof MapExpression);
        MapExpression map = (MapExpression) result;
        assertTrue(map.getEntries().containsKey("a"));
        assertTrue(map.getEntries().containsKey("b"));
    }

    @Test
    void testIsValid() {
        NativeFunction fn = (NativeFunction) environment.get("isValid");
        assertTrue((Boolean) fn.call(interpreter, List.of("https://example.com")));
    }

    @Test
    void testBuild() {
        NativeFunction fn = (NativeFunction) environment.get("build");
        String result = (String) fn.call(interpreter, List.of("https", "example.com", "/api", "key=val"));
        assertTrue(result.startsWith("https://example.com/api"));
        assertTrue(result.contains("key=val"));
    }
}
