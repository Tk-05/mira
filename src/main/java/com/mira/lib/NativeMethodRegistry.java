package com.mira.lib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mira.runtime.functions.Callable;

/**
 * Process-wide table of {@code NativeType -> methodName -> Callable}, filled as
 * a side effect of running a {@link NativeMethodProvider} lib's
 * {@link Lib#loadLib} (see {@code LibIndex.getNativeMethods} and
 * {@code ImportResolver.resolveStdlibImport}) - mirroring the visibility a
 * plain stdlib free function already has: a native method only becomes
 * dispatchable once its owning lib has actually been imported in the running
 * program.
 */
public final class NativeMethodRegistry {

    public static final NativeMethodRegistry INSTANCE = new NativeMethodRegistry();

    private final Map<NativeType, Map<String, Callable>> methods = new ConcurrentHashMap<>();

    private NativeMethodRegistry() {
    }

    public void register(NativeType type, String name, Callable callable) {
        methods.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>()).put(name, callable);
    }

    public Callable lookup(NativeType type, String name) {
        Map<String, Callable> forType = methods.get(type);
        return forType == null ? null : forType.get(name);
    }

    public boolean has(NativeType type, String name) {
        return lookup(type, name) != null;
    }
}
