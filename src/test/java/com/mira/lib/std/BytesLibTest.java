package com.mira.lib.std;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.lexer.token.Token;
import com.mira.lexer.token.TokenType;
import com.mira.parser.nodes.expression.Expression.DumbExpression;
import com.mira.parser.nodes.expression.Expression.ListExpression;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;
import com.mira.runtime.values.BytesValue;

public class BytesLibTest {

    static Bytes bytes = new Bytes();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        bytes.loadLib(environment);
    }

    private NativeFunction fn(String name) {
        return (NativeFunction) environment.get(name);
    }

    private static DumbExpression num(double d) {
        return new DumbExpression(new Token(TokenType.EXPRESSION, String.valueOf(d), 0, 0));
    }

    private static ListExpression listOf(double... values) {
        java.util.List<com.mira.parser.nodes.expression.Expression> members = new java.util.ArrayList<>();
        for (double v : values) {
            members.add(num(v));
        }
        return new ListExpression(members);
    }

    @Test
    void testNewBytes() {
        BytesValue b = (BytesValue) fn("newBytes").call(interpreter, List.of("5"));
        assertEquals(5, b.size());
        for (byte by : b.getData()) {
            assertEquals(0, by);
        }
    }

    @Test
    void testFromString() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("Hello"));
        assertEquals(5, b.size());
        assertEquals(72, b.getData()[0] & 0xFF);
        assertEquals(111, b.getData()[4] & 0xFF);
    }

    @Test
    void testFromList() {
        BytesValue b = (BytesValue) fn("fromList").call(interpreter, List.of(listOf(72, 101, 108, 108, 111)));
        assertEquals(5, b.size());
        assertEquals(72, b.getData()[0] & 0xFF);
    }

    @Test
    void testFromHex() {
        BytesValue b = (BytesValue) fn("fromHex").call(interpreter, List.of("48656c6c6f"));
        assertEquals(5, b.size());
        assertEquals(72, b.getData()[0] & 0xFF);
    }

    @Test
    void testFromBase64() {
        BytesValue b = (BytesValue) fn("fromBase64").call(interpreter, List.of("SGVsbG8="));
        assertEquals(5, b.size());
        assertEquals("Hello", new String(b.getData(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void testSize() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("Hi"));
        double size = (double) fn("size").call(interpreter, List.of(b));
        assertEquals(2.0, size);
    }

    @Test
    void testGet() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("ABC"));
        double val = (double) fn("get").call(interpreter, List.of(b, "0"));
        assertEquals(65.0, val);
        val = (double) fn("get").call(interpreter, List.of(b, "2"));
        assertEquals(67.0, val);
    }

    @Test
    void testSet() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("Hello"));
        BytesValue result = (BytesValue) fn("set").call(interpreter, List.of(b, "0", "87"));
        assertEquals(87, result.getData()[0] & 0xFF);
        assertEquals(72, b.getData()[0] & 0xFF);
    }

    @Test
    void testSlice() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("Hello"));
        BytesValue result = (BytesValue) fn("slice").call(interpreter, List.of(b, "1", "4"));
        assertEquals(3, result.size());
        assertEquals(101, result.getData()[0] & 0xFF);
    }

    @Test
    void testConcat() {
        BytesValue a = (BytesValue) fn("fromString").call(interpreter, List.of("Hi"));
        BytesValue b2 = (BytesValue) fn("fromString").call(interpreter, List.of("!"));
        BytesValue result = (BytesValue) fn("concat").call(interpreter, List.of(a, b2));
        assertEquals(3, result.size());
        assertEquals("Hi!", new String(result.getData(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void testCopy() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("Hi"));
        BytesValue copy = (BytesValue) fn("copy").call(interpreter, List.of(b));
        assertEquals(b.size(), copy.size());
        assertEquals(b.getData()[0], copy.getData()[0]);
    }

    @Test
    void testFill() {
        BytesValue b = (BytesValue) fn("newBytes").call(interpreter, List.of("3"));
        BytesValue filled = (BytesValue) fn("fill").call(interpreter, List.of(b, "255"));
        for (byte by : filled.getData()) {
            assertEquals((byte) 255, by);
        }
    }

    @Test
    void testToString() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("Hello"));
        String result = (String) fn("toString").call(interpreter, List.of(b));
        assertEquals("Hello", result);
    }

    @Test
    void testToList() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("AB"));
        ListExpression list = (ListExpression) fn("toList").call(interpreter, List.of(b));
        assertEquals(2, list.getMembers().size());
        assertEquals("65.0", String.valueOf(((DumbExpression) list.getMembers().get(0)).getValue()));
    }

    @Test
    void testToHex() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("Hello"));
        String hex = (String) fn("toHex").call(interpreter, List.of(b));
        assertEquals("48656c6c6f", hex);
    }

    @Test
    void testToBase64() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("Hello"));
        String b64 = (String) fn("toBase64").call(interpreter, List.of(b));
        assertEquals("SGVsbG8=", b64);
    }

    @Test
    void testReadWriteFile() throws IOException {
        Path tmp = Files.createTempFile("mira-bytes-test-", ".bin");
        try {
            BytesValue original = (BytesValue) fn("fromString").call(interpreter, List.of("TestData"));
            fn("writeFile").call(interpreter, List.of(tmp.toString(), original));
            BytesValue loaded = (BytesValue) fn("readFile").call(interpreter, List.of(tmp.toString()));
            assertEquals("TestData", new String(loaded.getData(), java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void testReadFileMissing() {
        assertThrows(RuntimeException.class,
                () -> fn("readFile").call(interpreter, List.of("/nonexistent/path/file.bin")));
    }

    @Test
    void testBytesValueToStringEmpty() {
        assertEquals("[]", new BytesValue(0).toString());
    }

    @Test
    void testBytesValueToStringSingle() {
        assertEquals("[0x48]", new BytesValue(new byte[]{0x48}).toString());
    }

    @Test
    void testBytesValueToStringMultiple() {
        BytesValue b = (BytesValue) fn("fromString").call(interpreter, List.of("Hi"));
        assertEquals("[0x48, 0x69]", b.toString());
    }

    @Test
    void testBytesValueToStringHighByte() {
        assertEquals("[0xff]", new BytesValue(new byte[]{(byte) 0xFF}).toString());
    }

    @Test
    void testGetWrongType() {
        assertThrows(RuntimeException.class, () -> fn("get").call(interpreter, List.of("not-bytes", "0")));
    }
}
