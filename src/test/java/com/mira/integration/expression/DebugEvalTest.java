package com.mira.integration.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import com.mira.integration.InterpreterTestBase;

public class DebugEvalTest extends InterpreterTestBase {
    @Test
    void evalInTopLevel() {
        assertEquals(7.0, run("eval(3 + 4);"));
    }

    @Test
    void evalInFunction() {
        assertEquals(7.0, run("fn add(a, b) { return eval($a + $b); } add(3, 4);"));
    }
}
