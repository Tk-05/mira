package com.mira.runtime.functions;

public class ThrowSignal extends RuntimeException {

    private final Object value;

    public ThrowSignal(Object value) {
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
