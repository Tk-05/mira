package com.mira.integration.shared.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public abstract class AbstractTryCatchTests {

    protected abstract String runForOutput(String source);

    @Test
    void noThrowSkipsCatchBlock() {
        assertEquals("ok", runForOutput("""
                try { print("ok"); } catch(error) { print("caught"); }
                """));
    }

    @Test
    void executionContinuesAfterTryCatch() {
        assertEquals("after", runForOutput("""
                try { var x : 1; } catch(error) {}
                print("after");
                """));
    }

    @Test
    void throwInsideFunction() {
        assertEquals("caught", runForOutput("""
                fn boom() { throw error("oops"); }
                try { boom(); } catch(error) { print("caught"); }
                """));
    }

    @Test
    void finallyRunsWhenNoThrow() {
        assertEquals("finally", runForOutput("""
                try { var x : 1; } catch(error) {} finally { print("finally"); }
                """));
    }

    @Test
    void finallyRunsAfterCatch() {
        assertEquals("finally", runForOutput("""
                try { throw error("x"); } catch(error) {} finally { print("finally"); }
                """));
    }
}
