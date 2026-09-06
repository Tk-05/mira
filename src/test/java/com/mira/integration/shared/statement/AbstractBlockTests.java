package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractBlockTests {

    protected abstract String runForOutput(String source);

    @Test
    void funcDeclaredInBlockIsCallableAfterBlock() {
        assertEquals("42", runForOutput("{ fn answer() { return 42; } } print(answer());"));
    }

    @Test
    void funcDeclaredInBlockReceivesArgs() {
        assertEquals("6", runForOutput("{ fn double(n) { return (n * 2); } } print(double(3));"));
    }

    @Test
    void blockCanAccessOuterVariable() {
        assertEquals("10", runForOutput("var x : 10; { print(x); }"));
    }

    @Test
    void blockCanModifyOuterVariable() {
        assertEquals("20", runForOutput("var x : 10; { x : 20; } print(x);"));
    }

    @Test
    void blockWithMultipleStatements() {
        assertEquals("6", runForOutput("""
                var a : 1;
                var b : 2;
                var c : 3;
                {
                    a : (a + b + c);
                }
                print(a);
                """));
    }
}
