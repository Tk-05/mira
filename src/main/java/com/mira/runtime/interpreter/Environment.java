package com.mira.runtime.interpreter;

import java.util.HashMap;
import java.util.Map;

import com.mira.error.runtime.RuntimeError.ObjectAlreadyDefinedInScope;
import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.error.runtime.RuntimeError.UndefinedVariableError;

public class Environment {

    private final Environment parent;
    private final Map<String, Object> values = new HashMap<>();
    private static boolean overwriteMode = false;

    public Environment() {
        this.parent = null;
    }

    public Environment(Environment parent) {
        this.parent = parent;
    }

    public void define(String name, Object value) {
        if (overwriteMode || !exists(name)) {
            values.put(name, value);
        } else {
            throw new ObjectAlreadyDefinedInScope(name);
        }
    }

    public Object get(String name) {
        if (values.containsKey(name)) {
            return values.get(name);
        }

        if (parent != null) {
            return parent.get(name);
        }

        throw new UndefinedReferenceError(name);
    }

    public void assign(String name, Object value) {
        if (overwriteMode || values.containsKey(name)) {
            values.put(name, value);
            return;
        }

        if (overwriteMode || parent != null) {
            parent.assign(name, value);
            return;
        }

        throw new UndefinedVariableError(name);
    }

    public boolean exists(String name) {
        return values.containsKey(name);
    }

    public int getSize() {
        return values.size();
    }

    public static void setOverwriteMode(boolean overwriteMode) {
        Environment.overwriteMode = overwriteMode;
    }

    public static boolean getOverwriteMode() {
        return overwriteMode;
    }
}
