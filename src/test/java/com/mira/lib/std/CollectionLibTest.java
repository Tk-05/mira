package com.mira.lib.std;

import java.util.ArrayList;
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
import com.mira.parser.nodes.expression.Expression.TupleExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class CollectionLibTest {

    static Collection collection = new Collection();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        collection.loadLib(environment);
    }

    private static DumbExpression wrap(String val) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, val, 0, 0));
    }

    private static ListExpression makeList(String... values) {
        List<com.mira.parser.nodes.expression.Expression> members = new ArrayList<>();
        for (String v : values) {
            members.add(wrap(v));
        }
        return new ListExpression(members);
    }

    private static TupleExpression makeTuple(String... values) {
        List<com.mira.parser.nodes.expression.Expression> members = new ArrayList<>();
        for (String v : values) {
            members.add(wrap(v));
        }
        return new TupleExpression(members);
    }

    private Object call(String name, Object... args) {
        NativeFunction fn = (NativeFunction) environment.get(name);
        return fn.call(interpreter, List.of(args));
    }

    @Test
    void testSizeOnList() {
        assertEquals(3.0, call("size", makeList("1", "2", "3")));
    }

    @Test
    void testSizeOnTuple() {
        assertEquals(2.0, call("size", makeTuple("a", "b")));
    }

    @Test
    void testSizeOnEmptyList() {
        assertEquals(0.0, call("size", makeList()));
    }

    @Test
    void testPushAddsElement() {
        ListExpression list = makeList("1", "2");
        call("push", list, "3");
        assertEquals(3, list.getMembers().size());
    }

    @Test
    void testPushReturnsUpdatedList() {
        ListExpression list = makeList("a");
        Object result = call("push", list, "b");
        assertInstanceOf(ListExpression.class, result);
        assertEquals(2, ((ListExpression) result).getMembers().size());
    }

    @Test
    void testPushOnTupleThrows() {
        assertThrows(RuntimeException.class, () -> call("push", makeTuple("x"), "y"));
    }

    @Test
    void testPopRemovesLastElement() {
        ListExpression list = makeList("1", "2", "3");
        call("pop", list);
        assertEquals(2, list.getMembers().size());
    }

    @Test
    void testPopOnEmptyListThrows() {
        assertThrows(RuntimeException.class, () -> call("pop", makeList()));
    }

    @Test
    void testPopOnTupleThrows() {
        assertThrows(RuntimeException.class, () -> call("pop", makeTuple("x")));
    }

    @Test
    void testFirstOnList() {
        Object result = call("first", makeList("a", "b", "c"));
        assertEquals("a", ((DumbExpression) result).getValue());
    }

    @Test
    void testFirstOnTuple() {
        Object result = call("first", makeTuple("x", "y"));
        assertEquals("x", ((DumbExpression) result).getValue());
    }

    @Test
    void testFirstOnEmptyListThrows() {
        assertThrows(RuntimeException.class, () -> call("first", makeList()));
    }

    @Test
    void testLastOnList() {
        Object result = call("last", makeList("a", "b", "c"));
        assertEquals("c", ((DumbExpression) result).getValue());
    }

    @Test
    void testLastOnTuple() {
        Object result = call("last", makeTuple("x", "y"));
        assertEquals("y", ((DumbExpression) result).getValue());
    }

    @Test
    void testLastOnEmptyListThrows() {
        assertThrows(RuntimeException.class, () -> call("last", makeList()));
    }

    @Test
    void testContainsTrue() {
        assertEquals(true, call("contains", makeList("a", "b", "c"), "b"));
    }

    @Test
    void testContainsFalse() {
        assertEquals(false, call("contains", makeList("a", "b", "c"), "z"));
    }

    @Test
    void testContainsOnTuple() {
        assertEquals(true, call("contains", makeTuple("x", "y"), "x"));
    }

    @Test
    void testContainsOnEmptyList() {
        assertEquals(false, call("contains", makeList(), "a"));
    }

    @Test
    void testIndexOfFound() {
        assertEquals(1.0, call("findIndex", makeList("a", "b", "c"), "b"));
    }

    @Test
    void testIndexOfNotFound() {
        assertEquals(-1.0, call("findIndex", makeList("a", "b", "c"), "z"));
    }

    @Test
    void testIndexOfFirstOccurrence() {
        assertEquals(0.0, call("findIndex", makeList("a", "a", "b"), "a"));
    }

    @Test
    void testSlice() {
        ListExpression result = (ListExpression) call("slice", makeList("a", "b", "c", "d"), "1", "3");
        assertEquals(2, result.getMembers().size());
    }

    @Test
    void testSliceFullRange() {
        ListExpression result = (ListExpression) call("slice", makeList("a", "b", "c"), "0", "3");
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testSliceEmptyRange() {
        ListExpression result = (ListExpression) call("slice", makeList("a", "b", "c"), "1", "1");
        assertEquals(0, result.getMembers().size());
    }

    @Test
    void testReverseList() {
        ListExpression result = (ListExpression) call("reverse", makeList("a", "b", "c"));
        assertEquals("c", ((DumbExpression) result.getMembers().get(0)).getValue());
        assertEquals("a", ((DumbExpression) result.getMembers().get(2)).getValue());
    }

    @Test
    void testReverseDoesNotMutateOriginal() {
        ListExpression original = makeList("a", "b", "c");
        call("reverse", original);
        assertEquals("a", ((DumbExpression) original.getMembers().get(0)).getValue());
    }

    @Test
    void testReverseSingleElement() {
        ListExpression result = (ListExpression) call("reverse", makeList("x"));
        assertEquals(1, result.getMembers().size());
    }

    @Test
    void testConcatTwoLists() {
        ListExpression result = (ListExpression) call("concat", makeList("a", "b"), makeList("c", "d"));
        assertEquals(4, result.getMembers().size());
    }

    @Test
    void testConcatWithEmptyList() {
        ListExpression result = (ListExpression) call("concat", makeList("a", "b"), makeList());
        assertEquals(2, result.getMembers().size());
    }

    @Test
    void testConcatTwoEmptyLists() {
        ListExpression result = (ListExpression) call("concat", makeList(), makeList());
        assertEquals(0, result.getMembers().size());
    }

    @Test
    void testFlattenNestedList() {
        ListExpression inner = makeList("b", "c");
        ListExpression outer = new ListExpression(new ArrayList<>(List.of(wrap("a"), inner)));
        ListExpression result = (ListExpression) call("flatten", outer);
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testFlattenFlatListUnchanged() {
        ListExpression result = (ListExpression) call("flatten", makeList("a", "b", "c"));
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testJoinWithComma() {
        assertEquals("a,b,c", call("join", makeList("a", "b", "c"), ","));
    }

    @Test
    void testJoinWithEmptySeparator() {
        assertEquals("abc", call("join", makeList("a", "b", "c"), ""));
    }

    @Test
    void testJoinSingleElement() {
        assertEquals("a", call("join", makeList("a"), ","));
    }

    @Test
    void testJoinEmptyList() {
        assertEquals("", call("join", makeList(), ","));
    }

    @Test
    void testEmptyList() {
        Object result = call("emptyList");
        assertInstanceOf(ListExpression.class, result);
        assertEquals(0, ((ListExpression) result).getMembers().size());
    }

    @Test
    void testEmptyTuple() {
        Object result = call("emptyTuple");
        assertInstanceOf(TupleExpression.class, result);
        assertEquals(0, ((TupleExpression) result).getMembers().size());
    }
}
