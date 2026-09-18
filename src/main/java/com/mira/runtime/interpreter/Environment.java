package com.mira.runtime.interpreter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final Set<String> publicDeclarations = new HashSet<>();

    // Slot-mode storage (resolver-based fast path, see Resolver.java): when
    // non-null, this Environment stores its local bindings in these parallel
    // arrays - indexed by a slot number assigned once, statically, by the
    // Resolver - instead of hashing a name into `values` on every access. Only
    // scopes the Resolver has fully analyzed (function calls, for/while bodies,
    // blocks, ...) are constructed in this mode; everything else (globals,
    // struct/object field storage, dynamically-evaluated code) keeps today's
    // HashMap-backed behavior untouched. `values` stays a harmless empty map in
    // this mode purely so the pre-existing name-based methods below don't need
    // extra null handling on it - they branch on `slots != null` first and fall
    // back to a linear scan over `slotNames`, which is fine since that path is
    // never hot (only the CLI debugger and similar introspection use it; normal
    // execution always goes through getAt/assignAt/defineAt with a precomputed
    // index).
    private final Object[] slots;
    private final String[] slotNames;
    private final boolean[] slotConst;

    public Environment() {
        this.parent = null;
        this.values = new HashMap<>();
        this.slots = null;
        this.slotNames = null;
        this.slotConst = null;
    }

    public Environment(Environment parent) {
        this.parent = parent;
        this.values = new HashMap<>();
        this.slots = null;
        this.slotNames = null;
        this.slotConst = null;
    }

    public Environment(Environment parent, int initialCapacity) {
        this.parent = parent;
        this.values = new HashMap<>(Math.max(initialCapacity * 2, 4));
        this.slots = null;
        this.slotNames = null;
        this.slotConst = null;
    }

    /**
     * Creates a slot-mode Environment with a fixed, pre-determined set of local
     * names (in Resolver-assigned slot order). {@code slotNames} is kept by
     * reference and treated as immutable - safe to share the same array across
     * every runtime instantiation of the same lexical scope (e.g. every call of the
     * same function, every loop iteration), since it never changes shape.
     */
    public Environment(Environment parent, String[] slotNames) {
        this.parent = parent;
        this.values = Collections.emptyMap();
        this.slots = new Object[slotNames.length];
        this.slotNames = slotNames;
        this.slotConst = new boolean[slotNames.length];
    }

    private int slotIndexOf(String name) {
        for (int i = 0; i < slotNames.length; i++) {
            if (slotNames[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    // ===== Resolved fast path: no hashing, no name lookup =====

    public Object getAt(int distance, int slot) {
        Environment env = this;
        for (int i = 0; i < distance; i++) {
            env = env.parent;
        }
        return env.slots[slot];
    }

    public void assignAt(int distance, int slot, Object value) {
        Environment env = this;
        for (int i = 0; i < distance; i++) {
            env = env.parent;
        }
        if (env.slotConst[slot]) {
            throw new ReferenceIsImmutableError(env.slotNames[slot]);
        }
        env.slots[slot] = value;
    }

    public void defineAt(int slot, Object value, boolean isConst) {
        slots[slot] = value;
        if (isConst) {
            slotConst[slot] = true;
        }
    }

    // ===== Name-based API: unchanged behavior for HashMap-mode instances, a
    // linear-scan fallback (correct but not hot) for slot-mode instances =====

    public void define(String name, Object value) {
        if (slots != null) {
            int idx = slotIndexOf(name);
            if (idx < 0) {
                throw new UndefinedReferenceError(name, "not part of this scope's declared locals");
            }
            slots[idx] = value;
            return;
        }
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

    public void forceDefine(String name, Object value) {
        if (slots != null) {
            int idx = slotIndexOf(name);
            if (idx < 0) {
                throw new UndefinedReferenceError(name, "not part of this scope's declared locals");
            }
            slots[idx] = value;
            slotConst[idx] = false;
            return;
        }
        values.put(name, value);
        constants.remove(name);
    }

    public void defineConst(String name, Object value) {
        if (slots != null) {
            int idx = slotIndexOf(name);
            if (idx < 0) {
                throw new UndefinedReferenceError(name, "not part of this scope's declared locals");
            }
            slots[idx] = value;
            slotConst[idx] = true;
            return;
        }
        if (!exists(name)) {
            values.put(name, value);
            constants.add(name);
        } else {
            throw new ObjectAlreadyDefinedInScope(name);
        }
    }

    public void assign(String name, Object value) {
        if (slots != null) {
            int idx = slotIndexOf(name);
            if (idx >= 0) {
                if (slotConst[idx]) {
                    throw new ReferenceIsImmutableError(name);
                }
                slots[idx] = value;
                return;
            }
            if (parent != null) {
                parent.assign(name, value);
                return;
            }
            throw new UndefinedVariableError(name);
        }
        // get() first instead of containsKey()+get(): a single hashmap probe covers
        // the overwhelmingly common case (an existing non-null value), falling back
        // to containsKey() only to disambiguate "absent" from "present but null"
        // (e.g. the top-level `args` binding can legitimately hold Java null).
        if (values.get(name) != null || values.containsKey(name)) {
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
        if (slots != null) {
            int idx = slotIndexOf(name);
            if (idx >= 0) {
                return slots[idx];
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
        Object value = values.get(name);
        if (value != null) {
            return value;
        }
        if (values.containsKey(name)) {
            return null;
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
            Iterable<String> names = env.slots != null ? Arrays.asList(env.slotNames) : env.values.keySet();
            for (String key : names) {
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
        if (slots != null) {
            int idx = slotIndexOf(name);
            if (idx >= 0) {
                return slots[idx];
            }
            if (parent != null) {
                return parent.getOrNull(name);
            }
            return null;
        }
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
        if (slots != null) {
            return slotIndexOf(name) >= 0;
        }
        return values.containsKey(name);
    }

    public Set<String> getDefinedNames() {
        if (slots != null) {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(slotNames)));
        }
        return Collections.unmodifiableSet(values.keySet());
    }

    public boolean existsInChain(String name) {
        if (exists(name)) {
            return true;
        }
        if (parent != null) {
            return parent.existsInChain(name);
        }
        return false;
    }

    public boolean isConst(String name) {
        if (slots != null) {
            int idx = slotIndexOf(name);
            if (idx >= 0) {
                return slotConst[idx];
            }
        } else if (constants.contains(name)) {
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

    /**
     * Hops {@code distance} {@link #getParent()} steps from this Environment - used
     * by the resolver fast path (see {@code Resolver}) once a variable reference's
     * static scope distance is known, so the interpreter can go straight to the
     * defining scope instead of walking the chain one link at a time while probing
     * each one by name.
     */
    public Environment ancestor(int distance) {
        Environment env = this;
        for (int i = 0; i < distance; i++) {
            env = env.parent;
        }
        return env;
    }

    public Environment copyShallow() {
        if (slots != null) {
            Environment copy = new Environment(this.parent, this.slotNames);
            System.arraycopy(this.slots, 0, copy.slots, 0, this.slots.length);
            System.arraycopy(this.slotConst, 0, copy.slotConst, 0, this.slotConst.length);
            return copy;
        }
        Environment copy = new Environment(this.parent);
        copy.values.putAll(this.values);
        copy.constants.addAll(this.constants);
        return copy;
    }

    public Environment snapshot(Environment globalEnv) {
        if (this == globalEnv || parent == null) {
            return this;
        }
        Environment parentSnapshot = parent.snapshot(globalEnv);
        if (slots != null) {
            Environment copy = new Environment(parentSnapshot, slotNames);
            System.arraycopy(slots, 0, copy.slots, 0, slots.length);
            System.arraycopy(slotConst, 0, copy.slotConst, 0, slotConst.length);
            return copy;
        }
        Environment copy = new Environment(parentSnapshot);
        copy.values.putAll(this.values);
        copy.constants.addAll(this.constants);
        return copy;
    }

    public int getSize() {
        return slots != null ? slots.length : values.size();
    }

    public Set<String> keySet() {
        if (slots != null) {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(slotNames)));
        }
        return values.keySet();
    }

    public Map<String, Object> getLocalValues() {
        if (slots != null) {
            Map<String, Object> view = new LinkedHashMap<>();
            for (int i = 0; i < slotNames.length; i++) {
                view.put(slotNames[i], slots[i]);
            }
            return Collections.unmodifiableMap(view);
        }
        return Collections.unmodifiableMap(values);
    }

    public void markPublic(String name) {
        publicDeclarations.add(name);
    }

    public boolean isPublicDeclaration(String name) {
        return publicDeclarations.contains(name);
    }

    public void copyDeclarationsTo(Environment target, Set<String> exclude) {
        for (String name : values.keySet()) {
            if (exclude.contains(name)) {
                continue;
            }
            if (!publicDeclarations.contains(name)) {
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
