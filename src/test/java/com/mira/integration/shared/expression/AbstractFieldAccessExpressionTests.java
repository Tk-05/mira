package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractFieldAccessExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void accessInitializedField() {
        assertEquals("42", runForOutput("""
                var obj : { var x : 42; };
                print(obj.x);
                """));
    }

    @Test
    void accessFieldInArithmetic() {
        assertEquals("10", runForOutput("""
                var obj : { var x : 5; };
                print((obj.x * 2));
                """));
    }

    @Test
    void multipleFieldsOnSameObject() {
        assertEquals("3", runForOutput("""
                var p : { var x : 1; var y : 2; };
                print((p.x + p.y));
                """));
    }

    @Test
    void fieldAccessAfterAssignment() {
        assertEquals("99", runForOutput("""
                var obj : { var val : 1; };
                obj.val : 99;
                print(obj.val);
                """));
    }
}
