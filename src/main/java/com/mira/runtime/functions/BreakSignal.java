package com.mira.runtime.functions;

public class BreakSignal extends RuntimeException {

    public BreakSignal() {
        super(null, null, true, false);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
