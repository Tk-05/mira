package com.mira.integration.shared.statement;

// Spawn/concurrency is interpreter-only — no shared tests.
public abstract class AbstractSpawnTests {
    protected abstract String runForOutput(String source);
}
