package com.mira.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.parser.ParserError.UnexpectedToken;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.functions.ReturnSignal;

public class InterpreterTest {

    Interpreter interpreter;

    @BeforeEach
    void setup() {
        interpreter = new Interpreter();
    }

    Object run(String source) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        return interpreter.run(parser.parseTokens(tokenizer.tokenize(source)));
    }

    @Test
    void testSimpleArithmetic() {
        assertEquals(3.0, run("eval(1+2);"));
        assertEquals(2.0, run("eval(5-3);"));
        assertEquals(6.0, run("eval(2*3);"));
        assertEquals(4.0, run("eval(8/2);"));
    }

    @Test
    void testUnaryOperators() {
        assertEquals(-5.0, run("eval(-5);"));
        assertEquals(1.0, run("eval(-1+2);"));
        assertEquals(-3.0, run("eval(-1*3);"));
    }

    @Test
    void testParentheses() {
        double result = (double) Evaluator.evaluate("(1+2)*3");
        assertEquals(9.0, result);

        result = (double) Evaluator.evaluate("-(2+3)*4");
        assertEquals(-20.0, result);

        result = (double) Evaluator.evaluate("((1+2)+(3+4))*2");
        assertEquals(20.0, result);
    }

    @Test
    void testVariableUsage() {
        Interpreter.getGlobalEnvironment().define("x", 10);
        Interpreter.getGlobalEnvironment().define("y", 5);
        Interpreter.getGlobalEnvironment().define("val", 3);

        assertEquals(15.0, run("eval($x + $y);"));
        assertEquals(5.0, run("eval($val+2);"));
        assertEquals(23.0, run("eval($val + $x * 2);"));
    }

    @Test
    void testComplexExpressionWithUnaryAndParentheses() {
        assertEquals(0.5, run("""
                var a : 5;
                var b : 3;
                eval((-$a + ($b*2)) / 2);
                """));
    }

    @Test
    void testDecimalNumbers() {
        double result = (double) Evaluator.evaluate("1.5 + 2.3");
        assertEquals(3.8, result, 0.0001);

        result = (double) Evaluator.evaluate("10.0 / 4.0");
        assertEquals(2.5, result, 0.0001);

        result = (double) Evaluator.evaluate("-0.5 * 8");
        assertEquals(-4.0, result, 0.0001);
    }

    @Test
    void testNestedParentheses() {
        double result = (double) Evaluator.evaluate("((1+2)+(3+4))*2");
        assertEquals(20.0, result);

        result = (double) Evaluator.evaluate("(1+(2+(3+4)))");
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
        assertThrows(UnexpectedToken.class, () -> run("eval(1++2);"));
        assertThrows(RuntimeException.class, () -> run("eval(unknownVar+1);"));
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
                exec($x);
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
                exec($x);
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
                exec($y);
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
                exec($print);
                """;
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals("Hello World", returnSignal.getValue());
        }
    }

    @Test
    void testWrappedReference() {
        String source = """
                var x : 1;
                var y : x;
                var z : y;

                print($$$z);
                """;
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals("1", returnSignal.getValue());
        }
    }

    @Test
    void testIfStatement() {
        String source = """
                var x : 5;

                if ($x > 3) {
                    ret(true);
                } else {
                    ret(false);
                }
                """;
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source))));
        } catch (ReturnSignal returnSignal) {
            assertEquals("true", returnSignal.getValue());
        }
    }

    @Test
    void testSimpleConditions() {
        assertTrue((boolean) Evaluator.evaluate("1 > 0"));
        assertFalse((boolean) Evaluator.evaluate("1 < 0"));
        assertTrue((boolean) Evaluator.evaluate("5 >= 5"));
    }

    @Test
    void testConditionsWithPara() {
        assertTrue((boolean) Evaluator.evaluate("(5 > 3 && 10 > 5)"));
        assertFalse((boolean) Evaluator.evaluate("5 > 3 && 10 < 5"));
        assertFalse((boolean) Evaluator.evaluate("!(5 > 3)"));
        assertTrue((boolean) Evaluator.evaluate("((5 > 3 && 10 > 5) || (3 == 4)) && !(2 > 10)"));
    }

    @Test
    void testEdgeCases() {
        assertFalse((boolean) Evaluator.evaluate("1 == 1 == 1"));
    }

    @Test
    void testFibonacci() {
        assertEquals(6765.0, run("""
                fn fibonacci(n){
                    if($n<=1){
                        ret($n);
                    }else{
                        ret(fibonacci(eval($n-2)) + fibonacci(eval($n-1)));
                    }
                    ret(0);
                }
                eval(fibonacci(20));
                """));
    }
}
