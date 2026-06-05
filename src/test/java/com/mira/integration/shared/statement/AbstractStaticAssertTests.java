package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.resolver.StaticCheckError.StaticAssertFailedError;

public abstract class AbstractStaticAssertTests {

    protected abstract String runForOutput(String source);

    @Test
    void staticAssertTrueDoesNotThrow() {
        assertDoesNotThrow(() -> runForOutput("static_assert(true);"));
    }

    @Test
    void staticAssertFalseThrows() {
        assertThrows(StaticAssertFailedError.class, () -> runForOutput("static_assert(false);"));
    }

    @Test
    void staticAssertWithMessageDoesNotThrowOnTrue() {
        assertDoesNotThrow(() -> runForOutput("static_assert(1 == 1, \"math works\");"));
    }

    @Test
    void staticAssertWithMessageThrowsOnFalse() {
        assertThrows(StaticAssertFailedError.class, () -> runForOutput("static_assert(1 == 2, \"impossible\");"));
    }

    @Test
    void staticAssertNonZeroDoesNotThrow() {
        assertDoesNotThrow(() -> runForOutput("static_assert(1);"));
    }

    @Test
    void staticAssertZeroThrows() {
        assertThrows(StaticAssertFailedError.class, () -> runForOutput("static_assert(0);"));
    }

    @Test
    void staticAssertInsideFunctionDoesNotThrowWhenTrue() {
        assertDoesNotThrow(() -> runForOutput("""
                fn check(x) {
                    static_assert($x > 0, "x must be positive");
                }
                check(5);
                """));
    }

    @Test
    void staticAssertInsideFunctionThrowsWhenFalse() {
        assertThrows(StaticAssertFailedError.class, () -> runForOutput("""
                fn check(x) {
                    static_assert($x > 0, "x must be positive");
                }
                check(-1);
                """));
    }

    @Test
    void staticAssertInsideIfBlock() {
        assertDoesNotThrow(() -> runForOutput("""
                var ok : true;
                if ($ok) {
                    static_assert($ok, "ok must be true");
                }
                """));
    }

    @Test
    void staticAssertWithComptimeConstant() {
        assertDoesNotThrow(() -> runForOutput("""
                comptime {
                    const SIZE : 64;
                }
                static_assert($SIZE > 0, "SIZE must be positive");
                """));
    }

    @Test
    void staticAssertWithComptimeConstantFails() {
        assertThrows(StaticAssertFailedError.class, () -> runForOutput("""
                comptime {
                    const SIZE : -1;
                }
                static_assert($SIZE > 0, "SIZE must be positive");
                """));
    }
}
