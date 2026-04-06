package com.mira.runtime.interpreter.statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.AssertionFailedError;
import com.mira.runtime.interpreter.InterpreterTestBase;

public class AssertTest extends InterpreterTestBase {

    @Test
    void assertTrueDoesNotThrow() {
        assertDoesNotThrow(() -> run("assert(true);"));
    }

    @Test
    void assertFalseThrows() {
        assertThrows(AssertionFailedError.class, () -> run("assert(false);"));
    }

    @Test
    void assertPassingCondition() {
        assertDoesNotThrow(() -> run("""
                var x : 5;
                assert($x == 5);
                """));
    }

    @Test
    void assertFailingCondition() {
        assertThrows(AssertionFailedError.class, () -> run("""
                var x : 3;
                assert($x == 5);
                """));
    }

    @Test
    void assertWithMessageContainsMessage() {
        AssertionFailedError error = assertThrows(AssertionFailedError.class,
                () -> run("assert(false, \"x must be positive\");"));
        assertEquals("Assertion failed: x must be positive", error.getMessage());
    }

    @Test
    void assertWithMessageDoesNotThrowOnTrue() {
        assertDoesNotThrow(() -> run("assert(true, \"should not fail\");"));
    }

    @Test
    void assertNullThrows() {
        assertThrows(AssertionFailedError.class, () -> run("assert(null);"));
    }

    @Test
    void assertNonZeroNumberDoesNotThrow() {
        assertDoesNotThrow(() -> run("assert(1);"));
    }

    @Test
    void assertZeroThrows() {
        assertThrows(AssertionFailedError.class, () -> run("assert(0);"));
    }

    @Test
    void assertAfterPassingDoesNotAbortExecution() {
        run("""
                var x : 0;
                assert(true);
                $x : 1;
                """);
        assertEquals("1", com.mira.runtime.interpreter.Interpreter.getGlobalEnvironment().get("x"));
    }

    @Test
    void assertFailureAbortsExecution() {
        assertThrows(AssertionFailedError.class, () -> run("""
                var x : 0;
                assert(false);
                $x : 1;
                """));
    }

    @Test
    void assertWithArithmeticCondition() {
        assertDoesNotThrow(() -> run("""
                var a : 10;
                var b : 5;
                assert(eval($a - $b) == 5);
                """));
    }
}
