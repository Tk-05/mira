package com.mira.runtime;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.functions.ReturnSignal;

public class InterpreterTest {

    Interpreter interpreter;

    @BeforeEach
    void setup() {
        interpreter = new Interpreter();
    }

    @Test
    void testSimpleArithmetic() {
        double result = Evaluator.evaluate("1+2");
        assertEquals(3.0, result);

        result = Evaluator.evaluate("5-3");
        assertEquals(2.0, result);

        result = Evaluator.evaluate("2*3");
        assertEquals(6.0, result);

        result = Evaluator.evaluate("8/2");
        assertEquals(4.0, result);
    }

    @Test
    void testUnaryOperators() {
        double result = Evaluator.evaluate("-5");
        assertEquals(-5.0, result);

        result = Evaluator.evaluate("-1+2");
        assertEquals(1.0, result);

        result = Evaluator.evaluate("-1*3");
        assertEquals(-3.0, result);
    }

    @Test
    void testParentheses() {
        double result = Evaluator.evaluate("(1+2)*3");
        assertEquals(9.0, result);

        result = Evaluator.evaluate("-(2+3)*4");
        assertEquals(-20.0, result);

        result = Evaluator.evaluate("((1+2)+(3+4))*2");
        assertEquals(20.0, result);
    }

    @Test
    void testVariableUsage() {
        Interpreter.getGlobalEnvironment().define("x", 10);
        Interpreter.getGlobalEnvironment().define("y", 5);
        Interpreter.getGlobalEnvironment().define("$val", 3);

        double result = Evaluator.evaluate("x+y");
        assertEquals(15.0, result);

        result = Evaluator.evaluate("$val+2");
        assertEquals(5.0, result);

        result = Evaluator.evaluate("$val+ x * 2");
        assertEquals(23.0, result);
    }

    @Test
    void testComplexExpressionWithUnaryAndParentheses() {
        Interpreter.getGlobalEnvironment().define("$a", 5);
        Interpreter.getGlobalEnvironment().define("$b", 3);

        double result = Evaluator.evaluate("(-$a + ($b*2)) / 2");
        assertEquals(0.5, result);
    }

    @Test
    void testDecimalNumbers() {
        double result = Evaluator.evaluate("1.5 + 2.3");
        assertEquals(3.8, result, 0.0001);

        result = Evaluator.evaluate("10.0 / 4.0");
        assertEquals(2.5, result, 0.0001);

        result = Evaluator.evaluate("-0.5 * 8");
        assertEquals(-4.0, result, 0.0001);
    }

    @Test
    void testNestedParentheses() {
        double result = Evaluator.evaluate("((1+2)+(3+4))*2");
        assertEquals(20.0, result);

        result = Evaluator.evaluate("(1+(2+(3+4)))");
        assertEquals(10.0, result);
    }

    @Test
    void testEntangledVarDecl() {
        String source = """
                var x : 24;
                var y : 18;
                var z : eval($x + $y);
                ret($z);
                """;

        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals(42.0, returnSignal.getValue());
        }
    }

    @Test
    void testGlobalExit() {
        String source = "ret(eval(0));";
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals(0.0, returnSignal.getValue());
        }
    }

    @Test
    void testErrors() {
        assertThrows(NoSuchElementException.class, () -> Evaluator.evaluate("1++2"));
        assertThrows(RuntimeException.class, () -> Evaluator.evaluate("((1+2)"));
        assertThrows(RuntimeException.class, () -> Evaluator.evaluate("unknownVar+1"));
    }

    @Test
    void testAssign() {
        String source = """
                var x : 24;
                var y : 18;
                var z;
                $z : $x + $y;
                ret(eval($z));
                """;

        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals(42.0, returnSignal.getValue());
        }
    }

    @Test
    void testEmptyVarDecl() {
        String source = """
                var x;
                ret($x);
                """;
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals(null, returnSignal.getValue());
        }
    }

    @Test
    void testWrappedReturnWithEval() {
        String source = """
                var x : "ret(eval(0));";
                eval($x);
                """;
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals(0.0, returnSignal.getValue());
        }
    }

    @Test
    void testWrappedReturnWithComplexEval() {
        String source = """
                var x : "ret(eval(2+2));";
                eval($x);
                """;
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals(4.0, returnSignal.getValue());
        }
    }

    @Test
    void testWrappedReturnWithRef() {
        String source = """
                var x : 7;
                var y : "ret(eval($x * 3));";
                eval($y);
                """;
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals(21.0, returnSignal.getValue());
        }
    }

    @Test
    void testWrappedReturnFunction() {
        String source = """
                fn greet(name) {
                    ret("Hello " $name);
                }
                var print : "ret(greet(\"World\"));";
                eval($print);
                """;
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals("Hello World", returnSignal.getValue());
        }
    }
}
