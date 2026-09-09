package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractIfTests {

    protected abstract String runForOutput(String source);

    @Test
    void trueBranchExecutes() {
        assertEquals("true", runForOutput("var x : 5; if (x > 3) { print(true); } else { print(false); }"));
    }

    @Test
    void falseBranchExecutes() {
        assertEquals("false", runForOutput("var x : 1; if (x > 3) { print(true); } else { print(false); }"));
    }

    @Test
    void nestedIf() {
        assertEquals("true", runForOutput("""
                var x : 5;
                var y : 10;
                if (x > 3) {
                    if (y > 5) { print(true); }
                    else { print(false); }
                }
                """));
    }

    @Test
    void ifWithLogicalAndCondition() {
        assertEquals("true", runForOutput("""
                var x : 5;
                var y : 10;
                if (x > 3 && y > 5) { print(true); } else { print(false); }
                """));
    }

    @Test
    void ifWithLogicalOrCondition() {
        assertEquals("true", runForOutput("""
                var x : 1;
                var y : 10;
                if (x > 3 || y > 5) { print(true); } else { print(false); }
                """));
    }

    @Test
    void ifWithTrueLiteral() {
        assertEquals("true", runForOutput("if(true) { print(true); } else { print(false); }"));
    }

    @Test
    void ifWithFalseLiteral() {
        assertEquals("false", runForOutput("if(false) { print(true); } else { print(false); }"));
    }

    @Test
    void ifWithBooleanVariable() {
        assertEquals("true", runForOutput("var x : true; if(x) { print(true); } else { print(false); }"));
    }

    @Test
    void elseIfTaken() {
        assertEquals("true", runForOutput(
                "var x : 2; if (x > 3) { print(false); } else if (x > 1) { print(true); } else { print(false); }"));
    }

    @Test
    void elseIfSkipped() {
        assertEquals("true", runForOutput(
                "var x : 0; if (x > 3) { print(false); } else if (x > 1) { print(false); } else { print(true); }"));
    }

    @Test
    void elseIfChain() {
        assertEquals("5", runForOutput("""
                var x : 5;
                if (x == 1) { print(1); }
                else if (x == 2) { print(2); }
                else if (x == 5) { print(5); }
                else { print(0); }
                """));
    }

    @Test
    void singleStatementTrueBranchNoBraces() {
        assertEquals("yes", runForOutput("if (true) print(\"yes\");"));
    }

    @Test
    void singleStatementFalseBranchSkipped() {
        assertEquals("no", runForOutput("var x : false; if (x) print(\"yes\"); else print(\"no\");"));
    }

    @Test
    void singleStatementElseNoBraces() {
        assertEquals("else", runForOutput("if (false) print(\"then\"); else print(\"else\");"));
    }

    @Test
    void singleStatementElseIfNoBraces() {
        assertEquals("2", runForOutput("""
                var x : 2;
                if (x == 1) print(1);
                else if (x == 2) print(2);
                else print(0);
                """));
    }

    @Test
    void singleStatementBodyDoesNotLeakToNextStatement() {
        assertEquals("inside", runForOutput("""
                var out : "outside";
                if (true) out : "inside";
                print(out);
                """));
    }

    @Test
    void nextStatementAfterSingleBodyAlwaysRuns() {
        assertEquals("AB", runForOutput("""
                if (true) print("A");
                print("B");
                """));
    }
}
