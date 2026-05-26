package com.mira.integration.shared.statement;

// Async/await is interpreter-only — no shared tests.
public abstract class AbstractAsyncAwaitTests {
    protected abstract String runForOutput(String source);
}
