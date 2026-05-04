package com.mira.linter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class LintScope {

    public record VarInfo(int line, int column, boolean isConst, boolean used) {

        public VarInfo markUsed() {
            return new VarInfo(line, column, isConst, true);
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
        if (!scopes.isEmpty()) {
            scopes.peek().put(name, new VarInfo(line, column, isConst, false));
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
        if (scopes.isEmpty()) return false;
        Map<String, VarInfo> outermost = null;
        for (Map<String, VarInfo> scope : scopes) {
            outermost = scope;
        }
        return outermost != null && outermost.containsKey(name);
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

    public void markUsed(String name) {
        for (Map<String, VarInfo> scope : scopes) {
            if (scope.containsKey(name)) {
                scope.put(name, scope.get(name).markUsed());
                return;
            }
        }
    }
}
