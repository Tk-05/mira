package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractTernaryExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void numericBranches() {
        assertEquals("1", runForOutput("var x : true; print(eval($x ? 1 : 0));"));
    }

    @Test
    void ternaryZeroIsFalsy() {
        assertEquals("no", runForOutput("print(0 ? \"yes\" : \"no\");"));
    }

    @Test
    void ternaryPassedToFunction() {
        assertEquals("yes", runForOutput("""
                fn f(s) { return $s; }
                print(f(true ? "yes" : "no"));
                """));
    }

    @Test
    void conditionFromComparison() {
        assertEquals("big", runForOutput("var x : 10; print(eval($x > 5 ? \"big\" : \"small\"));"));
    }
}
