package com.mira.lib.std;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class TomlLibTest {

    static Toml lib = new Toml();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    private static final String SAMPLE = "name = \"Mira\"\nversion = \"1.0\"\n";

    @Test
    void testParse() {
        NativeFunction fn = (NativeFunction) environment.get("parse");
        Object result = fn.call(interpreter, List.of(SAMPLE));
        assertTrue(result instanceof MapExpression);
        MapExpression map = (MapExpression) result;
        assertTrue(map.getEntries().containsKey("name"));
        assertTrue(map.getEntries().containsKey("version"));
    }

    @Test
    void testGet() {
        NativeFunction parseFn = (NativeFunction) environment.get("parse");
        NativeFunction fn = (NativeFunction) environment.get("get");
        Object map = parseFn.call(interpreter, List.of(SAMPLE));
        Object val = fn.call(interpreter, List.of(map, "name"));
        assertEquals("Mira", val.toString());
    }

    @Test
    void testHas() {
        NativeFunction parseFn = (NativeFunction) environment.get("parse");
        NativeFunction fn = (NativeFunction) environment.get("has");
        Object map = parseFn.call(interpreter, List.of(SAMPLE));
        assertTrue((Boolean) fn.call(interpreter, List.of(map, "name")));
        assertFalse((Boolean) fn.call(interpreter, List.of(map, "missing")));
    }

    @Test
    void testGetArray() {
        NativeFunction parseFn = (NativeFunction) environment.get("parse");
        NativeFunction fn = (NativeFunction) environment.get("getArray");
        Object map = parseFn.call(interpreter, List.of(SAMPLE));
        Object result = fn.call(interpreter, List.of(map, "missing"));
        assertNotNull(result);
    }

    private static void assertNotNull(Object o) {
        assertTrue(o != null);
    }
}
