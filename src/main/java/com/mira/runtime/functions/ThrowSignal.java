package com.mira.runtime.functions;

public class ThrowSignal extends RuntimeException {

    private final String exceptionType;
    private final Object value;

    public ThrowSignal(String exceptionType, Object value) {
        super(null, null, true, false);
        this.exceptionType = exceptionType;
        this.value = value;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public Object getValue() {
        return value;
    }
}
