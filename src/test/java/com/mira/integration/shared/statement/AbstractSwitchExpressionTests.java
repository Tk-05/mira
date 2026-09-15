package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractSwitchExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void matchesFirstCase() {
        assertEquals("one", runForOutput("""
                var x : 1;
                var result : switch(x) {
                    case 1 -> "one"
                    case 2 -> "two"
                    default -> "other"
                };
                print(result);
                """));
    }

    @Test
    void fallsToDefault() {
        assertEquals("other", runForOutput("""
                var x : 99;
                var result : switch(x) {
                    case 1 -> "one"
                    default -> "other"
                };
                print(result);
                """));
    }

    @Test
    void stringMatch() {
        assertEquals("yes", runForOutput("""
                var s : "hi";
                var result : switch(s) {
                    case "hi" -> "yes"
                    default -> "no"
                };
                print(result);
                """));
    }
}
