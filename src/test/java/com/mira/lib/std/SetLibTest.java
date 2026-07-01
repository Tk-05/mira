package com.mira.lib.std;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class SetLibTest {

    static SetLib lib = new SetLib();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    private ListExpression emptySet() {
        NativeFunction fn = (NativeFunction) environment.get("newSet");
        return (ListExpression) fn.call(interpreter, List.of());
    }

    private ListExpression addAll(String... values) {
        NativeFunction addFn = (NativeFunction) environment.get("add");
        ListExpression set = emptySet();
        for (String v : values) {
            set = (ListExpression) addFn.call(interpreter, List.of(set, v));
        }
        return set;
    }

    @Test
    void testNewSet() {
        ListExpression set = emptySet();
        assertEquals(0, set.getMembers().size());
    }

    @Test
    void testAdd() {
        NativeFunction fn = (NativeFunction) environment.get("add");
        ListExpression set = emptySet();
        Object result = fn.call(interpreter, List.of(set, "hello"));
        assertTrue(result instanceof ListExpression);
        assertEquals(1, ((ListExpression) result).getMembers().size());
    }

    @Test
    void testNoDuplicates() {
        ListExpression set = addAll("a", "b", "a", "c", "b");
        NativeFunction sizeFn = (NativeFunction) environment.get("size");
        assertEquals(3.0, sizeFn.call(interpreter, List.of(set)));
    }

    @Test
    void testHas() {
        NativeFunction fn = (NativeFunction) environment.get("has");
        ListExpression set = addAll("x", "y");
        assertTrue((Boolean) fn.call(interpreter, List.of(set, "x")));
        assertFalse((Boolean) fn.call(interpreter, List.of(set, "z")));
    }

    @Test
    void testRemove() {
        NativeFunction fn = (NativeFunction) environment.get("remove");
        NativeFunction hasFn = (NativeFunction) environment.get("has");
        ListExpression set = addAll("a", "b", "c");
        ListExpression result = (ListExpression) fn.call(interpreter, List.of(set, "b"));
        assertFalse((Boolean) hasFn.call(interpreter, List.of(result, "b")));
        assertTrue((Boolean) hasFn.call(interpreter, List.of(result, "a")));
    }

    @Test
    void testUnion() {
        NativeFunction fn = (NativeFunction) environment.get("union");
        NativeFunction sizeFn = (NativeFunction) environment.get("size");
        ListExpression s1 = addAll("a", "b");
        ListExpression s2 = addAll("b", "c");
        Object result = fn.call(interpreter, List.of(s1, s2));
        assertEquals(3.0, sizeFn.call(interpreter, List.of(result)));
    }

    @Test
    void testIntersection() {
        NativeFunction fn = (NativeFunction) environment.get("intersection");
        NativeFunction sizeFn = (NativeFunction) environment.get("size");
        ListExpression s1 = addAll("a", "b", "c");
        ListExpression s2 = addAll("b", "c", "d");
        Object result = fn.call(interpreter, List.of(s1, s2));
        assertEquals(2.0, sizeFn.call(interpreter, List.of(result)));
    }

    @Test
    void testDifference() {
        NativeFunction fn = (NativeFunction) environment.get("difference");
        NativeFunction sizeFn = (NativeFunction) environment.get("size");
        ListExpression s1 = addAll("a", "b", "c");
        ListExpression s2 = addAll("b", "c");
        Object result = fn.call(interpreter, List.of(s1, s2));
        assertEquals(1.0, sizeFn.call(interpreter, List.of(result)));
    }

    @Test
    void testFromList() {
        NativeFunction fn = (NativeFunction) environment.get("fromList");
        NativeFunction sizeFn = (NativeFunction) environment.get("size");
        ListExpression set = addAll("a", "a", "b", "b", "c");
        Object result = fn.call(interpreter, List.of(set));
        assertEquals(3.0, sizeFn.call(interpreter, List.of(result)));
    }
}
