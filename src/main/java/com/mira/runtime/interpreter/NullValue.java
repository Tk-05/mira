package com.mira.runtime.interpreter;

public final class NullValue {

    public static final NullValue INSTANCE = new NullValue();

    private NullValue() {}

    @Override
    public String toString() {
        return "null";
    }
}
