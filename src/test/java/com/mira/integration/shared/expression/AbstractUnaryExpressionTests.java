package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractUnaryExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void referenceInArithmetic() {
        assertEquals("15", runForOutput("var x : 10; print((x + 5));"));
    }

    @Test
    void postIncrement() {
        assertEquals("6", runForOutput("var x : 5; x++; print(x);"));
    }

    @Test
    void postDecrement() {
        assertEquals("4", runForOutput("var x : 5; x--; print(x);"));
    }

    @Test
    void postIncrementLiteralHasNoReferent() {
        assertEquals("2", runForOutput("print(1++);"));
    }

    @Test
    void postDecrementLiteralHasNoReferent() {
        assertEquals("0", runForOutput("print(1--);"));
    }

    @Test
    void postIncrementComputedExpressionDoesNotMutateOperand() {
        assertEquals("4,2", runForOutput("var x : 2; print((x + 1)++); print(\",\"); print(x);"));
    }

    @Test
    void postIncrementArrayElementMutatesInPlace() {
        assertEquals("2,2", runForOutput("var arr : [1,2,3]; print(arr[0]++); print(\",\"); print(arr[0]);"));
    }

    @Test
    void postDecrementArrayElementMutatesInPlace() {
        assertEquals("1,1", runForOutput("var arr : [2,2,3]; print(arr[0]--); print(\",\"); print(arr[0]);"));
    }

    @Test
    void postIncrementObjectFieldMutatesInPlace() {
        assertEquals("2,2",
                runForOutput("var obj : { var count : 1; }; print(obj.count++); print(\",\"); print(obj.count);"));
    }

    @Test
    void postDecrementObjectFieldMutatesInPlace() {
        assertEquals("1,1",
                runForOutput("var obj : { var count : 2; }; print(obj.count--); print(\",\"); print(obj.count);"));
    }

    @Test
    void preIncrement() {
        assertEquals("6", runForOutput("var x : 5; ++x; print(x);"));
    }

    @Test
    void preDecrement() {
        assertEquals("4", runForOutput("var x : 5; --x; print(x);"));
    }

    @Test
    void preIncrementLiteralHasNoReferent() {
        assertEquals("2", runForOutput("print(++1);"));
    }

    @Test
    void preDecrementLiteralHasNoReferent() {
        assertEquals("0", runForOutput("print(--1);"));
    }

    @Test
    void preIncrementComputedExpressionDoesNotMutateOperand() {
        assertEquals("4,2", runForOutput("var x : 2; print(++(x + 1)); print(\",\"); print(x);"));
    }

    @Test
    void preIncrementArrayElementMutatesInPlace() {
        assertEquals("2,2", runForOutput("var arr : [1,2,3]; print(++arr[0]); print(\",\"); print(arr[0]);"));
    }

    @Test
    void preDecrementArrayElementMutatesInPlace() {
        assertEquals("1,1", runForOutput("var arr : [2,2,3]; print(--arr[0]); print(\",\"); print(arr[0]);"));
    }

    @Test
    void preIncrementObjectFieldMutatesInPlace() {
        assertEquals("2,2",
                runForOutput("var obj : { var count : 1; }; print(++obj.count); print(\",\"); print(obj.count);"));
    }

    @Test
    void preDecrementObjectFieldMutatesInPlace() {
        assertEquals("1,1",
                runForOutput("var obj : { var count : 2; }; print(--obj.count); print(\",\"); print(obj.count);"));
    }

    @Test
    void booleanNegationTrue() {
        assertEquals("false", runForOutput("print((!true));"));
    }

    @Test
    void booleanNegationFalse() {
        assertEquals("true", runForOutput("print((!false));"));
    }
}
