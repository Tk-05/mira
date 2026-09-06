package com.mira.lib;

import java.util.List;

import com.mira.runtime.functions.NativeInterop;
import com.mira.runtime.interpreter.Environment;

/**
 * Optional alternative to implementing {@link Lib} by hand: a native library
 * exposes the Java class(es) it wants auto-bound via reflection ({@link
 * #targets()}), plus a small list of {@link #overrides()} for whatever the
 * auto-scan can't safely resolve on its own (ambiguous overloads, struct
 * constructors, pointer-building glue). {@link #loadLib} is provided,
 * delegating to {@link NativeInterop#bind} - a conforming library needs no
 * {@code loadLib} of its own for the common case.
 */
public interface ReflectiveLib extends Lib {

    List<Class<?>> targets();

    default List<NativeOverride> overrides() {
        return List.of();
    }

    @Override
    default void loadLib(Environment environment) {
        NativeInterop.bind(this, environment);
    }
}
