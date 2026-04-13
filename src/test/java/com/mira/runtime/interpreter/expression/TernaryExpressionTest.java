package com.mira.runtime.interpreter.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.ReturnSignal;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class TernaryExpressionTest extends InterpreterTestBase {

    @Test
    void trueBranchReturned() {
        try {
            run("return true ? \"yes\" : \"no\";");
        } catch (ReturnSignal r) {
            assertEquals("yes", r.getValue());
        }
    }

    @Test
    void falseBranchReturned() {
        try {
            run("return false ? \"yes\" : \"no\";");
        } catch (ReturnSignal r) {
            assertEquals("no", r.getValue());
        }
    }

    @Test
    void conditionFromComparison() {
        try {
            run("var x : 10; return $x > 5 ? \"big\" : \"small\";");
        } catch (ReturnSignal r) {
            assertEquals("big", r.getValue());
        }
    }

    @Test
    void conditionFromComparisonFalse() {
        try {
            run("var x : 3; return $x > 5 ? \"big\" : \"small\";");
        } catch (ReturnSignal r) {
            assertEquals("small", r.getValue());
        }
    }

    @Test
    void numericBranches() {
        try {
            run("var x : 1; return $x == 1 ? 100 : 200;");
        } catch (ReturnSignal r) {
            assertEquals(100.0, normNum(r.getValue()));
        }
    }

    @Test
    void resultStoredInVariable() {
        try {
            run("var x : 7; var label : $x >= 5 ? \"pass\" : \"fail\"; return $label;");
        } catch (ReturnSignal r) {
            assertEquals("pass", r.getValue());
        }
    }

    @Test
    void nestedTernaryInThenBranch() {
        try {
            run("var x : 10; return $x > 5 ? ($x > 8 ? \"high\" : \"mid\") : \"low\";");
        } catch (ReturnSignal r) {
            assertEquals("high", r.getValue());
        }
    }

    @Test
    void nestedTernaryInElseBranch() {
        try {
            run("var x : 2; return $x > 5 ? \"high\" : ($x > 1 ? \"mid\" : \"low\");");
        } catch (ReturnSignal r) {
            assertEquals("mid", r.getValue());
        }
    }

    @Test
    void ternaryWithArithmeticInBranch() {
        try {
            run("var x : 4; return $x > 3 ? eval($x * 2) : eval($x + 1);");
        } catch (ReturnSignal r) {
            assertEquals(8.0, normNum(r.getValue()));
        }
    }

    @Test
    void ternaryWithLogicalAndCondition() {
        try {
            run("var x : 5; var y : 10; return $x > 3 && $y > 5 ? \"both\" : \"not both\";");
        } catch (ReturnSignal r) {
            assertEquals("both", r.getValue());
        }
    }

    @Test
    void ternaryWithLogicalOrCondition() {
        try {
            run("var x : 1; var y : 10; return $x > 5 || $y > 5 ? \"one\" : \"none\";");
        } catch (ReturnSignal r) {
            assertEquals("one", r.getValue());
        }
    }

    @Test
    void ternaryPassedToFunction() {
        try {
            run("""
                    fn identity(v) { return $v; }
                    var x : 3;
                    return identity($x > 2 ? "ok" : "fail");
                    """);
        } catch (ReturnSignal r) {
            assertEquals("ok", r.getValue());
        }
    }

    @Test
    void ternaryWithBooleanVariable() {
        try {
            run("var flag : true; return $flag ? 1 : 0;");
        } catch (ReturnSignal r) {
            assertEquals(1.0, normNum(r.getValue()));
        }
    }

    @Test
    void ternaryZeroIsFalsy() {
        try {
            run("return eval(0) ? \"yes\" : \"no\";");
        } catch (ReturnSignal r) {
            assertEquals("no", r.getValue());
        }
    }
}
