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
import com.mira.parser.nodes.expression.Expression.MapExpression;
import com.mira.runtime.functions.Callable;
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

    private Object call(String name, Object... args) {
        NativeFunction fn = (NativeFunction) environment.get(name);
        return fn.call(interpreter, List.of(args));
    }

    private Object callHof(String name, Object... args) {
        Callable fn = (Callable) environment.get(name);
        return fn.call(interpreter, List.of(args));
    }

    private static NativeFunction doubler() {
        return new NativeFunction(1, args -> Double.parseDouble(String.valueOf(args.get(0))) * 2);
    }

    private static NativeFunction isEven() {
        return new NativeFunction(1, args -> Double.parseDouble(String.valueOf(args.get(0))) % 2 == 0);
    }

    private static NativeFunction adder() {
        return new NativeFunction(2, args -> Double.parseDouble(String.valueOf(args.get(0)))
                + Double.parseDouble(String.valueOf(args.get(1))));
    }

    private static NativeFunction identity() {
        return new NativeFunction(1, args -> Double.parseDouble(String.valueOf(args.get(0))));
    }

    private static NativeFunction toStringKey() {
        return new NativeFunction(1, args -> {
            double v = Double.parseDouble(String.valueOf(args.get(0)));
            return v % 2 == 0 ? "even" : "odd";
        });
    }

    @Test
    void testSizeOnList() {
        assertEquals(3.0, call("size", makeList("1", "2", "3")));
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
    void testRemoveByIndex() {
        ListExpression list = makeList("a", "b", "c");
        call("remove", list, "1");
        assertEquals(2, list.getMembers().size());
        assertEquals("a", ((DumbExpression) list.getMembers().get(0)).getValue());
        assertEquals("c", ((DumbExpression) list.getMembers().get(1)).getValue());
    }

    @Test
    void testRemoveFirstElement() {
        ListExpression list = makeList("x", "y", "z");
        call("remove", list, "0");
        assertEquals(2, list.getMembers().size());
        assertEquals("y", ((DumbExpression) list.getMembers().get(0)).getValue());
    }

    @Test
    void testRemoveOnNonListThrows() {
        assertThrows(RuntimeException.class, () -> call("remove", "notAList", "0"));
    }

    @Test
    void testFirstOnList() {
        Object result = call("first", makeList("a", "b", "c"));
        assertEquals("a", ((DumbExpression) result).getValue());
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
    void testContainsOnEmptyList() {
        assertEquals(false, call("contains", makeList(), "a"));
    }

    @Test
    void testIndexOfFound() {
        assertEquals(1.0, call("indexOf", makeList("a", "b", "c"), "b"));
    }

    @Test
    void testIndexOfNotFound() {
        assertEquals(-1.0, call("indexOf", makeList("a", "b", "c"), "z"));
    }

    @Test
    void testIndexOfFirstOccurrence() {
        assertEquals(0.0, call("indexOf", makeList("a", "a", "b"), "a"));
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
    void testNewList() {
        Object result = call("newList");
        assertInstanceOf(ListExpression.class, result);
        assertEquals(0, ((ListExpression) result).getMembers().size());
    }

    // HOF tests

    @Test
    void testMap() {
        ListExpression result = (ListExpression) callHof("map", makeList("1", "2", "3"), doubler());
        assertEquals(3, result.getMembers().size());
        assertEquals(2.0, Double.parseDouble(String.valueOf(((DumbExpression) result.getMembers().get(0)).getValue())));
    }

    @Test
    void testFilter() {
        ListExpression result = (ListExpression) callHof("filter", makeList("1", "2", "3", "4"), isEven());
        assertEquals(2, result.getMembers().size());
    }

    @Test
    void testReduce() {
        Object result = callHof("reduce", makeList("1", "2", "3", "4"), adder(), 0.0);
        assertEquals(10.0, Double.parseDouble(String.valueOf(result)));
    }

    @Test
    void testAnyTrue() {
        assertEquals(true, callHof("any", makeList("1", "2", "3"), isEven()));
    }

    @Test
    void testAnyFalse() {
        assertEquals(false, callHof("any", makeList("1", "3", "5"), isEven()));
    }

    @Test
    void testAllTrue() {
        assertEquals(true, callHof("all", makeList("2", "4", "6"), isEven()));
    }

    @Test
    void testAllFalse() {
        assertEquals(false, callHof("all", makeList("1", "2", "3"), isEven()));
    }

    @Test
    void testCount() {
        assertEquals(2.0, callHof("count", makeList("1", "2", "3", "4"), isEven()));
    }

    @Test
    void testSortBy() {
        ListExpression result = (ListExpression) callHof("sortBy", makeList("3", "1", "2"), identity());
        assertEquals("1", String.valueOf(((DumbExpression) result.getMembers().get(0)).getValue()));
    }

    // Utility tests

    @Test
    void testSort() {
        ListExpression result = (ListExpression) call("sort", makeList("3", "1", "2"));
        assertEquals("1", String.valueOf(((DumbExpression) result.getMembers().get(0)).getValue()));
    }

    @Test
    void testUnique() {
        ListExpression result = (ListExpression) call("unique", makeList("a", "b", "a", "c"));
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testSum() {
        assertEquals(6.0, call("sum", makeList("1", "2", "3")));
    }

    @Test
    void testAvg() {
        assertEquals(2.0, call("avg", makeList("1", "2", "3")));
    }

    @Test
    void testAvgEmptyThrows() {
        assertThrows(RuntimeException.class, () -> call("avg", makeList()));
    }

    @Test
    void testZip() {
        ListExpression result = (ListExpression) call("zip", makeList("a", "b"), makeList("1", "2"));
        assertEquals(2, result.getMembers().size());
    }

    @Test
    void testFill() {
        ListExpression result = (ListExpression) call("fill", "3", "x");
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testMin() {
        assertEquals(1.0, call("min", makeList("3", "1", "2")));
    }

    @Test
    void testMax() {
        assertEquals(3.0, call("max", makeList("3", "1", "2")));
    }

    @Test
    void testTake() {
        ListExpression result = (ListExpression) call("take", makeList("a", "b", "c", "d"), "2");
        assertEquals(2, result.getMembers().size());
    }

    @Test
    void testDrop() {
        ListExpression result = (ListExpression) call("drop", makeList("a", "b", "c", "d"), "2");
        assertEquals(2, result.getMembers().size());
    }

    @Test
    void testChunk() {
        ListExpression result = (ListExpression) callHof("chunk", makeList("1", "2", "3", "4"), "2");
        assertEquals(2, result.getMembers().size());
    }

    @Test
    void testFindFirst() {
        Object result = callHof("findFirst", makeList("1", "2", "3"), isEven());
        assertEquals(2.0, Double.parseDouble(String.valueOf(result)));
    }

    @Test
    void testGroupBy() {
        Object result = callHof("groupBy", makeList("1", "2", "3", "4"), toStringKey());
        assertInstanceOf(MapExpression.class, result);
    }
}
