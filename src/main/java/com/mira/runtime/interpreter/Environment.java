package com.mira.runtime.interpreter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mira.error.runtime.RuntimeError.ObjectAlreadyDefinedInScope;
import com.mira.error.runtime.RuntimeError.ReferenceIsImmutableError;
import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.error.runtime.RuntimeError.UndefinedVariableError;

public class Environment {

    private final Environment parent;
    private final Map<String, Object> values;
    private final Set<String> constants = new HashSet<>();
    private static final ThreadLocal<Boolean> overwriteMode = ThreadLocal.withInitial(() -> false);

    public Environment() {
        this.parent = null;
        this.values = new HashMap<>();
    }

    public Environment(Environment parent) {
        this.parent = parent;
        this.values = new HashMap<>();
    }

    public Environment(Environment parent, int initialCapacity) {
        this.parent = parent;
        this.values = new HashMap<>(Math.max(initialCapacity * 2, 4));
    }

    public void define(String name, Object value) {
        if (overwriteMode.get() || !exists(name)) {
            values.put(name, value);
        } else {
            throw new ObjectAlreadyDefinedInScope(name);
        }
    }

    public void defineConst(String name, Object value) {
        if (overwriteMode.get() || !exists(name)) {
            values.put(name, value);
            constants.add(name);
        } else {
            throw new ObjectAlreadyDefinedInScope(name);
        }
    }

    public boolean isConst(String name) {
        if (constants.contains(name)) {
            return true;
        }
        if (parent != null) {
            return parent.isConst(name);
        }
        return false;
    }

    public Object get(String name) {
        Object value = values.get(name);
        if (value != null || values.containsKey(name)) {
            return value;
        }
        if (parent != null) {
            return parent.get(name);
        }
        throw new UndefinedReferenceError(name);
    }

    public Object getOrNull(String name) {
        Object value = values.get(name);
        if (value != null || values.containsKey(name)) {
            return value;
        }
        if (parent != null) {
            return parent.getOrNull(name);
        }
        return null;
    }

    public void assign(String name, Object value) {
        if (values.containsKey(name)) {
            if (!overwriteMode.get() && constants.contains(name)) {
                throw new ReferenceIsImmutableError(name);
            }
            values.put(name, value);
            return;
        }
        if (parent != null) {
            parent.assign(name, value);
            return;
        }
        throw new UndefinedVariableError(name);
    }

    public boolean exists(String name) {
        return values.containsKey(name);
    }

    public boolean existsInChain(String name) {
        if (values.containsKey(name)) {
            return true;
        }
        if (parent != null) {
            return parent.existsInChain(name);
        }
        return false;
    }

    public Environment getParent() {
        return parent;
    }

    public int getSize() {
        return values.size();
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    public static void setOverwriteMode(boolean value) {
        overwriteMode.set(value);
    }

    public static boolean getOverwriteMode() {
        return overwriteMode.get();
    }
}
