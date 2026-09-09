package com.mira.integration.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mira.error.resolver.StaticCheckError.StaticAssertFailedError;
import com.mira.integration.InterpreterRunner;
import com.mira.integration.shared.statement.AbstractStaticAssertTests;

public class StaticAssertTest extends AbstractStaticAssertTests {

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
    void errorMessageContainsUserMessage() {
        StaticAssertFailedError error = assertThrows(StaticAssertFailedError.class,
                () -> backend.run("static_assert(false, \"custom message\");"));
        assertTrue(error.getMessage().contains("custom message"));
    }

    @Test
    void errorWithoutMessageHasDefaultText() {
        StaticAssertFailedError error = assertThrows(StaticAssertFailedError.class,
                () -> backend.run("static_assert(false);"));
        assertTrue(error.getMessage().contains("static assertion failed"));
    }

    @Test
    void errorCodeIsE308() {
        StaticAssertFailedError error = assertThrows(StaticAssertFailedError.class,
                () -> backend.run("static_assert(false);"));
        assertTrue(error.getErrorCode().equals("E308"));
    }

    @Test
    void multiplePassingAssertsDoNotAbortExecution() {
        assertDoesNotThrow(() -> backend.run("""
                static_assert(true);
                static_assert(1 == 1);
                static_assert(2 > 1);
                """));
    }

    @Test
    void firstFailingAssertAbortsExecution() {
        assertThrows(StaticAssertFailedError.class, () -> backend.run("""
                static_assert(true);
                static_assert(false, "this fails");
                static_assert(true);
                """));
    }
}
