package com.mira.runtime.interpreter;

import java.util.HashMap;
import java.util.Map;

import com.mira.error.runtime.RuntimeError.UndefinedReferenceError;
import com.mira.error.runtime.RuntimeError.UndefinedVariableError;

public class Environment {

    private final Environment parent;
    private final Map<String, Object> values = new HashMap<>();

    public Environment() {
        this.parent = null;
    }

    public Environment(Environment parent) {
        this.parent = parent;
    }

    public void define(String name, Object value) {
        values.put(name, value);
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

        if (values.containsKey(name)) {
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
}
