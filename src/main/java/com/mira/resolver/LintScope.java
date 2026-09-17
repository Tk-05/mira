package com.mira.resolver;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class LintScope {

    public record VarInfo(int line, int column, boolean isConst, boolean used, boolean isImport, boolean isFunction,
            boolean isComptime, boolean isLoopIterator) {

        public VarInfo(int line, int column, boolean isConst, boolean used) {
            this(line, column, isConst, used, false, false, false, false);
        }

        public VarInfo(int line, int column, boolean isConst, boolean used, boolean isImport) {
            this(line, column, isConst, used, isImport, false, false, false);
        }

        public VarInfo markUsed() {
            return new VarInfo(line, column, isConst, true, isImport, isFunction, isComptime, isLoopIterator);
        }
    }

    private final Deque<Map<String, VarInfo>> scopes = new ArrayDeque<>();

    public void push() {
        scopes.push(new HashMap<>());
    }

    public Map<String, VarInfo> pop() {
        return scopes.pop();
    }

    public void declare(String name, int line, int column, boolean isConst) {
        declare(name, line, column, isConst, false);
    }

    public void declare(String name, int line, int column, boolean isConst, boolean isComptime) {
        if (!scopes.isEmpty()) {
            scopes.peek().put(name, new VarInfo(line, column, isConst, false, false, false, isComptime, false));
        }
    }

    public void declareImport(String name, int line, int column) {
        if (!scopes.isEmpty()) {
            scopes.peek().put(name, new VarInfo(line, column, false, false, true));
        }
    }

    public void declareFunction(String name, int line, int column) {
        if (!scopes.isEmpty()) {
            scopes.peek().put(name, new VarInfo(line, column, false, false, false, true, false, false));
        }
    }

    public void declareLoopIterator(String name, int line, int column) {
        if (!scopes.isEmpty()) {
            scopes.peek().put(name, new VarInfo(line, column, false, false, false, false, false, true));
        }
    }

    public boolean isDeclared(String name) {
        for (Map<String, VarInfo> scope : scopes) {
            if (scope.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDeclaredInCurrentScope(String name) {
        return !scopes.isEmpty() && scopes.peek().containsKey(name);
    }

    public boolean isDeclaredInOutermostScope(String name) {
        if (scopes.isEmpty()) {
            return false;
        }
        Map<String, VarInfo> outermost = null;
        for (Map<String, VarInfo> scope : scopes) {
            outermost = scope;
        }
        return outermost != null && outermost.containsKey(name);
    }

    public void markAllFunctionsUsed() {
        if (!scopes.isEmpty()) {
            scopes.peek().replaceAll((name, info) -> info.isFunction() ? info.markUsed() : info);
        }
    }

    public boolean isConst(String name) {
        for (Map<String, VarInfo> scope : scopes) {
            VarInfo info = scope.get(name);
            if (info != null) {
                return info.isConst();
            }
        }
        return false;
    }

    public boolean isLoopIterator(String name) {
        for (Map<String, VarInfo> scope : scopes) {
            VarInfo info = scope.get(name);
            if (info != null) {
                return info.isLoopIterator();
            }
        }
        return false;
    }

    public boolean isComptime(String name) {
        for (Map<String, VarInfo> scope : scopes) {
            VarInfo info = scope.get(name);
            if (info != null) {
                return info.isComptime();
            }
        }
        return false;
    }

    public void markUsed(String name) {
        for (Map<String, VarInfo> scope : scopes) {
            if (scope.containsKey(name)) {
                scope.put(name, scope.get(name).markUsed());
                return;
            }
        }
    }
}
