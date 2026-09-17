package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractEnumDeclTests {

    protected abstract String runForOutput(String source);

    @Test
    void enumAccessedViaFieldAccess() {
        assertEquals("0", runForOutput("enum Color { RED, GREEN, BLUE } print(Color.RED);"));
    }

    @Test
    void enumValueUsedInComparison() {
        assertEquals("true", runForOutput("""
                enum Status { OK, ERR }
                var s : Status.OK;
                print(s == Status.OK);
                """));
    }

    @Test
    void enumValueUsedInSwitch() {
        assertEquals("ok", runForOutput("""
                enum Status { OK, ERR }
                var s : Status.OK;
                switch(s) {
                    case Status.OK { print("ok"); }
                    default { print("err"); }
                }
                """));
    }
}
