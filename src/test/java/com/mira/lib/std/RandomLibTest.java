package com.mira.lib.std;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

public class RandomLibTest {

    static RandomLib lib = new RandomLib();
    static Environment environment = new Environment();
    static Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        lib.loadLib(environment);
    }

    private static ListExpression makeList(String... values) {
        java.util.List<com.mira.parser.nodes.expression.Expression> members = new java.util.ArrayList<>();
        for (String v : values) {
            members.add(new DumbExpression(new Token(TokenType.EXPRESSION, v, 0, 0)));
        }
        return new ListExpression(members);
    }

    @Test
    void testSeed() {
        NativeFunction fn = (NativeFunction) environment.get("seed");
        assertNotNull(fn.call(interpreter, List.of(42.0)));
    }

    @Test
    void testNext() {
        NativeFunction fn = (NativeFunction) environment.get("next");
        double result = (double) fn.call(interpreter, List.of());
        assertTrue(result >= 0.0 && result < 1.0);
    }

    @Test
    void testNextInt() {
        NativeFunction fn = (NativeFunction) environment.get("nextInt");
        double result = (double) fn.call(interpreter, List.of(5.0, 10.0));
        assertTrue(result >= 5.0 && result < 10.0);
    }

    @Test
    void testNextFloat() {
        NativeFunction fn = (NativeFunction) environment.get("nextFloat");
        double result = (double) fn.call(interpreter, List.of(1.0, 2.0));
        assertTrue(result >= 1.0 && result < 2.0);
    }

    @Test
    void testNextBool() {
        NativeFunction fn = (NativeFunction) environment.get("nextBool");
        Object result = fn.call(interpreter, List.of());
        assertTrue(result instanceof Boolean);
    }

    @Test
    void testNextGaussian() {
        NativeFunction fn = (NativeFunction) environment.get("nextGaussian");
        Object result = fn.call(interpreter, List.of());
        assertTrue(result instanceof Double);
    }

    @Test
    void testShuffle() {
        NativeFunction fn = (NativeFunction) environment.get("shuffle");
        ListExpression input = makeList("1", "2", "3", "4", "5");
        Object result = fn.call(interpreter, List.of(input));
        assertTrue(result instanceof ListExpression);
        assertEquals(5, ((ListExpression) result).getMembers().size());
    }

    @Test
    void testPick() {
        NativeFunction fn = (NativeFunction) environment.get("pick");
        ListExpression input = makeList("a", "b", "c");
        Object result = fn.call(interpreter, List.of(input));
        assertNotNull(result);
    }

    @Test
    void testSample() {
        NativeFunction fn = (NativeFunction) environment.get("sample");
        ListExpression input = makeList("a", "b", "c", "d", "e");
        Object result = fn.call(interpreter, List.of(input, 3.0));
        assertTrue(result instanceof ListExpression);
        assertEquals(3, ((ListExpression) result).getMembers().size());
    }
}
