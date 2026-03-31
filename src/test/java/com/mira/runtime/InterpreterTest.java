package com.mira.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.PostUnaryError;
import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.lexer.Tokenizer;
import com.mira.parser.Parser;
import com.mira.runtime.functions.BreakSignal;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.Evaluator;
import com.mira.runtime.interpreter.ImportResolver;
import com.mira.runtime.interpreter.Interpreter;

public class InterpreterTest {

    Interpreter interpreter;

    @BeforeEach
    void setup() {
        interpreter = new Interpreter();
    }

    void createNewGlobalContext() {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        interpreter.run(parser.parseTokens(tokenizer.tokenize("", false)), false);
    }

    Object runWithNewGlobalContext(String source) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        return interpreter.run(parser.parseTokens(tokenizer.tokenize(source, false)), false);
    }

    Object runWithoutNewGlobalContext(String source) {
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        return interpreter.runWithoutLoadingNewContext(parser.parseTokens(tokenizer.tokenize(source, false)));
    }

    @Test
    void testSimpleArithmetic() {
        assertEquals(3.0, runWithNewGlobalContext("eval(1+2);"));
        assertEquals(2.0, runWithNewGlobalContext("eval(5-3);"));
        assertEquals(6.0, runWithNewGlobalContext("eval(2*3);"));
        assertEquals(4.0, runWithNewGlobalContext("eval(8/2);"));
    }

    @Test
    void testUnaryOperators() {
        assertEquals(-5.0, runWithNewGlobalContext("eval(-5);"));
        assertEquals(1.0, runWithNewGlobalContext("eval(-1+2);"));
        assertEquals(-3.0, runWithNewGlobalContext("eval(-1*3);"));
    }

    @Test
    void testParentheses() {
        double result = (double) Evaluator.evaluate("(1+2)*3", false);
        assertEquals(9.0, result);

        result = (double) Evaluator.evaluate("-(2+3)*4", false);
        assertEquals(-20.0, result);

        result = (double) Evaluator.evaluate("((1+2)+(3+4))*2", false);
        assertEquals(20.0, result);
    }

    @Test
    void testVariableUsage() {
        createNewGlobalContext();
        Interpreter.getGlobalEnvironment().define("x", 10);
        Interpreter.getGlobalEnvironment().define("y", 5);
        Interpreter.getGlobalEnvironment().define("val", 3);

        assertEquals(15.0, runWithoutNewGlobalContext("eval($x + $y);"));
        assertEquals(5.0, runWithoutNewGlobalContext("eval($val+2);"));
        assertEquals(23.0, runWithoutNewGlobalContext("eval($val + $x * 2);"));
    }

    @Test
    void testComplexExpressionWithUnaryAndParentheses() {
        assertEquals(0.5, runWithNewGlobalContext("""
                var a : 5;
                var b : 3;
                eval((-$a + ($b*2)) / 2);
                """));
    }

    @Test
    void testDecimalNumbers() {
        double result = (double) Evaluator.evaluate("1.5 + 2.3", false);
        assertEquals(3.8, result, 0.0001);

        result = (double) Evaluator.evaluate("10.0 / 4.0", false);
        assertEquals(2.5, result, 0.0001);

        result = (double) Evaluator.evaluate("-0.5 * 8", false);
        assertEquals(-4.0, result, 0.0001);
    }

    @Test
    void testNestedParentheses() {
        double result = (double) Evaluator.evaluate("((1+2)+(3+4))*2", false);
        assertEquals(20.0, result);

        result = (double) Evaluator.evaluate("(1+(2+(3+4)))", false);
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
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, false))), false);
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
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, true))), false);
        } catch (ReturnSignal returnSignal) {
            assertEquals(0.0, returnSignal.getValue());
        }
    }

    @Test
    void testErrors() {
        assertThrows(PostUnaryError.class, () -> runWithNewGlobalContext("eval(1++2);"));
        assertThrows(RuntimeException.class, () -> runWithNewGlobalContext("eval(unknownVar+1);"));
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
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, false))), false);
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
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, false))), false);
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
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, false))), false);
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
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, false))), false);
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
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, false))), false);
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
                var greeting : "ret(greet(\"World\"));";
                exec($greeting);
                """;
        Tokenizer tokenizer = new Tokenizer();
        Parser parser = new Parser();
        try {
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, false))), false);
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
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, false))), false);
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
            interpreter.run((parser.parseTokens(tokenizer.tokenize(source, false))),false);
        } catch (ReturnSignal returnSignal) {
            assertEquals("true", returnSignal.getValue());
        }
    }

    @Test
    void testSimpleConditions() {
        assertTrue((boolean) Evaluator.evaluate("1 > 0", false));
        assertFalse((boolean) Evaluator.evaluate("1 < 0", false));
        assertTrue((boolean) Evaluator.evaluate("5 >= 5", false));
    }

    @Test
    void testConditionsWithPara() {
        assertTrue((boolean) Evaluator.evaluate("(5 > 3 && 10 > 5)", false));
        assertFalse((boolean) Evaluator.evaluate("5 > 3 && 10 < 5", false));
        assertFalse((boolean) Evaluator.evaluate("!(5 > 3)", false));
        assertTrue((boolean) Evaluator.evaluate("((5 > 3 && 10 > 5) || (3 == 4)) && !(2 > 10)", false));
    }

    @Test
    void testEdgeCases() {
        assertFalse((boolean) Evaluator.evaluate("1 == 1 == 1", false));
    }

    @Test
    void testFibonacci() {
        assertEquals(6765.0, runWithNewGlobalContext("""
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

    @Test
    void testForWithFibonacci() {
        assertEquals(88.0, runWithNewGlobalContext("""
                var result : 0;
                
                fn fibonacci(n){
                    if($n<=1){
                        ret($n);
                    }else{
                        ret(fibonacci(eval($n-2)) + fibonacci(eval($n-1)));
                    }
                    ret(0);
                }
                
                for (var i : 0, var j : 0; $i < 10 && $j == 0; $i : eval($i + 1)) {
                    $result : $result + fibonacci($i);
                }
                
                eval($result);
                """));
    }

    @Test
    void testForWithoutInitalizer() {
        assertEquals(null, runWithNewGlobalContext("""
                var i : 0;
                for (; $i < 3; $i : eval($i + 1)) {}
                """));
    }

    @Test
    void testWhile() {
        assertEquals(null, runWithNewGlobalContext("""
                var i : 0;
                while($i <= 10){
                    $i : eval($i + 1);
                }
                """));
    }

    @Test
    void testBreak() {
        assertThrows(BreakSignal.class, () -> runWithNewGlobalContext("""
                break();
                """));

        assertEquals(null, runWithNewGlobalContext("""
                    while(1){
                        break();
                    }
                """));
    }

    @Test
    void testDeepNestedBreak() {
        runWithNewGlobalContext("""
                        var outer : 0;
                        var middle : 0;
                        var inner : 0;
                
                        while ($outer < 3) {
                            $outer : eval($outer + 1);
                
                            while ($middle < 5) {
                                $middle : eval($middle + 1);
                
                                while (1) {
                                    $inner : eval($inner + 1);
                                    break();
                                }
                            }
                        }
                """);

        Object o = Interpreter.getGlobalEnvironment().get("outer");
        Object m = Interpreter.getGlobalEnvironment().get("middle");
        Object i = Interpreter.getGlobalEnvironment().get("inner");

        assertEquals(3.0, o);
        assertEquals(5.0, m);
        assertEquals(5.0, i);
    }

    @Test
    void testDeepBreakWithPostExecution() {
        runWithNewGlobalContext("""
                    var x : 0;
                
                    while ($x < 3) {
                        $x : eval($x + 1);
                
                        while (1) {
                            break();
                        }
                    }
                
                    $x : eval($x + 10);
                """);

        Object result = Interpreter.getGlobalEnvironment().get("x");
        assertEquals(13.0, result);
    }

    @Test
    void testEmptyTuple() {
        String source = """
                var tuple : [];
                $tuple[0];
                """;
        assertThrows(IndexOutOfBoundsException.class, () -> runWithNewGlobalContext(source));
    }

    @Test
    void testTupleWithExpressions() {
        String source = """
                var tuple : [1+2, 3*4, 5];
                eval($tuple[0]);
                """;
        assertEquals(3.0, runWithNewGlobalContext(source));
    }

    @Test
    void testNestedTuples() {
        String source = """
                var tuple : [[1,2],[3,69],69];
                eval($tuple[1][1]);
                """;
        assertEquals(69.0, runWithNewGlobalContext(source));
    }

    @Test
    void testListCreationAndAccess() {
        String source = """
                var list : {1, 2, 3};
                eval($list[0]);
                """;

        assertEquals(1.0, runWithNewGlobalContext(source));
    }

    @Test
    void testImplicitListAccess() {
        String source = """
                fn getList() {
                    var list : {1, 2, 3};
                    ret($list);
                }
                
                eval(getList()[0]);
                """;

        assertEquals(1.0, runWithNewGlobalContext(source));
    }

    @Test
    void testNestedListAccess() {
        String source = """
                var list : {{1, 2}, {3, 4}};
                eval($list[1][0]);
                """;

        assertEquals(3.0, runWithNewGlobalContext(source));
    }

    @Test
    void testListAssignment() {
        String source = """
                var list : {1, 2, 3};
                $list[1] : 10;
                eval($list[1]);
                """;

        assertEquals(10.0, runWithNewGlobalContext(source));
    }

    @Test
    void testNestedListAssignment() {
        String source = """
                var list : {{1, 2}, {3, 4}};
                $list[1][1] : 99;
                eval($list[1][1]);
                """;

        assertEquals(99.0, runWithNewGlobalContext(source));
    }

    @Test
    void testNestedListAssignmentWithTuple() {
        String source = """
                var list : [{1, 2}, {3, 4}];
                $list[1][1] : 99;
                eval($list[1][1]);
                """;

        assertEquals(99.0, runWithNewGlobalContext(source));
    }

    @Test
    void testListWithExpressions() {
        String source = """
                var list : {1+2, 3*4, 5};
                eval($list[0]);
                """;

        assertEquals(3.0, runWithNewGlobalContext(source));
    }

    @Test
    void testExpressionIndex() {
        String source = """
                var list : {10, 20, 30};
                eval($list[eval(1+1)]);
                """;

        assertEquals(30.0, runWithNewGlobalContext(source));
    }

    @Test
    void testIndexOutOfBounds() {
        String source = """
                var list : {1, 2, 3};
                $list[5];
                """;

        assertThrows(IndexOutOfBoundsException.class, () -> runWithNewGlobalContext(source));
    }

    @Test
    void testAssignToNonList() {
        String source = """
                var x : 5;
                $x[0] : 10;
                """;

        assertThrows(ReferenceIsImmutableError.class, () -> runWithNewGlobalContext(source));
    }

    @Test
    void testEmptyList() {
        String source = """
                var list : {};
                """;

        assertNull(runWithNewGlobalContext(source));
    }

    @Test
    void testAssignmentEvaluatesExpression() {
        String source = """
                var list : {1, 2, 3};
                $list[0] : eval(1+2);
                eval($list[0]);
                """;

        assertEquals(3.0, runWithNewGlobalContext(source));
    }

    @Test
    void testBlockStatement() {
        String source = """
                var ref : 69;
                {
                    var ref : 10;
                }
                eval($ref);
                """;
        assertEquals(69.0, runWithNewGlobalContext(source));
    }

    @Test
    void testDefinedInBlock() {
        String source = """
                {
                    var ref : 0;
                }
                $ref;
                """;
        assertThrows(UndefinedReferenceError.class, () -> runWithNewGlobalContext(source));
    }

    @Test
    void testOverwrite() {
        Interpreter.getGlobalEnvironment().define("test", "Test");
        String source = """
                overwrite(
                "
                    var test : Hello World;
                "
                );
                
                $test;
                """;
        assertEquals("HelloWorld", runWithoutNewGlobalContext(source));
    }

    @Test
    void testPostUnaryOperator() {
        String source = """
                        var test : 10;
                        $test++;
                        $test--;
                """;
        assertEquals(10.0, runWithNewGlobalContext(source));
    }

    @Test
    void testPostUnaryOperatorInExpression() {
        String source = """
                        var test : 10;
                        $test : $test+++1;
                        eval($test);
                """;
        assertEquals(12.0, runWithNewGlobalContext(source));
    }

    @Test
    void testMultiplePostUnaryOperatorInExpression() {
        String source = """
                        var test : 5;
                        $test : $test+++$test+++$test;
                        eval($test);
                """;
        assertEquals(20.0, runWithNewGlobalContext(source));
    }

    @Test
    void testNestedPostUnaryOperator() {
        String source = """
                        var test : 0;
                        $test : (($test++) + 1) + $test;
                        eval($test);
                """;
        assertEquals(3.0, runWithNewGlobalContext(source));
    }

    @Test
    void testForeachOnList() {
        String source = """
                var list : {1,2,3};
                var lastResult;
                foreach(var element in $list) {
                    $lastResult : $element;
                }
                $lastResult;
                """;
        assertEquals("3", runWithNewGlobalContext(source));
    }

    @Test
    void testForeachOnTuple() {
        String source = """
                var list : [1,2,3];
                var lastResult;
                foreach(var element in $list) {
                    $lastResult : $element;
                }
                $lastResult;
                """;
        assertEquals("3", runWithNewGlobalContext(source));
    }

    @Test
    void testForeachOnString() {
        String source = """
                var string : "ABC";
                var lastResult;
                foreach(var element in $string) {
                    $lastResult : $element;
                }
                $lastResult;
                """;
        assertEquals('C', runWithNewGlobalContext(source));
    }

    @Test
    void testForeachWithBreak() {
        String source = """
                var list : {1,2,3};
                foreach(var element in $list) {
                    if($element == 1) {
                        break();
                    }
                }
                """;
        try {
            runWithNewGlobalContext(source);
        } catch (BreakSignal breakSignal) {
        }
    }

    @Test
    void testForeachWithReturn() {
        String source = """
                var list : {1,2,3};
                foreach(var element in $list) {
                    if($element == 1) {
                        ret();
                    }
                }
                """;
        try {
            runWithNewGlobalContext(source);
        } catch (ReturnSignal returnSignal) {
        }
    }

    @Test
    void testNestedForeach() {
        String source = """
                var list1 : {1,2,3};
                var list2 : {4,5,6};
                foreach(var element1 in $list1) {
                    foreach(var element2 in $list2) {
                        if($element1 == 3 && $element2 == 6) {
                            break();
                        }
                    }
                }
                """;
        try {
            runWithNewGlobalContext(source);
        } catch (ReturnSignal returnSignal) {
        }
    }

    @Test
    void testForeachOnNestedList() {
        String source = """
                var list1 : {{1,2,3}};
                foreach(var element in $list1[0]) {
                    if($element == 3) {
                        break();
                    }
                }
                """;
        try {
            runWithNewGlobalContext(source);
        } catch (ReturnSignal returnSignal) {
        }
    }

    @Test
    void testIfWithNewline() {
        String source = """
                import string;
                var test : "\n";
                
                if(charAt(0, $test) == "\n") {
                    ret();
                }
                """;
        try {
            Tokenizer tokenizer = new Tokenizer();
            Parser parser = new Parser();
            ImportResolver.reset();
            interpreter.run(parser.parseTokens(tokenizer.tokenize(source, false)), false);
        } catch (ReturnSignal returnSignal) {
        }
    }

    @Test
    void testIfWithDelimiter() {
        String source = """
                import string;
                var test : "}";
                if(charAt(0, $test) == "}") {
                    ret();
                }
                """;
        try {
            Tokenizer tokenizer = new Tokenizer();
            Parser parser = new Parser();
            interpreter.run(parser.parseTokens(tokenizer.tokenize(source, false)), false);
        } catch (ReturnSignal returnSignal) {
        }
    }
}
