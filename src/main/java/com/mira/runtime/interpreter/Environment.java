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
    private final Set<String> declaredFunctions = new HashSet<>();

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
        if (!exists(name)) {
            values.put(name, value);
        } else {
            throw new ObjectAlreadyDefinedInScope(name);
        }
    }

    public void defineFunction(String name, Object value) {
        define(name, value);
        declaredFunctions.add(name);
    }

    public boolean isDeclaredFunction(String name) {
        if (declaredFunctions.contains(name)) {
            return true;
        }
        return parent != null && parent.isDeclaredFunction(name);
    }

    public void forceDefine(String name, Object value) {
        values.put(name, value);
        constants.remove(name);
    }

    public void defineConst(String name, Object value) {
        if (!exists(name)) {
            values.put(name, value);
            constants.add(name);
        } else {
            throw new ObjectAlreadyDefinedInScope(name);
        }
    }

    public void assign(String name, Object value) {
        if (values.containsKey(name)) {
            if (constants.contains(name)) {
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

    public Object get(String name) {
        Object value = values.get(name);
        if (value != null || values.containsKey(name)) {
            return value;
        }
        if (parent != null) {
            return parent.get(name);
        }
        String suggestion = findSimilar(name);
        String hint = suggestion != null
                ? "Did you mean '" + suggestion + "'?"
                : "Make sure '" + name + "' is imported or declared before use";
        throw new UndefinedReferenceError(name, hint);
    }

    private String findSimilar(String name) {
        String best = null;
        int bestDist = 3;
        Environment env = this;
        while (env != null) {
            for (String key : env.values.keySet()) {
                int dist = editDistance(name.toLowerCase(), key.toLowerCase());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = key;
                }
            }
            env = env.parent;
        }
        return best;
    }

    private static int editDistance(String a, String b) {
        int la = a.length(), lb = b.length();
        if (Math.abs(la - lb) >= 3) {
            return 99;
        }
        int[] prev = new int[lb + 1];
        for (int j = 0; j <= lb; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= la; i++) {
            int[] curr = new int[lb + 1];
            curr[0] = i;
            for (int j = 1; j <= lb; j++) {
                curr[j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? prev[j - 1]
                        : 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
            }
            prev = curr;
        }
        return prev[lb];
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

    public boolean isConst(String name) {
        if (constants.contains(name)) {
            return true;
        }
        if (parent != null) {
            return parent.isConst(name);
        }
        return false;
    }

    public Environment getParent() {
        return parent;
    }

    public Environment snapshot(Environment globalEnv) {
        if (this == globalEnv || parent == null) {
            return this;
        }
        Environment parentSnapshot = parent.snapshot(globalEnv);
        Environment copy = new Environment(parentSnapshot);
        copy.values.putAll(this.values);
        copy.constants.addAll(this.constants);
        return copy;
    }

    public int getSize() {
        return values.size();
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    public void copyDeclarationsTo(Environment target, Set<String> exclude) {
        for (String name : values.keySet()) {
            if (exclude.contains(name)) {
                continue;
            }
            Object value = values.get(name);
            if (declaredFunctions.contains(name)) {
                target.defineFunction(name, value);
            } else if (constants.contains(name)) {
                target.defineConst(name, value);
            } else {
                target.define(name, value);
            }
        }
    }

}
