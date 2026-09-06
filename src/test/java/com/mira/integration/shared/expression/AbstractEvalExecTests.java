package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractEvalExecTests {

    protected abstract String runForOutput(String source);

    @Test
    void evalRunsMultiStatementCodeAndReturnsLastValue() {
        assertEquals("20", runForOutput("""
                print(eval("var x : 10; x * 2;"));
                """));
    }

    @Test
    void evalSyntaxErrorIsCatchableWithFilterlessCatch() {
        assertEquals("caught", runForOutput("""
                try {
                    eval("var z : ;");
                } catch (e) {
                    print("caught");
                }
                """));
    }

    @Test
    void evalRuntimeErrorIsCatchableWithExplicitTypeFilter() {
        assertEquals("caught", runForOutput("""
                try {
                    eval("undefinedEvalVar + 1;");
                } catch (EvalError e) {
                    print("caught");
                }
                """));
    }

    @Test
    void evalDeclarationIsVisibleToLaterEvalCalls() {
        assertEquals("6", runForOutput("""
                eval("var evalShared : 5;");
                print(eval("evalShared + 1;"));
                """));
    }
}
