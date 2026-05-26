package com.mira.integration.shared.expression;

// exec blocks use dynamic string evaluation — interpreter-only.
public abstract class AbstractExecBlockTests {
    protected abstract String runForOutput(String source);
}
