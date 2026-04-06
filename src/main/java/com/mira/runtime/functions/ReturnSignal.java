package com.mira.runtime.functions;

public class ReturnSignal extends RuntimeException {

    private final Object value;

    public ReturnSignal(Object value) {
        super(null, null, true, false);
        this.value = value;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    public Object getValue() {
        return value;
    }
}
