package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.AssertionFailedError;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractAssertTests;

public class AssertTest extends AbstractAssertTests {

    private final InterpreterRunner backend = new InterpreterRunner();

    @BeforeEach
    void setup() {
        backend.reset();
    }

    @Override
    protected String runForOutput(String source) {
        return backend.run(source);
    }

    @Test
    void assertWithMessageContainsMessage() {
        AssertionFailedError error = assertThrows(AssertionFailedError.class,
                () -> backend.run("assert(false, \"x must be positive\");"));
        assertEquals("Assertion failed: x must be positive", error.getMessage());
    }

    @Test
    void assertWithMessageDoesNotThrowOnTrue() {
        assertDoesNotThrow(() -> backend.run("assert(true, \"should not fail\");"));
    }

    @Test
    void assertNullThrows() {
        assertThrows(AssertionFailedError.class, () -> backend.run("assert(null);"));
    }

    @Test
    void assertAfterPassingDoesNotAbortExecution() {
        assertDoesNotThrow(() -> backend.run("""
                assert(true);
                assert(1 == 1);
                """));
    }

    @Test
    void assertFailureAbortsExecution() {
        assertThrows(AssertionFailedError.class, () -> backend.run("""
                assert(true);
                assert(false);
                assert(true);
                """));
    }

    @Test
    void assertWithArithmeticCondition() {
        assertDoesNotThrow(() -> backend.run("assert(eval(2 + 2) == 4);"));
    }
}
