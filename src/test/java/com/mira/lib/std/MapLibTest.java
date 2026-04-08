package com.mira.lib.std;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class MapLibTest {

    static Map mapLib = new Map();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        mapLib.loadLib(environment);
    }

    private static DumbExpression wrap(String val) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, val, 0, 0));
    }

    private static MapExpression makeMap(String... keysAndValues) {
        LinkedHashMap<String, com.mira.parser.nodes.expression.Expression> entries = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            entries.put(keysAndValues[i], wrap(keysAndValues[i + 1]));
        }
        return new MapExpression(entries);
    }

    private Object call(String name, Object... args) {
        NativeFunction fn = (NativeFunction) environment.get(name);
        return fn.call(interpreter, List.of(args));
    }

    // newMap

    @Test
    void newMapReturnsEmptyMap() {
        Object result = call("newMap");
        assertInstanceOf(MapExpression.class, result);
        assertEquals(0, ((MapExpression) result).getEntries().size());
    }

    @Test
    void newMapIsMutable() {
        MapExpression m = (MapExpression) call("newMap");
        m.getEntries().put("key", wrap("val"));
        assertEquals(1, m.getEntries().size());
    }

    // mapSize

    @Test
    void mapSizeEmpty() {
        assertEquals(0.0, call("mapSize", makeMap()));
    }

    @Test
    void mapSizeOneEntry() {
        assertEquals(1.0, call("mapSize", makeMap("a", "1")));
    }

    @Test
    void mapSizeMultipleEntries() {
        assertEquals(3.0, call("mapSize", makeMap("a", "1", "b", "2", "c", "3")));
    }

    @Test
    void mapSizeOnNonMapThrows() {
        assertThrows(RuntimeException.class, () -> call("mapSize", "not a map"));
    }

    // mapHas

    @Test
    void mapHasExistingKey() {
        assertEquals(true, call("mapHas", makeMap("name", "Alice"), "name"));
    }

    @Test
    void mapHasMissingKey() {
        assertEquals(false, call("mapHas", makeMap("name", "Alice"), "age"));
    }

    @Test
    void mapHasOnEmptyMap() {
        assertEquals(false, call("mapHas", makeMap(), "key"));
    }

    // mapRemove

    @Test
    void mapRemoveDecreasesSize() {
        MapExpression m = makeMap("a", "1", "b", "2");
        call("mapRemove", m, "a");
        assertEquals(1, m.getEntries().size());
    }

    @Test
    void mapRemoveDeletesKey() {
        MapExpression m = makeMap("a", "1", "b", "2");
        call("mapRemove", m, "a");
        assertEquals(false, m.getEntries().containsKey("a"));
    }

    @Test
    void mapRemoveReturnsMap() {
        MapExpression m = makeMap("a", "1");
        Object result = call("mapRemove", m, "a");
        assertInstanceOf(MapExpression.class, result);
    }

    @Test
    void mapRemoveNonExistentKeyIsNoOp() {
        MapExpression m = makeMap("a", "1");
        call("mapRemove", m, "missing");
        assertEquals(1, m.getEntries().size());
    }

    // mapKeys

    @Test
    void mapKeysReturnsList() {
        Object result = call("mapKeys", makeMap("x", "1"));
        assertInstanceOf(ListExpression.class, result);
    }

    @Test
    void mapKeysContainsAllKeys() {
        ListExpression keys = (ListExpression) call("mapKeys", makeMap("a", "1", "b", "2", "c", "3"));
        assertEquals(3, keys.getMembers().size());
    }

    @Test
    void mapKeysPreservesInsertionOrder() {
        ListExpression keys = (ListExpression) call("mapKeys", makeMap("first", "1", "second", "2"));
        assertEquals("first", ((DumbExpression) keys.getMembers().get(0)).getValue());
        assertEquals("second", ((DumbExpression) keys.getMembers().get(1)).getValue());
    }

    @Test
    void mapKeysEmptyMap() {
        ListExpression keys = (ListExpression) call("mapKeys", makeMap());
        assertEquals(0, keys.getMembers().size());
    }

    // mapValues

    @Test
    void mapValuesReturnsList() {
        Object result = call("mapValues", makeMap("k", "v"));
        assertInstanceOf(ListExpression.class, result);
    }

    @Test
    void mapValuesContainsAllValues() {
        ListExpression values = (ListExpression) call("mapValues", makeMap("a", "1", "b", "2"));
        assertEquals(2, values.getMembers().size());
    }

    @Test
    void mapValuesPreservesInsertionOrder() {
        ListExpression values = (ListExpression) call("mapValues", makeMap("a", "hello", "b", "world"));
        assertEquals("hello", ((DumbExpression) values.getMembers().get(0)).getValue());
        assertEquals("world", ((DumbExpression) values.getMembers().get(1)).getValue());
    }

    @Test
    void mapValuesEmptyMap() {
        ListExpression values = (ListExpression) call("mapValues", makeMap());
        assertEquals(0, values.getMembers().size());
    }
}
