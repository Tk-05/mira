package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class CsvLibTest {

    static Csv lib = new Csv();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    @Test
    void testParse() {
        NativeFunction fn = (NativeFunction) environment.get("parse");
        Object result = fn.call(interpreter, List.of("a,b,c\n1,2,3\n"));
        assertTrue(result instanceof ListExpression);
        ListExpression rows = (ListExpression) result;
        assertEquals(2, rows.getMembers().size());
        assertTrue(rows.getMembers().get(0) instanceof ListExpression);
    }

    @Test
    void testParseWithHeaders() {
        NativeFunction fn = (NativeFunction) environment.get("parseWithHeaders");
        Object result = fn.call(interpreter, List.of("name,age\nAlice,30\nBob,25\n"));
        assertTrue(result instanceof ListExpression);
        ListExpression rows = (ListExpression) result;
        assertEquals(2, rows.getMembers().size());
        assertTrue(rows.getMembers().get(0) instanceof MapExpression);
        MapExpression first = (MapExpression) rows.getMembers().get(0);
        assertTrue(first.getEntries().containsKey("name"));
        assertTrue(first.getEntries().containsKey("age"));
    }

    @Test
    void testStringify() {
        NativeFunction parseFn = (NativeFunction) environment.get("parse");
        NativeFunction fn = (NativeFunction) environment.get("stringify");
        Object parsed = parseFn.call(interpreter, List.of("a,b\n1,2\n"));
        String result = (String) fn.call(interpreter, List.of(parsed));
        assertTrue(result.contains("a") || result.contains("1"));
    }

    @Test
    void testParseRow() {
        NativeFunction fn = (NativeFunction) environment.get("parseRow");
        Object result = fn.call(interpreter, List.of("hello,\"world,!\",foo"));
        assertTrue(result instanceof ListExpression);
        ListExpression row = (ListExpression) result;
        assertEquals(3, row.getMembers().size());
    }

    @Test
    void testRowCount() {
        NativeFunction fn = (NativeFunction) environment.get("rowCount");
        assertEquals(3.0, fn.call(interpreter, List.of("a\nb\nc\n")));
    }

    @Test
    void testColumn() {
        NativeFunction fn = (NativeFunction) environment.get("column");
        NativeFunction parseFn = (NativeFunction) environment.get("parse");
        Object parsed = parseFn.call(interpreter, List.of("a,b\n1,2\n3,4\n"));
        Object result = fn.call(interpreter, List.of(parsed, 0.0));
        assertTrue(result instanceof ListExpression);
        assertEquals(3, ((ListExpression) result).getMembers().size());
    }
}
