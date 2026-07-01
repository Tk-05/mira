package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractAssignExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void assignExprReturnsAssignedValue() {
        assertEquals("42", runForOutput("""
                var x : 0;
                fn f() { return $x : 42; }
                print(f());
                """));
    }

    @Test
    void assignExprMutatesVariable() {
        assertEquals("42", runForOutput("""
                var x : 0;
                fn f() { return $x : 42; }
                f();
                print($x);
                """));
    }

    @Test
    void assignExprInIfCondition() {
        assertEquals("yes", runForOutput("""
                var x : 0;
                if ($x : 1) { print("yes"); } else { print("no"); }
                """));
    }

    @Test
    void chainedAssignExpr() {
        assertEquals("5,5", runForOutput("""
                var a : 0;
                var b : 0;
                $a : $b : 5;
                print($a); print(","); print($b);
                """));
    }

    @Test
    void assignExprInPrint() {
        assertEquals("7", runForOutput("""
                var x : 0;
                print($x : 7);
                """));
    }
}
