package com.mira.integration.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.PostExprNaNError;
import com.mira.runtime.functions.ReturnSignal;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.expression.AbstractUnaryExpressionTests;

public class UnaryExpressionTest extends AbstractUnaryExpressionTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void prefixNegation() {
        assertEquals("-5.0", runForOutput("print((-5));"));
    }

    @Test
    void referenceAccess() {
        try {
            backend.runAndGetValue("var x : \"hello\"; return x;");
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
                print(z);
                """);
    }

    @Test
    void postIncrementAndDecrement() {
        assertEquals(10.0, InterpreterRunner.normNum(backend.runAndGetValue("var num : 10; num++; num--;")));
    }

    @Test
    void postUnaryInExpression() {
        assertEquals(12.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var num : 10;
                num : num+++1;
                (num);
                """)));
    }

    @Test
    void multiplePostUnaryInExpression() {
        assertEquals(20.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var num : 5;
                num : num+++num+++num;
                (num);
                """)));
    }

    @Test
    void nestedPostUnary() {
        assertEquals(3.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var num : 0;
                num : ((num++) + 1) + num;
                (num);
                """)));
    }

    @Test
    void postIncrementNonNumericLiteralThrowsNaN() {
        assertThrows(PostExprNaNError.class, () -> backend.runAndGetValue("eval(\"abc\"++);"));
    }

    @Test
    void postIncrementNonNumericFieldThrowsNaN() {
        assertThrows(PostExprNaNError.class, () -> backend.runAndGetValue("""
                var obj : { var name : "abc"; };
                (obj.name++);
                """));
    }

    @Test
    void postIncrementLiteralHasNoReferentAndReturnsValue() {
        assertEquals(2.0, InterpreterRunner.normNum(backend.runAndGetValue("(1++);")));
    }

    @Test
    void postIncrementComputedExpressionDoesNotMutateSourceVariable() {
        assertEquals(2.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var x : 2;
                var result : (x + 1)++;
                (x);
                """)));
    }

    @Test
    void postIncrementComputedExpressionReturnsIncrementedValue() {
        assertEquals(4.0, InterpreterRunner.normNum(backend.runAndGetValue("""
                var x : 2;
                var result : (x + 1)++;
                (result);
                """)));
    }

    @Test
    void booleanNegationVariable() {
        try {
            backend.runAndGetValue("var x : true; return !x;");
        } catch (ReturnSignal r) {
            assertEquals(Boolean.FALSE, r.getValue());
        }
    }
}
