package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractTernaryExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void numericBranches() {
        assertEquals("1", runForOutput("var x : true; print((x ? 1 : 0));"));
    }

    @Test
    void ternaryZeroIsFalsy() {
        assertEquals("no", runForOutput("print(0 ? \"yes\" : \"no\");"));
    }

    @Test
    void ternaryPassedToFunction() {
        assertEquals("yes", runForOutput("""
                fn f(s) { return s; }
                print(f(true ? "yes" : "no"));
                """));
    }

    @Test
    void conditionFromComparison() {
        assertEquals("big", runForOutput("var x : 10; print((x > 5 ? \"big\" : \"small\"));"));
    }

    @Test
    void arrowLambdaBranchesTrueCondition() {
        assertEquals("10", runForOutput("""
                var f : true ? (x) -> (x * 2) : (x) -> (x + 1);
                print(f(5));
                """));
    }

    @Test
    void arrowLambdaBranchesFalseCondition() {
        assertEquals("6", runForOutput("""
                var f : false ? (x) -> (x * 2) : (x) -> (x + 1);
                print(f(5));
                """));
    }

    @Test
    void blockBodyArrowLambdaBranches() {
        assertEquals("10", runForOutput("""
                var f : true ? (x) -> { return (x * 2); } : (x) -> { return (x + 1); };
                print(f(5));
                """));
    }

    @Test
    void fnLambdaBranches() {
        assertEquals("6", runForOutput("""
                var f : false ? fn(x) { return (x * 2); } : fn(x) { return (x + 1); };
                print(f(5));
                """));
    }

    @Test
    void arrowLambdaBranchClosesOverOuterVariable() {
        assertEquals("50", runForOutput("""
                var factor : 10;
                var f : true ? (x) -> (x * factor) : (x) -> (x - factor);
                print(f(5));
                """));
    }
}
