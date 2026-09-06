package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.AssertionFailedError;

public abstract class AbstractAssertTests {

    protected abstract String runForOutput(String source);

    @Test
    void assertTrueDoesNotThrow() {
        assertDoesNotThrow(() -> runForOutput("assert(true);"));
    }

    @Test
    void assertFalseThrows() {
        assertThrows(AssertionFailedError.class, () -> runForOutput("assert(false);"));
    }

    @Test
    void assertPassingCondition() {
        assertDoesNotThrow(() -> runForOutput("var x : 5; assert(x == 5);"));
    }

    @Test
    void assertFailingCondition() {
        assertThrows(AssertionFailedError.class, () -> runForOutput("var x : 3; assert(x == 5);"));
    }

    @Test
    void assertNonZeroNumberDoesNotThrow() {
        assertDoesNotThrow(() -> runForOutput("assert(1);"));
    }

    @Test
    void assertZeroThrows() {
        assertThrows(AssertionFailedError.class, () -> runForOutput("assert(0);"));
    }
}
