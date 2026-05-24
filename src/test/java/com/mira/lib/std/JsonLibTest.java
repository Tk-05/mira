package com.mira.lib.std;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class JsonLibTest {

    static Json json = new Json();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    static final String FLAT_JSON = "{\"name\":\"Kotlin\",\"version\":1.9,\"stable\":true,\"deprecated\":false,\"alias\":null}";
    static final String MULTILINE_JSON = "{\n  \"name\": \"Kotlin\",\n  \"preis\": 2.5,\n  \"active\": true\n}";
    static final String ARRAY_JSON = "{\"tags\":[\"jvm\",\"kotlin\",\"java\"]}";

    @BeforeAll
    static void setup() {
        json.loadLib(environment);
    }

    private Object call(String name, Object... args) {
        NativeFunction fn = (NativeFunction) environment.get(name);
        return fn.call(interpreter, List.of(args));
    }

    private static DumbExpression wrap(String val) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, val, 0, 0));
    }

    private static ListExpression makeList(String... values) {
        List<com.mira.parser.nodes.expression.Expression> members = new ArrayList<>();
        for (String v : values) members.add(wrap(v));
        return new ListExpression(members);
    }

    @Test
    void testJsonGetString() {
        assertEquals("Kotlin", call("jsonGet", FLAT_JSON, "name"));
    }

    @Test
    void testJsonGetNumber() {
        assertEquals("1.9", call("jsonGet", FLAT_JSON, "version"));
    }

    @Test
    void testJsonGetTrue() {
        assertEquals("true", call("jsonGet", FLAT_JSON, "stable"));
    }

    @Test
    void testJsonGetFalse() {
        assertEquals("false", call("jsonGet", FLAT_JSON, "deprecated"));
    }

    @Test
    void testJsonGetNull() {
        assertEquals("null", call("jsonGet", FLAT_JSON, "alias"));
    }

    @Test
    void testJsonGetMissingKeyReturnsEmpty() {
        assertEquals("", call("jsonGet", FLAT_JSON, "nonexistent"));
    }

    @Test
    void testJsonGetWithMultilineJson() {
        assertEquals("Kotlin", call("jsonGet", MULTILINE_JSON, "name"));
    }

    @Test
    void testJsonGetNumberFromMultilineJson() {
        assertEquals("2.5", call("jsonGet", MULTILINE_JSON, "preis"));
    }

    @Test
    void testJsonGetBooleanFromMultilineJson() {
        assertEquals("true", call("jsonGet", MULTILINE_JSON, "active"));
    }

    @Test
    void testJsonGetEmptyJson() {
        assertEquals("", call("jsonGet", "{}", "name"));
    }

    @Test
    void testJsonHasExistingKey() {
        assertEquals(true, call("jsonHas", FLAT_JSON, "name"));
    }

    @Test
    void testJsonHasMissingKey() {
        assertEquals(false, call("jsonHas", FLAT_JSON, "nonexistent"));
    }

    @Test
    void testJsonHasOnEmptyJson() {
        assertEquals(false, call("jsonHas", "{}", "name"));
    }

    @Test
    void testJsonHasAllKeys() {
        for (String key : List.of("name", "version", "stable", "deprecated", "alias")) {
            assertEquals(true, call("jsonHas", FLAT_JSON, key));
        }
    }

    @Test
    void testJsonArrayReturnsListExpression() {
        assertInstanceOf(ListExpression.class, call("jsonArray", ARRAY_JSON, "tags"));
    }

    @Test
    void testJsonArrayCorrectSize() {
        ListExpression result = (ListExpression) call("jsonArray", ARRAY_JSON, "tags");
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testJsonArrayFirstElement() {
        ListExpression result = (ListExpression) call("jsonArray", ARRAY_JSON, "tags");
        DumbExpression first = (DumbExpression) result.getMembers().get(0);
        assertEquals("jvm", first.getValue());
    }

    @Test
    void testJsonArrayLastElement() {
        ListExpression result = (ListExpression) call("jsonArray", ARRAY_JSON, "tags");
        DumbExpression last = (DumbExpression) result.getMembers().get(2);
        assertEquals("java", last.getValue());
    }

    @Test
    void testJsonArrayMissingKeyReturnsEmptyList() {
        ListExpression result = (ListExpression) call("jsonArray", ARRAY_JSON, "nonexistent");
        assertEquals(0, result.getMembers().size());
    }

    @Test
    void testJsonArrayWithNumbers() {
        String numJson = "{\"values\":[1,2,3]}";
        ListExpression result = (ListExpression) call("jsonArray", numJson, "values");
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testJsonBuildBasic() {
        String result = (String) call("jsonBuild", makeList("name"), makeList("Kotlin"));
        assertEquals("{\"name\":\"Kotlin\"}", result);
    }

    @Test
    void testJsonBuildNumber() {
        String result = (String) call("jsonBuild", makeList("version"), makeList("1.9"));
        assertEquals("{\"version\":1.9}", result);
    }

    @Test
    void testJsonBuildBoolean() {
        String result = (String) call("jsonBuild", makeList("stable"), makeList("true"));
        assertEquals("{\"stable\":true}", result);
    }

    @Test
    void testJsonBuildNull() {
        String result = (String) call("jsonBuild", makeList("alias"), makeList("null"));
        assertEquals("{\"alias\":null}", result);
    }

    @Test
    void testJsonBuildMultipleKeys() {
        String result = (String) call("jsonBuild",
                makeList("name", "version"),
                makeList("Kotlin", "1.9"));
        assertTrue(result.contains("\"name\":\"Kotlin\""));
        assertTrue(result.contains("\"version\":1.9"));
    }

    @Test
    void testJsonBuildMismatchedSizesThrows() {
        assertThrows(RuntimeException.class, () ->
                call("jsonBuild", makeList("a", "b"), makeList("x")));
    }

    @Test
    void testJsonBuildNonListThrows() {
        assertThrows(RuntimeException.class, () ->
                call("jsonBuild", "notAList", makeList("x")));
    }

    @Test
    void testJsonBuildEmptyLists() {
        String result = (String) call("jsonBuild", makeList(), makeList());
        assertEquals("{}", result);
    }

    @Test
    void testJsonFormatReturnsString() {
        assertInstanceOf(String.class, call("jsonFormat", FLAT_JSON));
    }

    @Test
    void testJsonFormatContainsNewlines() {
        String result = (String) call("jsonFormat", FLAT_JSON);
        assertTrue(result.contains("\n"));
    }

    @Test
    void testJsonFormatContainsIndentation() {
        String result = (String) call("jsonFormat", FLAT_JSON);
        assertTrue(result.contains("  "));
    }

    @Test
    void testJsonFormatPreservesKeys() {
        String result = (String) call("jsonFormat", FLAT_JSON);
        assertTrue(result.contains("name"));
        assertTrue(result.contains("version"));
    }

    @Test
    void testJsonFormatPreservesValues() {
        String result = (String) call("jsonFormat", FLAT_JSON);
        assertTrue(result.contains("Kotlin"));
        assertTrue(result.contains("1.9"));
    }

    @Test
    void testJsonFormatEmptyObject() {
        String result = (String) call("jsonFormat", "{}");
        assertNotNull(result);
    }

    static final String NESTED_JSON = "{\"hourly\":{\"time\":[\"08:00\",\"09:00\",\"10:00\"]}}";

    @Test
    void testJsonNestedReturnsListExpression() {
        assertInstanceOf(ListExpression.class, call("jsonNested", NESTED_JSON, "hourly", "time"));
    }

    @Test
    void testJsonNestedCorrectSize() {
        ListExpression result = (ListExpression) call("jsonNested", NESTED_JSON, "hourly", "time");
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testJsonNestedFirstElement() {
        ListExpression result = (ListExpression) call("jsonNested", NESTED_JSON, "hourly", "time");
        assertEquals("08:00", ((DumbExpression) result.getMembers().get(0)).getValue());
    }

    @Test
    void testJsonNestedMissingParentThrows() {
        assertThrows(RuntimeException.class, () -> call("jsonNested", NESTED_JSON, "missing", "time"));
    }

    @Test
    void testJsonIndexOfFound() {
        ListExpression list = makeList("08:00", "09:00", "10:00");
        assertEquals(1.0, call("jsonIndexOf", list, "09:00"));
    }

    @Test
    void testJsonIndexOfNotFound() {
        ListExpression list = makeList("08:00", "09:00");
        assertEquals(-1.0, call("jsonIndexOf", list, "12:00"));
    }

    @Test
    void testJsonIndexOfFirstOccurrence() {
        ListExpression list = makeList("a", "b", "a");
        assertEquals(0.0, call("jsonIndexOf", list, "a"));
    }

    @Test
    void testJsonIndexOfNonListThrows() {
        assertThrows(RuntimeException.class, () -> call("jsonIndexOf", "notAList", "val"));
    }

    @Test
    void testJsonKeysReturnsAllKeys() {
        ListExpression keys = (ListExpression) call("jsonKeys", FLAT_JSON);
        assertEquals(5, keys.getMembers().size());
    }

    @Test
    void testJsonKeysEmptyObject() {
        ListExpression keys = (ListExpression) call("jsonKeys", "{}");
        assertEquals(0, keys.getMembers().size());
    }

    @Test
    void testJsonSizeObject() {
        double size = (double) call("jsonSize", FLAT_JSON);
        assertEquals(5.0, size);
    }

    @Test
    void testJsonSizeEmptyObject() {
        double size = (double) call("jsonSize", "{}");
        assertEquals(0.0, size);
    }

    @Test
    void testJsonSizeArray() {
        double size = (double) call("jsonSize", "[1,2,3]");
        assertEquals(3.0, size);
    }

    @Test
    void testJsonSetExistingKey() {
        String result = (String) call("jsonSet", "{\"name\":\"Alice\"}", "name", "Bob");
        assertTrue(result.contains("\"Bob\""));
    }

    @Test
    void testJsonSetNewKey() {
        String result = (String) call("jsonSet", "{\"a\":1}", "b", "2");
        assertTrue(result.contains("\"b\""));
    }

    @Test
    void testJsonSetNumberValue() {
        String result = (String) call("jsonSet", "{\"x\":1}", "x", 42.0);
        assertTrue(result.contains("42"));
    }
}