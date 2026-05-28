package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.PostUnaryError;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractUnaryExpressionTests;

public class UnaryExpressionTest extends AbstractUnaryExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() { backend.reset(); }

    @Override
    protected String runForOutput(String source) { return backend.run(source); }

    @Test
    void prefixNegation() {
        assertEquals("-5.0", runForOutput("print(eval(-5));"));
    }

    @Test
    void referenceAccess() {
        try {
            backend.runAndGetValue("var x : \"hello\"; return $x;");
        } catch (ReturnSignal r) {
            assertEquals("hello", r.getValue());
        }
    }

    @Test
    void doubleReference() {
        backend.runAndGetValue("""
                var x : 1;
                var y : x;
                var z : y;
                print($$$z);
                """);
    }

    @Test
    void postIncrementAndDecrement() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("var num : 10; $num++; $num--;")));
    }

    @Test
    void postUnaryInExpression() {
        assertEquals(12.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var num : 10;
                $num : $num+++1;
                eval($num);
                """)));
    }

    @Test
    void multiplePostUnaryInExpression() {
        assertEquals(20.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var num : 5;
                $num : $num+++$num+++$num;
                eval($num);
                """)));
    }

    @Test
    void nestedPostUnary() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var num : 0;
                $num : (($num++) + 1) + $num;
                eval($num);
                """)));
    }

    @Test
    void invalidPostUnaryThrows() {
        assertThrows(PostUnaryError.class, () -> backend.runAndGetValue("eval(1++2);"));
    }

    @Test
    void booleanNegationVariable() {
        try {
            backend.runAndGetValue("var x : true; return !$x;");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.FALSE, r.getValue());
        }
    }
}
