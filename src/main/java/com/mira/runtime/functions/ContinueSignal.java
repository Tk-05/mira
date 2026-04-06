package com.mira.runtime.functions;

public class ContinueSignal extends RuntimeException {

    public ContinueSignal() {
        super(null, null, true, false);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
