package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class RegexLibTest {

    static Regex regex = new Regex();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        regex.loadLib(environment);
    }

    private Object call(String name, Object... args) {
        NativeFunction fn = (NativeFunction) environment.get(name);
        return fn.call(interpreter, List.of(args));
    }

    @Test
    void testMatchesFullMatch() {
        assertEquals(true, call("matches", "hello123", "[a-z]+\\d+"));
    }

    @Test
    void testMatchesNoMatch() {
        assertEquals(false, call("matches", "hello", "\\d+"));
    }

    @Test
    void testMatchesEmailPattern() {
        assertEquals(true, call("matches", "test@example.com", "[^@]+@[^@]+\\.[^@]+"));
    }

    @Test
    void testMatchesRequiresFullStringMatch() {
        assertEquals(false, call("matches", "abc123def", "\\d+"));
    }

    @Test
    void testMatchesEmptyString() {
        assertEquals(true, call("matches", "", ".*"));
    }

    @Test
    void testMatchesReturnsBoolean() {
        assertInstanceOf(Boolean.class, call("matches", "hello", ".*"));
    }

    @Test
    void testContainsTrue() {
        assertEquals(true, call("contains", "hello world", "\\bworld\\b"));
    }

    @Test
    void testContainsFalse() {
        assertEquals(false, call("contains", "hello world", "\\d+"));
    }

    @Test
    void testContainsPartialMatch() {
        assertEquals(true, call("contains", "abc123def", "\\d+"));
    }

    @Test
    void testContainsEmptyPattern() {
        assertEquals(true, call("contains", "anything", ""));
    }

    @Test
    void testContainsReturnsBoolean() {
        assertInstanceOf(Boolean.class, call("contains", "hello", "h"));
    }

    @Test
    void testFindFirstReturnsMatch() {
        assertEquals("123", call("findFirst", "abc123def456", "\\d+"));
    }

    @Test
    void testFindFirstNoMatchReturnsEmpty() {
        assertEquals("", call("findFirst", "abcdef", "\\d+"));
    }

    @Test
    void testFindFirstReturnsOnlyFirst() {
        assertEquals("abc", call("findFirst", "abc def ghi", "[a-z]+"));
    }

    @Test
    void testFindFirstReturnsString() {
        assertInstanceOf(String.class, call("findFirst", "hello", "h"));
    }

    @Test
    void testFindFirstEmail() {
        assertEquals("user@example.com", call("findFirst", "contact user@example.com now", "[^\\s]+@[^\\s]+"));
    }

    @Test
    void testFindAllReturnsListExpression() {
        assertInstanceOf(ListExpression.class, call("findAll", "abc123def456", "\\d+"));
    }

    @Test
    void testFindAllCorrectSize() {
        ListExpression result = (ListExpression) call("findAll", "abc123def456", "\\d+");
        assertEquals(2, result.getMembers().size());
    }

    @Test
    void testFindAllCorrectValues() {
        ListExpression result = (ListExpression) call("findAll", "abc123def456", "\\d+");
        assertEquals("123", ((DumbExpression) result.getMembers().get(0)).getValue());
        assertEquals("456", ((DumbExpression) result.getMembers().get(1)).getValue());
    }

    @Test
    void testFindAllNoMatchReturnsEmptyList() {
        ListExpression result = (ListExpression) call("findAll", "abcdef", "\\d+");
        assertEquals(0, result.getMembers().size());
    }

    @Test
    void testFindAllWords() {
        ListExpression result = (ListExpression) call("findAll", "one two three", "\\w+");
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testReplaceAllBasic() {
        assertEquals("abc_def_ghi", call("replaceAll", "abc123def456ghi", "\\d+", "_"));
    }

    @Test
    void testReplaceAllNoMatch() {
        assertEquals("abcdef", call("replaceAll", "abcdef", "\\d+", "_"));
    }

    @Test
    void testReplaceAllWithEmptyReplacement() {
        assertEquals("abcdef", call("replaceAll", "abc123def", "\\d+", ""));
    }

    @Test
    void testReplaceAllMultipleOccurrences() {
        assertEquals("x x x", call("replaceAll", "a b c", "[a-z]", "x"));
    }

    @Test
    void testReplaceAllReturnsString() {
        assertInstanceOf(String.class, call("replaceAll", "hello", "l", "r"));
    }

    @Test
    void testReplaceFirstBasic() {
        assertEquals("abc_def456ghi", call("replaceFirst", "abc123def456ghi", "\\d+", "_"));
    }

    @Test
    void testReplaceFirstOnlyFirst() {
        assertEquals("xbc abc", call("replaceFirst", "abc abc", "a", "x"));
    }

    @Test
    void testReplaceFirstNoMatch() {
        assertEquals("abcdef", call("replaceFirst", "abcdef", "\\d+", "_"));
    }

    @Test
    void testReplaceFirstReturnsString() {
        assertInstanceOf(String.class, call("replaceFirst", "hello", "l", "r"));
    }

    @Test
    void testSplitByComma() {
        ListExpression result = (ListExpression) call("split", "a,b,c", ",");
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testSplitByWhitespace() {
        ListExpression result = (ListExpression) call("split", "one two three", "\\s+");
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testSplitCorrectValues() {
        ListExpression result = (ListExpression) call("split", "a,b,c", ",");
        assertEquals("a", ((DumbExpression) result.getMembers().get(0)).getValue());
        assertEquals("b", ((DumbExpression) result.getMembers().get(1)).getValue());
        assertEquals("c", ((DumbExpression) result.getMembers().get(2)).getValue());
    }

    @Test
    void testSplitNoDelimiterReturnsSingleElement() {
        ListExpression result = (ListExpression) call("split", "hello", ",");
        assertEquals(1, result.getMembers().size());
    }

    @Test
    void testSplitReturnsListExpression() {
        assertInstanceOf(ListExpression.class, call("split", "a,b", ","));
    }

    @Test
    void testCaptureReturnsListExpression() {
        assertInstanceOf(ListExpression.class, call("capture", "2024-01-15", "(\\d{4})-(\\d{2})-(\\d{2})"));
    }

    @Test
    void testCaptureCorrectGroupCount() {
        ListExpression result = (ListExpression) call("capture", "2024-01-15", "(\\d{4})-(\\d{2})-(\\d{2})");
        assertEquals(3, result.getMembers().size());
    }

    @Test
    void testCaptureCorrectGroupValues() {
        ListExpression result = (ListExpression) call("capture", "2024-01-15", "(\\d{4})-(\\d{2})-(\\d{2})");
        assertEquals("2024", ((DumbExpression) result.getMembers().get(0)).getValue());
        assertEquals("01", ((DumbExpression) result.getMembers().get(1)).getValue());
        assertEquals("15", ((DumbExpression) result.getMembers().get(2)).getValue());
    }

    @Test
    void testCaptureNoMatchReturnsEmptyList() {
        ListExpression result = (ListExpression) call("capture", "abcdef", "(\\d+)");
        assertEquals(0, result.getMembers().size());
    }

    @Test
    void testCaptureOptionalGroupReturnsEmpty() {
        ListExpression result = (ListExpression) call("capture", "abc", "(\\d*)");
        assertEquals(1, result.getMembers().size());
        assertEquals("", ((DumbExpression) result.getMembers().get(0)).getValue());
    }

    @Test
    void testCountMatchesBasic() {
        assertEquals(3.0, call("countMatches", "abc123def456ghi789", "\\d+"));
    }

    @Test
    void testCountMatchesNoMatch() {
        assertEquals(0.0, call("countMatches", "abcdef", "\\d+"));
    }

    @Test
    void testCountMatchesSingleMatch() {
        assertEquals(1.0, call("countMatches", "hello world", "world"));
    }

    @Test
    void testCountMatchesReturnsDouble() {
        assertInstanceOf(Double.class, call("countMatches", "aaa", "a"));
    }

    @Test
    void testCountMatchesAllChars() {
        assertEquals(5.0, call("countMatches", "hello", "."));
    }
}
