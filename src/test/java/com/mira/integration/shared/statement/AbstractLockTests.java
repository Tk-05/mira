package com.mira.integration.shared.statement;

// Lock/mutex is interpreter-only — no shared tests.
public abstract class AbstractLockTests {
    protected abstract String runForOutput(String source);
}
